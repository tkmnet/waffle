package jp.tkms.waffle.communicator;

import com.eclipsesource.json.JsonValue;
import jp.tkms.waffle.communicator.annotation.CommunicatorDescription;
import jp.tkms.waffle.communicator.process.RemoteProcess;
import jp.tkms.waffle.communicator.util.SshSessionSshj;
import jp.tkms.waffle.data.ComputerTask;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.computer.MasterPassword;
import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.log.message.InfoLogMessage;
import jp.tkms.waffle.data.log.message.WarnLogMessage;
import jp.tkms.waffle.data.util.WrappedJson;
import jp.tkms.waffle.data.util.WrappedJsonArray;
import jp.tkms.waffle.exception.FailedToControlRemoteException;
import jp.tkms.waffle.exception.FailedToTransferFileException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

@CommunicatorDescription("SSH (limited by job number)")
public class JobNumberLimitedSshSubmitter extends AbstractSubmitter {
  public static final String KEY_IDENTITY_FILE = "identity_file";
  private static final String ENCRYPTED_MARK = "#*# = ENCRYPTED = #*#";
  private static final String KEY_ENCRYPTED_IDENTITY_PASS = ".encrypted_identity_pass";

  SshSessionSshj session;

  public JobNumberLimitedSshSubmitter(Computer computer) {
    super(computer);
  }

  @Override
  public AbstractSubmitter connect(boolean retry) {
    try {
      String hostName = "";
      String user = "";
      String identityFile = "";
      String identityPass = "";
      int port = 22;

      WrappedJson parameters = computer.getParametersWithDefaultParameters();
      for (Map.Entry<Object, Object> entry : parameters.entrySet()) {
        switch (entry.getKey().toString()) {
          case "host" :
            hostName = entry.getValue().toString();
            break;
          case "user" :
            user = entry.getValue().toString();
            break;
          case "identity_file" :
            identityFile = entry.getValue().toString();
            break;
          case "identity_pass" :
            if (entry.getValue().toString().equals(ENCRYPTED_MARK)) {
              identityPass = MasterPassword.getDecrypted(parameters.getString(KEY_ENCRYPTED_IDENTITY_PASS, null));
            } else {
              if (!entry.getValue().toString().equals("")) {
                computer.setParameter(KEY_ENCRYPTED_IDENTITY_PASS, MasterPassword.getEncrypted(entry.getValue().toString()));
                identityPass = entry.getValue().toString();
                computer.setParameter("identity_pass", ENCRYPTED_MARK);
              }
            }
            break;
          case "port" :
            port = Integer.parseInt(entry.getValue().toString());
            break;
        }
      }

      ArrayList<WrappedJson> tunnelList = new ArrayList<>();
      WrappedJson tunnelRootObject = parameters.getObject("tunnel", null);
      {
        WrappedJson tunnelObject = tunnelRootObject;
        while (tunnelObject != null) {
          tunnelList.add(tunnelObject);
          tunnelObject = tunnelObject.getObject("tunnel", null);
        }
      }
      Collections.reverse(tunnelList);
      SshSessionSshj tunnelSession = null;
      for (WrappedJson tunnelObject : tunnelList) {
        tunnelSession = new SshSessionSshj(computer, tunnelSession);
        tunnelSession.setSession(tunnelObject.getString("user", ""),
          tunnelObject.getString("host", ""),
          tunnelObject.getInt("port", 22));
        String tunnelIdentityPass = tunnelObject.getString("identity_pass", "");
        if (tunnelIdentityPass == null) {
          tunnelIdentityPass = "";
        } else {
          if (tunnelIdentityPass.equals(ENCRYPTED_MARK)) {
            tunnelIdentityPass = MasterPassword.getDecrypted(parameters.getString(KEY_ENCRYPTED_IDENTITY_PASS + "_1", ""));
          } else {
            if (! tunnelIdentityPass.equals("")) {
              computer.setParameter(KEY_ENCRYPTED_IDENTITY_PASS + "_1", MasterPassword.getEncrypted(tunnelIdentityPass));
              tunnelObject.put("identity_pass", ENCRYPTED_MARK);
            }
          }
        }
        if (tunnelIdentityPass.equals("")) {
          tunnelSession.addIdentity(tunnelObject.getString("identity_file", ""));
        } else {
          tunnelSession.addIdentity(tunnelObject.getString("identity_file", ""), tunnelIdentityPass);
        }
      }

      if (tunnelRootObject != null) {
        computer.setParameter("tunnel", tunnelRootObject);
      }

      session = new SshSessionSshj(computer, tunnelSession);
      session.setSession(user, hostName, port);
      if (identityPass.equals("")) {
        session.addIdentity(identityFile);
      } else {
        session.addIdentity(identityFile, identityPass);
      }
      session.connect(retry);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }

    InfoLogMessage.issue(computer, "acquired the connection");

    switchToStreamMode();
    return this;
  }

