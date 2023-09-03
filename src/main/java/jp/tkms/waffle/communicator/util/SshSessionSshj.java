package jp.tkms.waffle.communicator.util;

import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile;
import jp.tkms.utils.abbreviation.Simple;
import jp.tkms.utils.debug.DebugElapsedTime;
import jp.tkms.utils.io.IOStreamUtil;
import jp.tkms.waffle.Constants;
import jp.tkms.waffle.Main;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.log.message.InfoLogMessage;
import jp.tkms.waffle.data.log.message.WarnLogMessage;
import jp.tkms.waffle.exception.FailedToAcquireConnectionException;
import jp.tkms.waffle.exception.FailedToControlRemoteException;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.Channel;
import net.schmizz.sshj.connection.channel.direct.DirectConnection;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Signal;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.*;
import net.schmizz.sshj.userauth.password.PasswordUtils;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.xfer.LocalSourceFile;
import net.schmizz.sshj.xfer.scp.SCPDownloadClient;
import net.schmizz.sshj.xfer.scp.SCPFileTransfer;

import java.io.*;
import java.nio.channels.UnresolvedAddressException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


public class SshSessionSshj implements AutoCloseable {

  private static final int LINE_FEED = '\n';
  static final int TIMEOUT = 60;
  static final int LONG_TIMEOUT = 300; //5min
  private static final Map<String, SessionWrapperSshj> sessionCache = new HashMap<>();


  protected FileKeyProvider fileKeyProvider;
  private SessionWrapperSshj sessionWrapper = null;
  String username;
  String host;
  int port;
  Computer loggingTarget;
  SshSessionSshj tunnelSession;
  private String tunnelTargetHost;
  private int tunnelTargetPort;
  String homePath;
  long previousAliveTime = -1;
  Thread watchdogThread = null;

  public SshSessionSshj(Computer loggingTarget, SshSessionSshj tunnelSession) {
    this.loggingTarget = loggingTarget;
    this.tunnelSession = tunnelSession;
  }

  private void startWatchdog() {
    stopWatchdog();
    updateAliveTime();
    watchdogThread = new Thread(() -> {
      Thread streamThread = null;
      try (Session.Shell channel = sessionWrapper.get().startSession().startShell()) {
        streamThread = new Thread() {
          @Override
          public void run() {
            try {
              while (!isInterrupted()) {
                int c = channel.getInputStream().read();
                if (c == -1) {
                  break;
                }
                updateAliveTime();
              }
            } catch (IOException e) {
              //NOP
            }
          }
        };
        streamThread.start();

        while (true) {
          long submitTime = System.currentTimeMillis();
          channel.getOutputStream().write(LINE_FEED);
          Thread.sleep(TIMEOUT * 1000);
          if (previousAliveTime < submitTime) {
            if (isConnected()) {
              WarnLogMessage.issue(loggingTarget, "Connection was lost");
              disconnect(false);
              return;
            }
          }
        }
      } catch (IOException | InterruptedException e) {
        //NOP
      }

      if (streamThread != null) {
        streamThread.interrupt();
      }
    });
    watchdogThread.start();
  }

  private void stopWatchdog() {
    if (watchdogThread != null) {
      watchdogThread.interrupt();
      watchdogThread = null;
    }
  }

  private void updateAliveTime() {
    previousAliveTime = System.currentTimeMillis();
  }

  public static String getSessionReport() {
    String report = "";
    for (Map.Entry<String, SessionWrapperSshj> entry : sessionCache.entrySet()) {
      report += entry.getKey() + "[" + (entry.getValue() == null || entry.getValue().get() == null ? "null" : entry.getValue().size()) + "]\n";
    }
    return report;
  }

  private String getConnectionName() {
    if (tunnelSession == null) {
      return username + "@" + host + ":" + port;
    } else {
      return tunnelSession.getConnectionName() + " -> " + username + "@" + tunnelTargetHost + ":" + tunnelTargetPort;
    }
  }

  public void addIdentity(String privKeyPath) throws GeneralSecurityException, IOException {
    synchronized (sessionCache) {
      addIdentity(privKeyPath, null);
    }
  }

