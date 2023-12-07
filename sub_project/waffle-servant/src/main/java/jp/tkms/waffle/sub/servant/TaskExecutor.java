package jp.tkms.waffle.sub.servant;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import jp.tkms.waffle.sub.servant.pod.PodTask;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class TaskExecutor extends TaskCommand {

  ArrayList<String> environmentList;
  Path executableBaseDirectory;
  String projectName;
  String workspaceName;
  String executableName;
  String command;
  JsonArray argumentList;
  JsonObject environmentMap;
  long timeout;
  private long pid;
  ExecKey execKey;
  FlagWatchdog flagWatchdog;

  public TaskExecutor(Path baseDirectory, Path taskJsonPath) throws Exception {
    super(baseDirectory, taskJsonPath);

    this.environmentList = new ArrayList<>();

    TaskJson taskJson = new TaskJson(Json.parse(new FileReader(taskJsonPath.toFile())).asObject());
    try {
      executableBaseDirectory = baseDirectory.resolve(taskJson.getExecutable()).resolve(Constants.BASE).normalize();
    } catch (Exception e) {
      executableBaseDirectory = null;
    }
    projectName = taskJson.getProject();
    workspaceName = taskJson.getWorkspace();
    executableName = taskJson.getExecutableName();
    command = taskJson.getCommand();
    argumentList = taskJson.getArguments();
    environmentMap = taskJson.getEnvironments();
    timeout = taskJson.getTimeout();
    execKey = taskJson.getExecKey();

    pid = ProcessHandle.current().pid();//Long.valueOf(java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
  }

  private void setPid(long pid) {
    this.pid = pid;
  }

  public long getPid() {
    return pid;
  }

  public void shutdown() {
    try {
      for (int sec = 0; sec < Constants.SHUTDOWN_TIMEOUT; sec += 1) {
        if (Runtime.getRuntime().exec("kill -0 " + getPid()).waitFor() == 1) {
          return;
        }
        TimeUnit.SECONDS.sleep(1);
      }
      recursiveKill(String.valueOf(getPid()));
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  private void close() {
    if (flagWatchdog != null) {
      flagWatchdog.close();
    }

    try {
      Files.writeString(baseDirectory.resolve(Constants.NOTIFIER), UUID.randomUUID().toString());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void recursiveKill(String childPid) {
    ProcessBuilder processBuilder
      = new ProcessBuilder("ps", "--ppid", childPid, "-o", "pid=");
    try {
      Process process = processBuilder.start();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        process.waitFor();
        reader.lines().forEach((child) -> recursiveKill(child));
      }
      Runtime.getRuntime().exec("kill -9 " + childPid).waitFor();
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  public void execute() {
    try {
      if (!execKey.authorize(getTaskDirectory())) {
        return;
      }

      flagWatchdog = new FlagWatchdog();
      flagWatchdog.start();

      DirectoryHash directoryHash = new DirectoryHash(baseDirectory, taskDirectory);
      directoryHash.waitToMatch(Constants.DIRECTORY_SYNCHRONIZATION_TIMEOUT);
      if (executableBaseDirectory != null) {
        new DirectoryHash(baseDirectory, executableBaseDirectory.getParent()).waitToMatch(Constants.DIRECTORY_SYNCHRONIZATION_TIMEOUT);
      }

      Path executingBaseDirectory = taskDirectory.resolve(Constants.BASE).normalize();
      Path stdoutPath = taskDirectory.resolve(Constants.STDOUT_FILE);
      Path stderrPath = taskDirectory.resolve(Constants.STDERR_FILE);
      Path eventFilePath = taskDirectory.resolve(Constants.EVENT_FILE);
      Path statusFilePath = taskDirectory.resolve(Constants.EXIT_STATUS_FILE);

      if (System.getenv().containsKey(Constants.WAFFLE_SLOT_INDEX)) {
        addEnvironment(Constants.WAFFLE_SLOT_INDEX, System.getenv().get(Constants.WAFFLE_SLOT_INDEX));
      }

      addEnvironment("PATH", Main.getBinDirectory(baseDirectory).toString() + File.pathSeparator + System.getenv().get("PATH"));
      addEnvironment(Constants.WAFFLE_BASE, executingBaseDirectory.toString());
      addEnvironment(Constants.WAFFLE_TASK_JSONFILE, taskJsonPath.toString());
      addEnvironment(Constants.WAFFLE_BATCH_WORKING_DIR, taskDirectory.toString());
      addEnvironment("WAFFLE_WORKING_DIR", executingBaseDirectory.toString());

      try {
        Files.createFile(stdoutPath);
        Files.createFile(stderrPath);
        Files.createFile(eventFilePath);
      } catch (IOException e) {
        // NOP
      }

      // write a status file
      try {
        FileWriter writer = new FileWriter(statusFilePath.toFile(), StandardCharsets.UTF_8, false);
        writer.write("-2");
        writer.flush();
        writer.close();
      } catch (IOException e) {
        e.printStackTrace();
      }

      Files.createDirectories(executingBaseDirectory);

      if (executableBaseDirectory != null) {
        // try to change permission of the command
        Runtime.getRuntime().exec(new String[]{"sh", "-c", "chmod a+x '" + command + "' >/dev/null 2>&1"},
          getEnvironments(), executableBaseDirectory.toFile()).waitFor();

        // prepare local shared directory path
        Path projectLocalSharedDirectory = baseDirectory.resolve(Constants.PROJECT).resolve(projectName)
          .resolve(Constants.LOCAL_SHARED).resolve(executableName).normalize();
        Path workspaceLocalSharedDirectory = baseDirectory.resolve(Constants.PROJECT).resolve(projectName)
          .resolve(Constants.WORKSPACE).resolve(workspaceName)
          .resolve(Constants.LOCAL_SHARED).resolve(executableName).normalize();

        // create link of executable entities
        createRecursiveLink(executableBaseDirectory, executingBaseDirectory,
          projectLocalSharedDirectory, workspaceLocalSharedDirectory);
      }

      // load custom environments
      for (JsonObject.Member member : environmentMap) {
        addEnvironment(member.getName(), member.getValue().asString());
      }

      int exitValue = 0;

      if (command.equals(PodTask.PODTASK)) {
        try {
          PodTask podTask = new PodTask(argumentList);
          podTask.run();
        } catch (Exception e) {
          exitValue = 1;
        }
      } else {
        // pre-process for local shared
        /*
        addEnvironment("WAFFLE_LOCAL_SHARED", localSharedDirectory.toString());
        Files.createDirectories(localSharedDirectory);
        for (JsonObject.Member member : localSharedMap) {
          String key = member.getName();
          String remote = member.getValue().asString();
          Runtime.getRuntime().exec(new String[]{"sh", "-c", "mkdir -p `dirname \"" + remote + "\"`;if [ -e \"${WAFFLE_LOCAL_SHARED}/" + key + "\" ]; then ln -fs \"${WAFFLE_LOCAL_SHARED}/" + key + "\" \"" + remote + "\"; else echo \"" + key + "\" >> \"${WAFFLE_BATCH_WORKING_DIR}/non_prepared_local_shared.txt\"; fi"},
            getEnvironments(), executingBaseDirectory.toFile()).waitFor();
        }
         */

        // BEGIN of main command executing
        ArrayList<String> commandArray = new ArrayList<>();
        commandArray.add(command);
        for (JsonValue value : argumentList) {
          commandArray.add(value.isString() ? value.asString() : value.toString());
        }

        Process process = Runtime.getRuntime().exec(commandArray.toArray(new String[commandArray.size()]),
          getEnvironments(), executingBaseDirectory.toFile());
        setPid(process.pid());

        OutputProcessor outProcessor =
          new OutputProcessor(process.getInputStream(), stdoutPath, new EventRecorder(baseDirectory, eventFilePath));
        OutputProcessor errProcessor =
          new OutputProcessor(process.getErrorStream(), stderrPath, new EventRecorder(baseDirectory, eventFilePath));
        outProcessor.start();
        errProcessor.start();

        if (timeout < 0) {
          process.waitFor();
        } else {
          process.waitFor(timeout, TimeUnit.SECONDS);
        }

        outProcessor.join();
        errProcessor.join();
        // END of main command executing

        // post-process of local shared
        /*
        for (JsonObject.Member member : localSharedMap) {
          String key = member.getName();
          String remote = member.getValue().asString();
          Runtime.getRuntime().exec(
            new String[]{"sh", "-c", "if grep \"^" + key + "$\" \"${WAFFLE_BATCH_WORKING_DIR}/non_prepared_local_shared.txt\"; then mv \"" + remote + "\" \"${WAFFLE_LOCAL_SHARED}/" + key + "\"; ln -fs \"${WAFFLE_LOCAL_SHARED}/"  + key + "\" \"" + remote + "\" ;fi"},
            getEnvironments(), executingBaseDirectory.toFile()).waitFor();
        }
         */

        exitValue = process.exitValue();
      }

      // write a status file
      try {
        FileWriter writer = new FileWriter(statusFilePath.toFile(), StandardCharsets.UTF_8, false);
        writer.write(String.valueOf(exitValue));
        writer.flush();
        writer.close();
      } catch (IOException e) {
        e.printStackTrace();
      }

      // update hash file
      new EventDirReader(baseDirectory, taskDirectory).recordRemains();
      directoryHash.update();
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    } finally {
      close();
    }
  }

  private void createRecursiveLink(Path source, Path destination, Path projectLocalShared, Path workspaceLocalShared) throws IOException {
    if (Files.isDirectory(source)) {
      Files.createDirectories(destination);
    }
    try (Stream<Path> stream = Files.list(source)) {
      stream.forEach(path -> {
        try {
          Path localShared = projectLocalShared;

          switch (LocalSharedFlag.getFlag(path).getLevel()) {
            case None:
              if (Files.isDirectory(path)) {
                createRecursiveLink(path, destination.resolve(path.getFileName()),
                  projectLocalShared.resolve(path.getFileName()), workspaceLocalShared.resolve(path.getFileName()));
              } else {
                Files.createSymbolicLink(destination.resolve(path.getFileName()), path);
              }
              break;
            case Run:
              if (Files.isDirectory(path)) {
                recursiveMerge(path, destination.resolve(path.getFileName()));
              } else {
                Files.copy(path, destination.resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
              }
              break;
            case Workspace: // replace localShared from project's.
              localShared = workspaceLocalShared;
            case Project: // localShared is projectLocalShared when Project case
              Path sharedPath = localShared.resolve(path.getFileName());
              if (Files.isDirectory(path)) {
                recursiveMerge(path, sharedPath);
              } else {
                merge(path, sharedPath);
              }
              Files.createSymbolicLink(destination.resolve(path.getFileName()), sharedPath);
              break;
          }
        } catch (IOException e) {
          e.printStackTrace();
          try {
            Files.writeString(Paths.get("/tmp/err"), e.toString());
          } catch (IOException ex) {
            ex.printStackTrace();
          }
        }
      });
    }
  }

  private void recursiveMerge(Path source, Path destination) throws IOException {
    if (Files.isDirectory(source)) {
      Files.createDirectories(destination);
    }
    try (Stream<Path> stream = Files.list(source)) {
      stream.forEach(path -> {
        try {
          if (Files.isDirectory(path)) {
            recursiveMerge(path, destination.resolve(path.getFileName()));
          } else {
            merge(path, destination.resolve(path.getFileName()));
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      });
    }
  }

  private void merge(Path source, Path destination) {
    if (!Files.exists(destination)) {
      try {
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public void addEnvironment(String name, String value) {
    try {
      //Process process = Runtime.getRuntime().exec(new String[]{"echo", "-n", value},
        //getEnvironments(), executableBaseDirectory.toFile());
      Path workingDirectory = Paths.get(".");
      if (executableBaseDirectory != null) {
        workingDirectory = executableBaseDirectory;
      }
      Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "echo -n \"" + value + "\""},
        getEnvironments(), workingDirectory.toFile());
      InputStream inputStream = process.getInputStream();
      process.waitFor();
      value = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    environmentList.add(name + '=' + value);
  }

  private String[] getEnvironments() {
    return environmentList.toArray(new String[environmentList.size()]);
  }

  private class FlagWatchdog extends Thread {
    Path flagPath;
    boolean interrupted;

    public FlagWatchdog() {
      this.interrupted = false;
      this.flagPath = getTaskDirectory().resolve(Constants.ALIVE);

      try {
        Files.createFile(flagPath);
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        flagPath.toFile().deleteOnExit();
      }
    }

    @Override
    public void run() {
      while (Files.exists(flagPath) && !interrupted) {
        try {
          TimeUnit.SECONDS.sleep(Constants.WATCHDOG_INTERVAL);
        } catch (InterruptedException e) {
          continue;
        }
      }

      if (!interrupted) {
        System.err.println("The task will be killed because missing " + flagPath);
        recursiveKill(String.valueOf(getPid()));
        System.exit(1);
      }
      return;
    }

    public void close() {
      interrupted = true;
      interrupt();
    }
  }
}