  @Override
  public WrappedJsonArray getFormSettings() {
    WrappedJsonArray settings = super.getFormSettings();
    {
      WrappedJson entry = new WrappedJson();
      entry.put(KEY_NAME, XSUB_TYPE);
      entry.put(KEY_LABEL, "Job scheduler type (for xsub)");
      entry.put(KEY_TYPE, "xsub");
      entry.put(KEY_DEFAULT, "None");
      settings.add(entry);
    }
    {
      WrappedJson entry = new WrappedJson();
      entry.put(KEY_NAME, "work_base_dir");
      entry.put(KEY_LABEL, "Work base directory on the computer");
      entry.put(KEY_TYPE, "text");
      entry.put(KEY_DEFAULT, "/tmp/waffle");
      settings.add(entry);
    }
    {
      WrappedJson entry = new WrappedJson();
      entry.put(KEY_NAME, "number_of_calculation_node");
      entry.put(KEY_LABEL, "Maximum number of jobs");
      entry.put(KEY_TYPE, "number");
      entry.put(KEY_CAST, "Integer");
      entry.put(KEY_DEFAULT, 1);
      settings.add(entry);
    }
    return settings;
  }

  @Override
  protected boolean isSubmittable(Computer computer, ComputerTask next, ArrayList<ComputerTask> list) {
    JsonValue jsonValue = (JsonValue) computer.getParameter(KEY_MAX_JOBS, this);
    if (jsonValue.isNumber()) {
      return (list.size() + (next == null ? 0 : 1)) <= jsonValue.asInt();
    }
    return false;
  }

  @Override
  public boolean isConnected() {
    if (session != null) {
      return session.isConnected();
    }
    return false;
  }

  @Override
  public void close() {
    super.close();

    if (session != null) {
      try {
        session.disconnect(true);
      } catch (Throwable e) {
        //NOP
      }
    }
  }

  @Override
  public Path parseHomePath(String pathString) {
    if (pathString.indexOf('~') == 0) {
      try {
        pathString = pathString.replaceAll("^~", session.getHomePath());
      } catch (Exception e) {
        ErrorLogMessage.issue(e);
      }
    }
    return Paths.get(pathString);
  }

  @Override
  public Path getAbsolutePath(Path path) throws FailedToControlRemoteException {
    return getWorkBaseDirectory().resolve(path);
  }

  @Override
  public void createDirectories(Path path) throws FailedToControlRemoteException {
    try {
      session.mkdir(path.toString());
    } catch (Exception e) {
      throw new FailedToControlRemoteException(new Exception(e.toString()));
    }
  }

  @Override
  public void chmod(int mod, Path path) throws FailedToControlRemoteException {
    try {
      session.chmod(mod, path.toString());
    } catch (Exception e) {
      throw new FailedToControlRemoteException(e);
    }
  }

  @Override
  public String exec(String command) {
    String result = "";

    try {
      SshSessionSshj.ExecChannel channel = session.exec(command, "");
      result += channel.getStdout();
      result += channel.getStderr();
    } catch (Exception e) {
      WarnLogMessage.issue(computer, e);
      return null;
    }

    return result;
  }

  @Override
  protected RemoteProcess startProcess(String command) throws FailedToControlRemoteException {
    RemoteProcess remoteProcess = new RemoteProcess();
    try {
      SshSessionSshj.LiveExecChannel channel = session.execLiveCommand(command, "");
      remoteProcess.setFinalizer(() -> {
        channel.close();
      });
      remoteProcess.setStream(channel.getOutputStream(), channel.getInputStream(), channel.getErrorStream());
    } catch (Exception e) {
      throw new FailedToControlRemoteException(e);
    }
    return remoteProcess;
  }

  @Override
  boolean exists(Path path) throws FailedToControlRemoteException {
    try {
      return session.exists(path.toString());
    } catch (Exception e) {
      throw new FailedToControlRemoteException(e);
    }
  }

  @Override
  boolean deleteFile(Path path) throws FailedToControlRemoteException {
    try {
      return session.rm(path.toString());
    } catch (Exception e) {
      throw new FailedToControlRemoteException(e);
    }
  }

  @Override
  public String getFileContents(ComputerTask run, Path path) {
    try {
      return session.getText(getContentsPath(run, path).toString(), "");
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public void transferFilesToRemote(Path localPath, Path remotePath) throws FailedToTransferFileException {
    try {
      session.scp(localPath.toFile(), remotePath.toString(), "/tmp");
    } catch (Exception e) {
      throw new FailedToTransferFileException(e);
    }
  }

  @Override
  public void transferFilesFromRemote(Path remotePath, Path localPath, Boolean isDir) throws FailedToTransferFileException {
    try {
      session.scp(remotePath.toString(), localPath.toFile(), "/tmp", isDir);
    } catch (Exception e) {
      throw new FailedToTransferFileException(e);
    }
  }

  @Override
  public WrappedJson getDefaultParameters(Computer computer) {
    WrappedJson jsonObject = new WrappedJson();
    jsonObject.put("host", computer.getName());
    jsonObject.put("user", System.getProperty("user.name"));
    jsonObject.put("identity_file", "~/.ssh/id_rsa");
    jsonObject.put("identity_pass", "");
    jsonObject.put("port", 22);
    return jsonObject;
  }
}
