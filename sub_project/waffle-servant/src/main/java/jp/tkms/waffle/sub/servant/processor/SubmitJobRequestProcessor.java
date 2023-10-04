package jp.tkms.waffle.sub.servant.processor;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import jp.tkms.waffle.sub.servant.Constants;
import jp.tkms.waffle.sub.servant.DirectoryHash;
import jp.tkms.waffle.sub.servant.Envelope;
import jp.tkms.waffle.sub.servant.XsubFile;
import jp.tkms.waffle.sub.servant.message.request.SubmitJobMessage;
import jp.tkms.waffle.sub.servant.message.response.*;
import org.jruby.embed.LocalContextScope;
import org.jruby.embed.LocalVariableBehavior;
import org.jruby.embed.PathType;
import org.jruby.embed.ScriptingContainer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SubmitJobRequestProcessor extends RequestProcessor<SubmitJobMessage> {
  public static final String XSUB_TYPE = "XSUB_TYPE";
  public static final String NONE = "None";
  public static final int MAX_REMOVING_RETRYING = 100;

  protected SubmitJobRequestProcessor() {
  }

  @Override
  protected void processIfMessagesExist(Path baseDirectory, Envelope request, Envelope response, ArrayList<SubmitJobMessage> messageList) throws ClassNotFoundException, IOException {
    ArrayList<Path> removingList = new ArrayList<>();
    Map<String, String> environments = new HashMap(System.getenv());
    if (!environments.containsKey(XSUB_TYPE)) {
      environments.put(XSUB_TYPE, NONE);
    }

    messageList.stream().parallel().forEach(message -> {
      Path workingDirectory = baseDirectory.resolve(message.getWorkingDirectory()).toAbsolutePath().normalize();

      if (!Files.exists(workingDirectory)) {
        response.add(new RequestRepreparingMessage(message));
        return;
      }

      Path jobIdPath = workingDirectory.resolve(Constants.JOBID_FILE);
      if (Files.exists(jobIdPath)) {
        try {
          response.add(new UpdateJobIdMessage(message, new String(Files.readAllBytes(jobIdPath)), workingDirectory));
          return;
        } catch (IOException e) {
          response.add(new JobExceptionMessage(message, e.toString()));
          return;
        }
      }

      if (!Files.exists(workingDirectory.resolve(message.getCommand()))) {
        synchronized (removingList) {
          removingList.add(workingDirectory);
        }
        response.add(new RequestRepreparingMessage(message));
        return;
      }

      if (Files.exists(workingDirectory.resolve(Constants.XSUB_LOG_FILE))) { // If already executed, remove and request resubmit
        try {
          Files.deleteIfExists(workingDirectory.resolve(Constants.ALIVE));
        } catch (IOException e) {
          //NOP
        }
        synchronized (removingList) {
          removingList.add(workingDirectory);
        }
        response.add(new RequestRepreparingMessage(message));
        return;
      }

      if (message.getExecutableDirectory() != null) {
        DirectoryHash executableDirectoryHash = new DirectoryHash(baseDirectory, message.getExecutableDirectory(), false);
        synchronized (this) {
          if (!executableDirectoryHash.hasHashFile()) {
            executableDirectoryHash.save();
          } else {
            if (executableDirectoryHash.update()) {
              response.add(new ExceptionMessage("EXECUTABLE FILES HAS CHANGED: " + message.getExecutableDirectory()));
            }
          }
        }
      }

      StringWriter outputWriter = new StringWriter();
      StringWriter errorWriter = new StringWriter();
      try {
        DirectoryHash directoryHash = new DirectoryHash(baseDirectory, message.getWorkingDirectory());
        directoryHash.createEmptyHashFile();

        final String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        final File currentJar = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        ProcessBuilder processBuilder = new ProcessBuilder(javaBin, "-cp", currentJar.toString(),
          "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens", "java.base/java.io=ALL-UNNAMED",
          "org.jruby.Main", XsubFile.getXsubPath(baseDirectory).toString(),
          "-p", message.getXsubParameter(), message.getCommand());
        processBuilder.directory(workingDirectory.toFile());
        environments.entrySet().forEach(entry -> {
          processBuilder.environment().put(entry.getKey(), entry.getValue());
        });
        Process process = processBuilder.start();
        Thread outputReaderThread = startStreamReaderThread(process.getInputStream(), outputWriter);
        Thread errorReaderThread = startStreamReaderThread(process.getErrorStream(), errorWriter);
        process.waitFor();
        outputReaderThread.join();
        errorReaderThread.join();
        /*
        ScriptingContainer container = new ScriptingContainer(LocalContextScope.SINGLETHREAD, LocalVariableBehavior.TRANSIENT);
        synchronized (this) {
          container.setEnvironment(environments);
          container.setCurrentDirectory(workingDirectory.toString());
          container.setArgv(new String[]{"-p", message.getXsubParameter(), message.getCommand()});
          container.setOutput(outputWriter);
          container.setError(errorWriter);
          container.runScriptlet("require 'jruby'");
          container.runScriptlet(PathType.ABSOLUTE, XsubFile.getXsubPath(baseDirectory).toString());
        }
        container.clear();
        try {
          container.finalize();
        } catch (Throwable e) {
          response.add(new ExceptionMessage(e.getMessage()));
        }
         */
        directoryHash.save();

        JsonObject jsonObject = Json.parse(outputWriter.toString()).asObject();
        Files.writeString(workingDirectory.resolve(Constants.JOBID_FILE), jsonObject.getString("job_id", "S_ERR"));
        response.add(new UpdateJobIdMessage(message, jsonObject.getString("job_id", null).toString(), workingDirectory));
      } catch (Exception e) {
        response.add(new UpdateJobIdMessage(message, "FAILED", workingDirectory));
        response.add(new UpdateStatusMessage(message, -3));
        errorWriter.flush();
        outputWriter.flush();
        String errorMessage = errorWriter.toString();
        if ("".equals(errorMessage)) {
          response.add(new JobExceptionMessage(message, e.getMessage() + "\n" + outputWriter.toString()));
        } else {
          response.add(new JobExceptionMessage(message, errorWriter.toString() + "\n" + outputWriter.toString()));
        }
      }
      try {
        errorWriter.close();
        outputWriter.close();
      } catch (IOException e) {
        //NOP
      }
    });

    if (!removingList.isEmpty()) {
      try {
        TimeUnit.SECONDS.sleep(Constants.WATCHDOG_INTERVAL);
      } catch (InterruptedException e) {
        //NOP
      }
      removingList.stream().parallel().forEach(path -> {
        for (int count = 0; count < MAX_REMOVING_RETRYING; count += 1) {
          try {
            Files.walk(path).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
          } catch (IOException e) {
            continue;
          }
          break;
        }
      });
    }
  }

  private static Thread startStreamReaderThread(InputStream inputStream, StringWriter stringWriter) {
    Thread thread = new Thread(() -> {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
        String line;
        while ((line = reader.readLine()) != null) {
          stringWriter.write(line);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    });
    thread.start();
    return thread;
  }
}
