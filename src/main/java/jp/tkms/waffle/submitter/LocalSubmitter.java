package jp.tkms.waffle.submitter;

import jp.tkms.waffle.data.Host;
import jp.tkms.waffle.data.SimulatorRun;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

public class LocalSubmitter extends AbstractSubmitter {
  @Override
  public AbstractSubmitter connect(boolean retry) {
    return this;
  }

  @Override
  String getWorkDirectory(SimulatorRun run) {
    Host host = run.getHost();
    String pathString = host.getWorkBaseDirectory() + host.getDirectorySeparetor()
      + RUN_DIR + host.getDirectorySeparetor() + run.getId();

    try {
      Files.createDirectories(Paths.get(pathString + host.getDirectorySeparetor()));
    } catch (IOException e) {
      e.printStackTrace();
    }

    return pathString;
  }

  @Override
  String getSimulatorBinDirectory(SimulatorRun run) {
    return run.getSimulator().getBinDirectoryLocation().toAbsolutePath().toString();
  }

  @Override
  void prepareSubmission(SimulatorRun run) {
  }

  @Override
  public String exec(String command) {
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

  @Override
  int getExitStatus(SimulatorRun run) {
    int status = -1;

    try {
      FileReader file
        = new FileReader(getWorkDirectory(run) + run.getHost().getDirectorySeparetor() + EXIT_STATUS_FILE);
      BufferedReader r  = new BufferedReader(file);
      String line;
      while ((line = r.readLine()) != null) {
        status = Integer.valueOf(line);
        break;
      }

      r.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    return status;
  }

  @Override
  void postProcess(SimulatorRun run) {
    try {
      //deleteDirectory(getWorkDirectory(run));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void close() {

  }

  @Override
  public void putText(SimulatorRun run, String path, String text) {
    try {
      PrintWriter pw = new PrintWriter(new BufferedWriter(
        new FileWriter(getWorkDirectory(run) + run.getHost().getDirectorySeparetor() + path)
      ));
      pw.println(text);
      pw.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public String getFileContents(SimulatorRun run, String path){
    return exec("cat " + getContentsPath(run, path));
  }

  @Override
  public JSONObject defaultParameters(Host host) {
    return new JSONObject();
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