  public void addIdentity(String privKeyPath, String pass) throws IOException {
    synchronized (sessionCache) {
      File privKeyFile = Paths.get(privKeyPath.replaceFirst("^~", System.getProperty("user.home"))).toFile();
      switch (KeyProviderUtil.detectKeyFileFormat(privKeyFile)) {
        case PKCS8:
          fileKeyProvider = new PKCS8KeyFile();
          break;
        case OpenSSH:
          fileKeyProvider = new OpenSSHKeyFile();
          break;
        case OpenSSHv1:
          fileKeyProvider = new OpenSSHKeyV1KeyFile();
          break;
        case PuTTY:
          fileKeyProvider = new PuTTYKeyFile();
          break;
        default:
          throw new IllegalArgumentException("Unknown key file format");
      }
      if (pass == null) {
        fileKeyProvider.init(privKeyFile);
      } else {
        fileKeyProvider.init(privKeyFile, PasswordUtils.createOneOff(pass.toCharArray()));
      }
    }
  }

  public void setSession(String username , String host, int port) {
    synchronized (sessionCache) {
      this.username = username;
      this.host = host;
      this.port = port;

      this.tunnelTargetHost = host;
      this.tunnelTargetPort = port;
    }
  }

  public boolean isConnected() {
    if (sessionWrapper == null) {
      return false;
    }

    synchronized (sessionWrapper) {
      SSHClient session = sessionWrapper.get();
      if (session != null) {
        return session.isConnected() && session.isAuthenticated();
      }
      return false;
    }
  }

  public void connect(boolean retry) throws IOException, UnresolvedAddressException {
    synchronized (sessionCache) {
      sessionWrapper = sessionCache.get(getConnectionName());
      if (sessionWrapper == null) {
        sessionWrapper = new SessionWrapperSshj();
        sessionCache.put(getConnectionName(), sessionWrapper);
      }
    }

    synchronized (sessionWrapper) {
      sessionWrapper.link(this);

      boolean connected = false;
      int waitTime = 5;
      do {
        try {
          if (!isConnected()) {
            SSHClient session = new SSHClient();
            session.addHostKeyVerifier(new PromiscuousVerifier());

            if (tunnelSession == null) {
              session.connect(host, port);
            } else {
              tunnelSession.connect(retry);
              session.connectVia(tunnelSession.getForwardingConnection(host, port));
            }
            sessionWrapper.set(session);

            if (fileKeyProvider != null) {
              session.authPublickey(username, fileKeyProvider);
            } else {
              session.authPublickey(username);
            }

            //startWatchdog();
            connected = true;
          } else {
            connected = true;
          }
        } catch (Exception e) {
          if (Main.hibernatingFlag) {
            disconnect(false);
            return;
          }

          if (e.getCause() instanceof UnresolvedAddressException || e.getMessage().toLowerCase().equals("userauth fail")) {
            disconnect(false);
            throw e;
          }

          if (!retry) {
            WarnLogMessage.issue(loggingTarget, e.getMessage());
            disconnect(false);
            throw e;
          } else if (!e.getMessage().toLowerCase().equals("session is already connected")) {
            WarnLogMessage.issue(loggingTarget, e.getMessage() + "\nRetry connection after " + waitTime + " sec.");
            disconnect(false);
            Simple.sleep(TimeUnit.SECONDS, waitTime);
            if (waitTime < 60) {
              waitTime += 10;
            }
          }
        }

        if (!connected) {
          disconnect(false);
        }
      } while (!connected);
    }
  }

  private DirectConnection getForwardingConnection(String targetHost, int targetPort) throws IOException {
    synchronized (sessionWrapper) {
      connect(true);
      SSHClient session = sessionWrapper.get();
      if (session != null) {
        return session.newDirectConnection(targetHost, targetPort);
      }
      return null;
    }
  }

  public void disconnect(boolean isNormal) {
    if (sessionWrapper != null) {
      synchronized (sessionWrapper) {
        if (sessionWrapper != null && sessionWrapper.get() != null) {
          SSHClient session = sessionWrapper.get();
          if (sessionWrapper.unlink(this) || !isNormal) {
            stopWatchdog();
            synchronized (sessionCache) {
              try {
                session.close();
              } catch (Throwable e) {
                //NOP
              }
              synchronized (sessionCache) {
                sessionCache.remove(getConnectionName());
              }
            }
          }

          if (tunnelSession != null) {
            tunnelSession.disconnect(isNormal);
          }
        }
      }
    }
  }

