package jp.tkms.waffle.communicator;

import com.eclipsesource.json.JsonValue;
import jp.tkms.waffle.communicator.annotation.CommunicatorDescription;
import jp.tkms.waffle.communicator.process.RemoteProcess;
import jp.tkms.waffle.data.ComputerTask;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.util.WrappedJson;
import jp.tkms.waffle.data.util.WrappedJsonArray;
import jp.tkms.waffle.exception.FailedToControlRemoteException;
import jp.tkms.waffle.exception.FailedToTransferFileException;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

@CommunicatorDescription("Local (limited by job number)")
public class JobNumberLimitedLocalSubmitter extends AbstractSubmitter {

  public JobNumberLimitedLocalSubmitter(Computer computer) {
    super(computer);
  }

  @Override
  public AbstractSubmitter connect(boolean retry) {
    switchToStreamMode();
    return this;
  }

  @Override
  public WrappedJsonArray getFormSettings() {
    WrappedJsonArray settings = super.getFormSettings();
    {
      WrappedJson entry = new WrappedJson();
      entry.put(KEY_NAME, KEY_XSUB_TYPE);
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
    return true;
  }

  @Override
  public Path parseHomePath(String pathString) {
    if (pathString.startsWith("~")) {
      pathString = pathString.replaceAll("^~", System.getProperty("user.home"));
    } else if (!pathString.startsWith("/")) {
      pathString = Paths.get(pathString).toAbsolutePath().toString();
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
      Files.createDirectories(path);
    } catch (IOException e) {
      throw new FailedToControlRemoteException(e);
    }
  }

  @Override
  public String exec(String command) throws FailedToControlRemoteException {
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
      throw new FailedToControlRemoteException(e);
    }

    return result;
  }

  @Override
  protected RemoteProcess startProcess(String command) throws FailedToControlRemoteException {
    RemoteProcess remoteProcess = new RemoteProcess();
    ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", command);
    try {
      Process process = processBuilder.start();
      remoteProcess.setFinalizer(() -> {
        process.destroy();
      });
      remoteProcess.setStream(process.getOutputStream(), process.getInputStream(), process.getErrorStream());
    } catch (IOException e) {
      throw new FailedToControlRemoteException(e);
    }
    return remoteProcess;
  }

  @Override
  public void chmod(int mod, Path path) throws FailedToControlRemoteException {
    exec("chmod " + mod + " '" + path.toString() + "'");
  }

  @Override
  boolean exists(Path path) {
    return Files.exists(path);
  }

  @Override
  boolean deleteFile(Path path) throws FailedToControlRemoteException {
    try {
      Files.delete(path);
      return true;
    } catch (IOException e) {
      throw new FailedToControlRemoteException(e);
    }
  }

  @Override
  public String getFileContents(ComputerTask run, Path path) throws FailedToTransferFileException {
    String result = null;
    try {
      result = exec("cat " + getContentsPath(run, path));
    } catch (FailedToControlRemoteException e) {
      throw new FailedToTransferFileException(e);
    }
    return result;
  }

  @Override
  public void transferFilesToRemote(Path localPath, Path remotePath) throws FailedToTransferFileException {
    try {
      Files.createDirectories(remotePath.getParent());
      if (Files.isDirectory(localPath)) {
        transferDirectory(localPath.toFile(), remotePath.toFile());
      } else {
        Files.copy(localPath, remotePath, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      throw new FailedToTransferFileException(e);
    }
  }

  @Override
  public void transferFilesFromRemote(Path remotePath, Path localPath, Boolean isDir) throws FailedToTransferFileException {
    try {
      Files.createDirectories(localPath.getParent());
      if (Files.isDirectory(remotePath)) {
        transferDirectory(remotePath.toFile(), localPath.toFile());
      } else {
        Files.copy(remotePath, localPath, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      throw new FailedToTransferFileException(e);
    }
  }

  @Override
  public WrappedJson getDefaultParameters(Computer computer) {
    return new WrappedJson();
  }

  void transferDirectory(File src, File dest) throws IOException {
    if (src.isDirectory()) {
      if (!dest.exists()) {
        dest.mkdir();
      }
      String files[] = src.list();
      for (String file : files) {
        File srcFile = new File(src, file);
        File destFile = new File(dest, file);
        transferDirectory(srcFile, destFile);
      }
    }else{
      Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  public static void deleteDirectory(final String dirPath) throws Exception {
    File file = new File(dirPath);
    recursiveDeleteFile(file);
  }

  private static void recursiveDeleteFile(final File file) throws Exception {
    if (!file.exists()) {
      return;
    }
    if (file.isDirectory()) {
      for (File child : file.listFiles()) {
        recursiveDeleteFile(child);
      }
    }
    file.delete();
  }
}
