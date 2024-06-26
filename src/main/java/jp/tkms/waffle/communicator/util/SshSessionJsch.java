package jp.tkms.waffle.communicator.util;

import com.jcraft.jsch.*;
import jp.tkms.waffle.Main;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.log.message.WarnLogMessage;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SshSessionJsch {
  private static final Map<String, SessionWrapperJsch> sessionCache = new HashMap<>();

  private final String DEFAULT_CONFIG_FILE = System.getProperty("user.home") + "/.ssh/config";
  private final String DEFAULT_PRIVKEY_FILE = System.getProperty("user.home") + "/.ssh/id_rsa";
  protected static JSch jsch = new JSch();
  //protected Session session;
  private SessionWrapperJsch sessionWrapper = null;
  Semaphore channelSemaphore = new Semaphore(4);
  String username;
  String host;
  int port;
  Computer loggingTarget;
  SshSessionJsch tunnelSession;

  private String tunnelTargetHost;
  private int tunnelTargetPort;

  public SshSessionJsch(Computer loggingTarget, SshSessionJsch tunnelSession) throws JSchException {
    this.loggingTarget = loggingTarget;
    this.tunnelSession = tunnelSession;
    //this.jsch = new JSch();
    if (Files.exists(Paths.get(DEFAULT_CONFIG_FILE))) {
      try {
        jsch.setConfigRepository(OpenSSHConfig.parseFile(DEFAULT_CONFIG_FILE));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    if (Files.exists(Paths.get(DEFAULT_PRIVKEY_FILE))) {
      jsch.addIdentity(DEFAULT_PRIVKEY_FILE);
    }
  }

  public static String getSessionReport() {
    String report = "";
    for (Map.Entry<String, SessionWrapperJsch> entry : sessionCache.entrySet()) {
      report += entry.getKey() + "[" + (entry.getValue() == null || entry.getValue().get() == null ? "null" : entry.getValue().size()) + "]\n";
    }
    return report;
  }

  public String getConnectionName() {
    if (tunnelSession == null) {
      return username + "@" + host + ":" + port;
    } else {
      return tunnelSession.getConnectionName() + " -> " + username + "@" + tunnelSession.getTunnelTargetHost() + ":" + tunnelSession.getTunnelTargetPort();
    }
  }

  protected String getTunnelTargetHost() {
    return tunnelTargetHost;
  }

  protected int getTunnelTargetPort() {
    return tunnelTargetPort;
  }

  public boolean isConnected() {
    if (sessionWrapper.get() != null) {
      return sessionWrapper.get().isConnected();
    }
    return false;
  }

  public void addIdentity(String privKey) throws JSchException {
    jsch.addIdentity(privKey);
  }

  public void addIdentity(String privKey, String pass) throws JSchException {
    jsch.addIdentity(privKey, pass);
  }

  public void setSession(String username , String host, int port) throws JSchException {
    this.username = username;
    this.host = host;
    this.port = port;
  }

  public void connect(boolean retry) throws JSchException {
    synchronized (sessionCache) {
      sessionWrapper = sessionCache.get(getConnectionName());
      if (sessionWrapper == null) {
        sessionWrapper = new SessionWrapperJsch();
        sessionCache.put(getConnectionName(), sessionWrapper);
      }
    }

    synchronized (sessionWrapper) {
      sessionWrapper.link(this);

      boolean connected = false;
      int waitTime = 10;
      do {
        try {
          /*
          if (sessionWrapper.getValue() != null) {
            session.disconnect();
          }
           */
          if (sessionWrapper.get() == null || !sessionWrapper.get().isConnected()) {
            sessionWrapper.set(jsch.getSession(username, host, port));
            sessionWrapper.get().setConfig("StrictHostKeyChecking", "no");
            sessionWrapper.get().connect();
            connected = true;
          } else if (sessionWrapper.get().isConnected()) {
            connected = true;
          }
        } catch (JSchException e) {
          if (Main.hibernatingFlag) {
            //sessionWrapper.unlink(this);
            disconnect();
            return;
          }

          if (e.getMessage().toLowerCase().equals("userauth fail")) {
            //sessionWrapper.unlink(this);
            disconnect();
            throw e;
          }

          if (!retry) {
            WarnLogMessage.issue(loggingTarget, e.getMessage());
            //sessionWrapper.unlink(this);
            disconnect();
            throw e;
          } else if (!e.getMessage().toLowerCase().equals("session is already connected")) {
            WarnLogMessage.issue(loggingTarget, e.getMessage() + "\nRetry connection after " + waitTime + " sec.");
            //sessionWrapper.getValue().disconnect();
            disconnect();
            try {
              TimeUnit.SECONDS.sleep(waitTime);
            } catch (InterruptedException ex) {
              ex.printStackTrace();
            }
            waitTime += 10;
          }
        }
      } while (!connected);

      if (!connected) {
        //sessionWrapper.unlink(this);
        disconnect();
      }
    }
  }

  public void disconnect() {
    synchronized (sessionWrapper) {
      if (channelSftp != null) {
        channelSftp.disconnect();
      }
      if (sessionWrapper.get() != null) {
        Session session = sessionWrapper.get();
        if (sessionWrapper.unlink(this)) {
          session.disconnect();
          sessionCache.remove(getConnectionName());
        }
      }

      if (tunnelSession != null) {
        tunnelSession.disconnect();
      }
    }
  }

  protected Channel openChannel(String type) throws JSchException, InterruptedException {
    channelSemaphore.acquire();

    return sessionWrapper.get().openChannel(type);
  }

  public int setPortForwardingL(String hostName, int rport) throws JSchException {
    tunnelTargetHost = hostName;
    tunnelTargetPort = rport;
    return sessionWrapper.get().setPortForwardingL(0, hostName, rport);
  }

  public SshChannelJsch exec(String command, String workDir) throws JSchException, InterruptedException {
    synchronized (sessionWrapper) {
      SshChannelJsch channel = new SshChannelJsch((ChannelExec) openChannel("exec"));
      int count = 0;
      boolean failed = false;
      do {
        try {
          channel.exec(command, workDir);
        } catch (JSchException e) {
          if (e.getMessage().equals("channel is not opened.")) {
            count += 1;

            failed = true;
            channelSemaphore.release();

            WarnLogMessage.issue(loggingTarget, "Retry to open channel after " + count + " sec.");
            try {
              TimeUnit.SECONDS.sleep(count);
            } catch (InterruptedException ex) {
              ex.printStackTrace();
            }
            channel = new SshChannelJsch((ChannelExec) openChannel("exec"));
          } else {
            throw e;
          }
        }
      } while (failed);
      channelSemaphore.release();
      return channel;
    }
  }

  public boolean chmod(int mod, Path path) throws JSchException {
    return processSftp(channelSftp -> {
      try {
        channelSftp.chmod(Integer.parseInt("" + mod, 8), path.toString());
      } catch (SftpException e) {
        return false;
      }
      return true;
    });
  }

  public boolean exists(Path path) throws JSchException {
    if (path == null) {
      return false;
    }
    return processSftp(channelSftp -> {
      try {
        channelSftp.stat(path.toString());
      } catch (SftpException e) {
        return false;
      }
      return true;
    });
  }

  public boolean mkdir(Path path) throws JSchException {
    return processSftp(channelSftp -> {
      try {
        channelSftp.stat(path.getParent().toString());
      } catch (SftpException e) {
        if (e.getMessage().startsWith("No such file")) {
          try {
            mkdir(path.getParent());
          } catch (JSchException ex) {
            WarnLogMessage.issue(loggingTarget, e);
            return false;
          }
        }
      }
      try {
        channelSftp.stat(path.toString());
        return true;
      } catch (SftpException e) {
        if (e.getMessage().startsWith("No such file")) {
          try {
            channelSftp.mkdir(path.toString());
          } catch (SftpException ex) {
            WarnLogMessage.issue(loggingTarget, ex);
            return false;
          }
        }
      }
      return true;
    });
  }

  public boolean rmdir(String path, String workDir) throws JSchException, InterruptedException {
    SshChannelJsch channel = exec("rm -rf " + path, workDir);

    return (channel.getExitStatus() == 0);
  }

  public String getText(String path, String workDir) throws JSchException {
    //SshChannel channel = exec("cat " + path, workDir);

    //return channel.getStdout();
    final String[] resultText = new String[1];
    processSftp(channelSftp -> {
      try {
        if (workDir != null && !"".equals(workDir)) {
          channelSftp.cd(workDir);
        }
        InputStream inputStream = channelSftp.get(path);
        resultText[0] = new BufferedReader(new InputStreamReader(inputStream)).lines().collect(Collectors.joining("\n"));
        inputStream.close();
      } catch (SftpException | IOException e) {
        WarnLogMessage.issue(loggingTarget, e);
        return false;
      }
      return true;
    });

    //TODO: implement an exception handling

    return resultText[0];
  }

  public synchronized boolean putText(String text, String path, String workDir) throws JSchException {
    return processSftp(channelSftp -> {
      try {
        channelSftp.cd(workDir);
        channelSftp.put (new ByteArrayInputStream(text.getBytes ()), path);
      } catch (SftpException e) {
        WarnLogMessage.issue(loggingTarget, e);
        return false;
      }
      return true;
    });
  }

  public synchronized boolean rm(Path path) throws JSchException {
    return processSftp(channelSftp -> {
      try {
        channelSftp.rm(path.toString());
      } catch (SftpException e) {
        WarnLogMessage.issue(loggingTarget, e);
        return false;
      }
      return true;
    });
  }

  public synchronized boolean scp(String remote, File local, String workDir) throws JSchException {
    return processSftp(channelSftp -> {
      try {
        channelSftp.cd(workDir);
        if(channelSftp.stat(remote).isDir()) {
          Files.createDirectories(local.toPath());
          for (Object o : channelSftp.ls(remote)) {
            ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) o;
            transferFiles(entry.getFilename(), local.toPath(), channelSftp);
          }
        } else {
          Files.createDirectories(local.toPath().getParent());
          transferFile(remote, local.toPath(), channelSftp);
        }
      } catch (Exception e) {
        WarnLogMessage.issue(loggingTarget, e);
        return false;
      }
      return true;
    });
  }

  public synchronized boolean scp(File local, String dest, String workDir) throws JSchException {
    return processSftp(channelSftp -> {
      try {
        channelSftp.cd(workDir);
        if (local.isDirectory()) {
          mkdir(Paths.get(dest));
          channelSftp.cd(dest);
          for(File file: local.listFiles()){
            transferFiles(file, dest, channelSftp);
          }
        } else {
          transferFile(local, dest, channelSftp);
        }
      } catch (Exception e) {
        WarnLogMessage.issue(loggingTarget, e);
        return false;
      }
      return true;
    });
  }

  private ChannelSftp channelSftp;
  private boolean processSftp(Function<ChannelSftp, Boolean> process) throws JSchException {
    synchronized (sessionWrapper) {
      int count = 0;
      boolean result = false;
      boolean failed;
      do {
        failed = false;

        try {
          if (channelSftp == null || channelSftp.isClosed()) {
            channelSftp = (ChannelSftp) sessionWrapper.get().openChannel("sftp");
            channelSftp.connect();
          }

          result = process.apply(channelSftp);
        } catch (JSchException e) {
          if (e.getMessage().equals("channel is not opened.")) {
            count += 1;

            failed = true;
            channelSftp = null;
            WarnLogMessage.issue(loggingTarget, "Retry to open channel after " + count + " sec.");
            try {
              TimeUnit.SECONDS.sleep(count);
            } catch (InterruptedException ex) {
              ex.printStackTrace();
            }
          } else {
            throw e;
          }
        }
      } while (failed);
      return result;
    }
  }

  private static void transferFiles(String remotePath, Path localPath, ChannelSftp clientChannel) throws SftpException, IOException {
    String name = Paths.get(remotePath).getFileName().toString();
    if(clientChannel.stat(remotePath).isDir()){
      Files.createDirectories(localPath.resolve(name));

      for(Object o: clientChannel.ls(remotePath)){
        ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) o;
        transferFiles(entry.getFilename(), localPath.resolve(name), clientChannel);
      }
    } else {
      transferFile(remotePath, localPath.resolve(name), clientChannel);
    }
  }

  private static void transferFile(String remotePath, Path localPath, ChannelSftp clientChannel) throws SftpException, FileNotFoundException {
    try {
      Files.copy(clientChannel.get(remotePath), localPath, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void transferFiles(File localFile, String destPath, ChannelSftp clientChannel) throws SftpException, FileNotFoundException {
    System.out.println(localFile + "   --->>>  " + destPath);
    if(localFile.isDirectory()){
      try {
        clientChannel.mkdir(localFile.getName());
      } catch (SftpException e) {}

      destPath = destPath + "/" + localFile.getName();
      clientChannel.cd(destPath);

      for(File file: localFile.listFiles()){
        transferFiles(file, destPath, clientChannel);
      }
      clientChannel.cd("..");
    } else {
      transferFile(localFile, localFile.getName(), clientChannel);
    }
  }

  private static void transferFile(File localFile, String destPath, ChannelSftp clientChannel) throws SftpException, FileNotFoundException {
    clientChannel.put(localFile.getAbsolutePath(), destPath, ChannelSftp.OVERWRITE);

    String perm = localExec("stat '" + localFile.getAbsolutePath() + "' -c '%a'").replaceAll("\\r|\\n", "");
    clientChannel.chmod(Integer.parseInt(perm, 8), destPath);
  }

  private static String localExec(String command) {
    String result = "";
    ProcessBuilder p = new ProcessBuilder("sh", "-c", command);
    p.redirectErrorStream(true);

    try {
      Process process = p.start();

      try (BufferedReader r
             = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
        String line;
        while ((line = r.readLine()) != null) {
          result += line + "\n";
        }
      }

    } catch (IOException e) {
      e.printStackTrace();
    }

    return result;
  }

  public static class SshChannelJsch {
    private final int waitTime = 25;
    private final int maxOfWait = 400;

    private ChannelExec channel;

    private String submittedCommand;
    private String stdout;
    private String stderr;
    private int exitStatus;

    public SshChannelJsch(ChannelExec channel) throws JSchException {
      this.channel = channel;
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

    public SshChannelJsch exec(String command, String workDir) throws JSchException {
      submittedCommand = "cd " + workDir + " && " + command;
      channel.setCommand("sh -c '" +  submittedCommand.replaceAll("'", "'\\\\''") + "'");
      channel.connect();

      try {
        BufferedInputStream outStream = new BufferedInputStream(channel.getInputStream());
        BufferedInputStream errStream = new BufferedInputStream(channel.getErrStream());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (true) {
          int len = outStream.read(buf);
          if (len <= 0) {
            break;
          }
          outputStream.write(buf, 0, len);
        }
        stdout = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);

        outputStream = new ByteArrayOutputStream();
        buf = new byte[1024];
        while (true) {
          int len = errStream.read(buf);
          if (len <= 0) {
            break;
          }
          outputStream.write(buf, 0, len);
        }
        stderr = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
      } catch (IOException e) {
        e.printStackTrace();
      }

      channel.disconnect();
      int sleepCount = 0;
      do {
        try {
          Thread.sleep(waitTime);
        } catch (InterruptedException e) { }
      } while (!channel.isClosed() && sleepCount++ < maxOfWait);

      exitStatus = channel.getExitStatus();

      return this;
    }
  }
}