  public ExecChannel exec(String command, String workDir) throws Exception {
    synchronized (sessionWrapper) {
      ExecChannel channel = new ExecChannel();
      boolean isFailed = false;
      do {
        isFailed = false;
        try (ChannelProvider provider = new ChannelProvider()) {
          try {
            channel.exec(command, workDir, provider);
          } catch (Exception e) {
            if (Main.hibernatingFlag) {
              throw e;
            }
            if (e instanceof NullPointerException || e instanceof IllegalStateException || e.getMessage().equals("channel is not opened.")) {
              isFailed = true;
              provider.failed();
            } else {
              throw new RuntimeException(e);
            }
          }
          provider.close();
        }
      } while (isFailed);
      return channel;
    }
  }

  public LiveExecChannel execLiveCommand(String command, String workDir) throws Exception {
    synchronized (sessionWrapper) {
      LiveExecChannel channel = new LiveExecChannel();
      boolean isFailed = false;
      do {
        isFailed = false;
        ChannelProvider provider = new ChannelProvider();
        try {
          channel.exec(command, workDir, provider);
        } catch (Exception e) {
          if (Main.hibernatingFlag) {
            throw e;
          }
          if (e instanceof NullPointerException || e instanceof IllegalStateException || e.getMessage().equals("channel is not opened.")) {
            isFailed = true;
            provider.failed();
          } else {
            throw new RuntimeException(e);
          }
        }
      } while (isFailed);
      return channel;
    }
  }

  private static String commandString(String... words) {
    StringBuilder builder = new StringBuilder();
    for (String w : words) {
      builder.append("\"");
      builder.append(w.replaceAll("\\\\", "\\\\").replaceAll("\"", "\\\""));
      builder.append("\" ");
    }
    return builder.toString();
  }

  public boolean chmod(int mod, String path) throws Exception {
    synchronized (sessionWrapper) {
      String finalPath = path.replaceFirst("^~", getHomePath());
      return exec(commandString("chmod", String.valueOf(mod), finalPath), "/tmp").getExitStatus() == 0;
    }
  }

  public boolean exists(String path) throws Exception {
    synchronized (sessionWrapper) {
      String finalPath = path.replaceFirst("^~", getHomePath());
      if (path == null) {
        return false;
      }
      return exec(commandString("test", "-e", path), "/tmp").getExitStatus() == 0;
    }
  }

  public boolean mkdir(String path) throws Exception {
    synchronized (sessionWrapper) {
      String resolvedPath = path.replaceFirst("^~", getHomePath());
      return exec(commandString("mkdir", "-p", resolvedPath), "/tmp").getExitStatus() == 0;
    }
  }

  public boolean rmdir(String path, String workDir) throws Exception {
    synchronized (sessionWrapper) {
      String resolvedPath = path.replaceFirst("^~", getHomePath());
      return exec(commandString("rm", "-rf", resolvedPath), workDir).getExitStatus() == 0;
    }
  }

  public String getText(String path, String workDir) throws Exception {
    synchronized (sessionWrapper) {
      final String resolvedPath = resolvePath(path, workDir);
      return exec(commandString("cat", resolvedPath), workDir).getStdout();
    }
  }

  public boolean putText(String text, String path, String workDir) throws Exception {
    synchronized (sessionWrapper) {
      final String resolvedPath = resolvePath(path, workDir);
      byte[] data = text.getBytes();
      long currentTime = System.currentTimeMillis();
      return processScp(scpClient -> {
        try {
          Path tmpFilePath = Files.createTempFile(Constants.APP_NAME, ".tmp");
          Files.write(tmpFilePath, data);
          scpClient.upload(tmpFilePath.toString(), resolvedPath);
          Files.delete(tmpFilePath);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        return true;
      });
    }
  }

  public boolean rm(String path) throws Exception {
    synchronized (sessionWrapper) {
      String finalPath = path.replaceFirst("^~", getHomePath());
      return exec(commandString("rm", finalPath), "/tmp").getExitStatus() == 0;
    }
  }

  private String resolvePath(String path, String workDir) throws Exception {
    String resolvedPath = path;
    if (workDir != null && !"".equals(workDir)) {
      resolvedPath = Paths.get(workDir).resolve(path).normalize().toString();
    }
    resolvedPath = resolvedPath.replaceFirst("^~", getHomePath());
    return resolvedPath;
  }

  public String getHomePath() throws Exception {
    if (homePath == null || homePath.equals("")) {
      synchronized (sessionWrapper) {
        if (homePath == null || homePath.equals("")) {
          ExecChannel channel = new ExecChannel();
          boolean isFailed = false;
          try (ChannelProvider provider = new ChannelProvider()) {
            do {
              isFailed = false;
              try {
                channel.exec("cd;pwd", "/", provider);
                if (channel.getExitStatus() != 0 || channel.getStdout().isEmpty()) {
                  throw new NullPointerException("Failed to get home path");
                }
                homePath = channel.getStdout().trim();
              } catch (Exception e) {
                if (Main.hibernatingFlag) {
                  throw e;
                }
                if (e instanceof NullPointerException
                  || e instanceof IllegalStateException || e.getMessage().equals("channel is not opened.")) {
                  isFailed = true;
                  provider.failed();
                } else {
                  throw new RuntimeException(e);
                }
              }
            } while (isFailed);
            provider.close();
          }
        }
      }
    }
    return homePath;
  }

  public boolean isDirectory(String remote) throws Exception {
    return exec(commandString("test", "-d", remote), "/tmp").getExitStatus() == 0;
  }

  public boolean scp(String remote, File local, String workDir, Boolean isDir) throws Exception {
    synchronized (sessionWrapper) {
      try {
        final String resolvedRemote = resolvePath(remote, workDir);
        boolean isDirectory = (isDir == null ? isDirectory(resolvedRemote) : isDir);

        if (isDirectory) {
          Files.createDirectories(local.toPath().getParent());
          processScp(client -> {
            try {
              transferFiles(resolvedRemote, local.toPath(), client, this);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
            return true;
          });
        } else {
          Files.createDirectories(local.toPath().getParent());
          processScp(client -> {
            try {
              transferFile(resolvedRemote, local.toPath(), client);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
            return true;
          });
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return true;
    }
  }

  public boolean scp(File local, String remote, String workDir) throws Exception {
    synchronized (sessionWrapper) {
      try {
        final String resolvedRemote = resolvePath(remote, workDir);
        if (local.isDirectory()) {
          mkdir(resolvedRemote);
          processScp(client -> {
            try {
              for (File file : local.listFiles()) {
                transferFiles(file, resolvedRemote, client, this);
              }
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
            return true;
          });
        } else {
          mkdir(Paths.get(resolvedRemote).getParent().toString());
          processScp(client -> {
            try {
              transferFile(local, resolvedRemote, client, this);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
            return true;
          });
        }
      } catch (Exception e) {
        //WarnLogMessage.issue(loggingTarget, e);
        //return false;
        throw new RuntimeException(e);
      }
      return true;
    }
  }

  private static void transferFiles(String remotePath, Path localPath, SCPFileTransfer sftpClient, SshSessionSshj session) throws Exception {
    //System.out.println(localPath + "   <<<---  " + remotePath);
    String name = Paths.get(remotePath).getFileName().toString();
    if(session.isDirectory(remotePath)){
      Files.createDirectories(localPath.resolve(name));
      SCPDownloadClient recursiveClient = sftpClient.newSCPDownloadClient();
      recursiveClient.setRecursiveMode(true);
      recursiveClient.copy(remotePath, new FileSystemFile(localPath.toFile()));
    } else {
      transferFile(remotePath, localPath.resolve(name), sftpClient);
    }
  }

  private static void transferFile(String remotePath, Path localPath, SCPFileTransfer sftpClient) throws IOException {
    sftpClient.download(remotePath, localPath.toString());
  }

  private static void transferFiles(File localFile, String destPath, SCPFileTransfer sftpClient, SshSessionSshj session) throws Exception {
    //System.out.println(localFile + "   --->>>  " + destPath);
    if(localFile.isDirectory()){
      destPath = destPath + "/" + localFile.getName();
      try {
        session.mkdir(destPath);
      } catch (Exception e) {
        //NOP????
      }
      for (File file: localFile.listFiles()){
        transferFiles(file, destPath, sftpClient, session);
      }
    } else {
      transferFile(localFile, destPath + "/" + localFile.getName(), sftpClient, session);
    }
  }

  private static void transferFile(File localFile, String destPath, SCPFileTransfer sftpClient, SshSessionSshj session) throws Exception {
    sftpClient.upload(localFile.toString(), destPath);
    session.chmod(getPermissionDec(localFile.toPath()), destPath);
  }

  public static int getPermissionDec(Path path) {
    try {
      String perm = PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
      perm = perm.substring(perm.length() - 9);
      perm = perm.replaceAll("-", "0").replaceAll("[^0]", "1");
      return (Integer.parseInt(perm.substring(0, 3), 2) * 100)
        + (Integer.parseInt(perm.substring(3, 6), 2) * 10)
        + Integer.parseInt(perm.substring(6, 9), 2);
    } catch (IOException e) {
      return 0;
    }
  }

  private static String localExec(String command) {
    String result = "";
    ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", command);
    processBuilder.redirectErrorStream(true);

    try {
      Process process = processBuilder.start();
      return IOStreamUtil.readString(process.getInputStream());
    } catch (IOException e) {
      WarnLogMessage.issue(e);
    }

    return result;
  }

  public void close() throws Exception {
    disconnect(true);
  }

  private boolean processScp(Function<SCPFileTransfer, Boolean> process) throws Exception {
    boolean result = false;
    boolean isFailed;
    do {
      isFailed = false;

      try (ChannelProvider provider = new ChannelProvider()) {
        try {
          result = process.apply(provider.createScpChannel());
          provider.close();
        } catch (Exception e) {
          if (Main.hibernatingFlag) {
            throw e;
          }
          if ( e.toString().contains("Channel is being closed")
            || e.toString().contains("channel is not opened")
            || e.toString().contains("SSH_FX_FAILURE") ) {
            e.printStackTrace();
            isFailed = true;
            provider.failed();
          } else {
            throw e;
          }
        }
      }
    } while (isFailed);
    return result;
  }

  public class LiveExecChannel {
    private Session.Command channel;
    private String submittedCommand;
    private OutputStream outputStream;
    private InputStream inputStream;
    private InputStream errorStream;
    private int exitStatus;

    public LiveExecChannel() throws Exception {
      exitStatus = -1;
    }

    public OutputStream getOutputStream() {
      return outputStream;
    }

    public InputStream getInputStream() {
      return inputStream;
    }

    public InputStream getErrorStream() {
      return errorStream;
    }

    public int getExitStatus() {
      return exitStatus;
    }

    public String getSubmittedCommand() {
      return submittedCommand;
    }

    public LiveExecChannel exec(String command, String workDir, ChannelProvider provider) throws Exception {
      submittedCommand = "cd " + workDir + " && " + command;
      String fullCommand = "sh -c '" + submittedCommand.replaceAll("'", "'\\\\''") + "'";
      channel = provider.createExecChannel(fullCommand);
      outputStream = channel.getOutputStream();
      inputStream = channel.getInputStream();
      errorStream = channel.getErrorStream();
      return this;
    }

    public void close() {
      try {
        channel.join(Constants.COMMUNICATION_TIMEOUT, TimeUnit.MILLISECONDS);
        exitStatus = channel.getExitStatus();
      } catch (Exception e) {
        WarnLogMessage.issue(e);
      }
      try {
        channel.close();
      } catch (Exception e) {
        WarnLogMessage.issue(e);
      }
    }
  }

  public class ExecChannel {
    private String submittedCommand;
    private String stdout;
    private String stderr;
    private int exitStatus;

    public ExecChannel() throws Exception {
      stdout = "";
      stderr = "";
      exitStatus = -1;
    }

    public String getStdout() {
      return stdout;
    }

    public String getStderr() {
      return stderr;
    }

    public int getExitStatus() {
      return exitStatus;
    }

    public String getSubmittedCommand() {
      return submittedCommand;
    }

    public ExecChannel exec(String command, String workDir, ChannelProvider provider) throws Exception {
      submittedCommand = "cd " + workDir + " && " + command;
      String fullCommand = "sh -c '" + submittedCommand.replaceAll("'", "'\\\\''") + "'";

      try (Session.Command channel = provider.createExecChannel(fullCommand);
           ByteArrayOutputStream stdoutOutputStream = new ByteArrayOutputStream();
           ByteArrayOutputStream stderrOutputStream = new ByteArrayOutputStream()) {
        int timeoutCount = 0;
        while (LONG_TIMEOUT > TIMEOUT * timeoutCount) {
          try {
            channel.join(TIMEOUT, TimeUnit.SECONDS);
            exitStatus = channel.getExitStatus();
            break;
          } catch (RuntimeException e) {
            InfoLogMessage.issue(loggingTarget, "waiting a response");
            timeoutCount += 1;
            if (LONG_TIMEOUT <= TIMEOUT * timeoutCount) {
              throw new FailedToControlRemoteException(e);
            }
          }
        }

        try (InputStream stdoutInputStream = channel.getInputStream();
             InputStream stderrInputStream = channel.getErrorStream()) {
          IOStreamUtil.pipe(stdoutInputStream, stdoutOutputStream);
          IOStreamUtil.pipe(stderrInputStream, stderrOutputStream);
        }

        stdout = stdoutOutputStream.toString();
        stderr = stderrOutputStream.toString();
      }
      return this;
    }
  }

  class ChannelProvider implements AutoCloseable {
    int failedCount;
    boolean isSessionClosed;
    DebugElapsedTime debugElapsedTime;
    boolean isClosed = false;

    Session channel = null;
    Session.Command subChannel = null;

    public ChannelProvider() {
      debugElapsedTime = new DebugElapsedTime("NULL: ");
      failedCount = 0;
      isSessionClosed = false;
      channel = null;
    }

    public Session.Command createExecChannel(String command) {
      debugElapsedTime = new DebugElapsedTime("EXEC: ");
      boolean isFailed = false;
      do {
        isFailed = false;
        checkSession();
        try {
          channel = sessionWrapper.get().startSession();
          subChannel = channel.exec(command);
        } catch (IOException e) {
          isFailed = true;
          failed();
        }
      } while (isFailed);
      return subChannel;
    }

    public SCPFileTransfer createScpChannel() {
      debugElapsedTime = new DebugElapsedTime("SCP: ");
      SCPFileTransfer channel = null;
      boolean isFailed = false;
      do {
        isFailed = false;
        checkSession();
        try {
          channel = sessionWrapper.get().newSCPFileTransfer();
        } catch (NullPointerException e) {
          isFailed = true;
          failed();
        }
      } while (isFailed);
      return channel;
    }


    private void checkSession() {
      if (!isConnected()) {
        //isSessionClosed = true; //TODO: check
        try {
          connect(true);
        } catch (IOException e) {
          throw new FailedToAcquireConnectionException(e);
        }
      }
    }

    public void failed() {
      failedCount += 1;
      if (failedCount <= 1) {
        resetChannel();
        InfoLogMessage.issue(loggingTarget, "Retry to open channel after 1 sec.");
        Simple.sleep(TimeUnit.SECONDS, failedCount);
      } else {
        resetConnection();
        int sleepTime = Math.min(60, failedCount);
        InfoLogMessage.issue(loggingTarget, "Reset the session and retry to open channel after " + sleepTime + " sec.");
        Simple.sleep(TimeUnit.SECONDS, sleepTime);
      }
    }

    private void resetChannel() {
      if (subChannel != null) {
        try {
          subChannel.close();
        } catch (TransportException | ConnectionException e) {
          //NOP
        }
        subChannel = null;
      }
      if (channel != null) {
        try {
          channel.close();
        } catch (TransportException | ConnectionException e) {
          //NOP
        }
        channel = null;
      }
    }

    private void resetConnection() {
      resetChannel();
      disconnect(false);
    }

    @Override
    public void close() throws Exception {
      if (!isClosed) {
        resetChannel();

        if (isSessionClosed) {
          disconnect(true);
        }

        if (System.getenv("DEBUG") != null) {
          if (System.getenv("DEBUG").equals("1")) {
            debugElapsedTime.print();
          } else if (System.getenv("DEBUG").equals("2")) {
            debugElapsedTime.print();
            try {
              throw new Exception("DEBUG MESSAGE");
            } catch (Exception e) {
              Simple.printSimpleStackTrace(e);
            }
          }
        }

        isClosed = true;
      }
    }
  }
}
