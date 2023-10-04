package jp.tkms.waffle.sub.servant.pod;

import jp.tkms.waffle.sub.servant.Constants;
import jp.tkms.waffle.sub.servant.DirectoryHash;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public abstract class AbstractExecutor {
  protected static final String BATCH_FILE = "./batch.sh";
  public static final Path BASE_PATH = Paths.get("BASE");
  public static final Path HASH_IGNORE_FILE_PATH = BASE_PATH.resolve(DirectoryHash.IGNORE_FLAG);
  public static final Path JOBS_PATH = BASE_PATH.resolve("JOBS");
  public static final Path ENTITIES_PATH = BASE_PATH.resolve("ENTITIES");
  public static final Path ALIVE_NOTIFIER_PATH = BASE_PATH.resolve("ALIVE_NOTIFIER");
  public static final Path UPDATE_FILE_PATH = ALIVE_NOTIFIER_PATH.resolve("UPDATE");
  public static final Path PREVIOUS_FILE_PATH = ALIVE_NOTIFIER_PATH.resolve("PREVIOUS");
  public static final Path PREVIOUS_CHECK_FILE_PATH = ALIVE_NOTIFIER_PATH.resolve("PREVIOUS_CHECK");
  public static final Path LOCKOUT_FILE_PATH = ALIVE_NOTIFIER_PATH.resolve("LOCKOUT");

  //private WatchService fileWatchService = null;
  private Thread shutdownTimer;
  private boolean isAlive;
  private HashSet<String> runningJobList = new HashSet<>();
  private ArrayList<String> slotArray = new ArrayList<>();
  private Object objectLocker = new Object();
  private int timeout = 0;
  private int waitingTimeCounter = 0;
  private int marginTime = 0;

  public static boolean isAlive(Path directory) {
    Path updateFile = directory.resolve(UPDATE_FILE_PATH);
    Path previousFile = directory.resolve(PREVIOUS_FILE_PATH);
    Path previousCheckTimeFile = directory.resolve(PREVIOUS_CHECK_FILE_PATH);

    try {
      if (!Files.exists(previousCheckTimeFile)) {
        Files.createDirectories(previousCheckTimeFile.getParent());
        Files.writeString(previousCheckTimeFile, String.valueOf(System.currentTimeMillis()));
        return true;
      } else if (System.currentTimeMillis() -2000 <= Long.valueOf(Files.readString(previousCheckTimeFile))) {
        return true;
      } else if (!Files.exists(updateFile)) {
        return false;
      } else if (Files.exists(previousFile)) {
        if (Files.readString(updateFile).equals(Files.readString(previousFile))) {
          return false;
        } else {
          Files.writeString(previousFile, Files.readString(updateFile));
          return true;
        }
      } else {
        Files.createDirectories(previousFile.getParent());
        Files.writeString(previousFile, Files.readString(updateFile));
      }
    } catch (IOException e) {
      return false;
    }
    return false;
  }

  public static void removeAllJob(Path directory) {
    Path jobsDirectory = directory.resolve(AbstractExecutor.JOBS_PATH);
    if (Files.exists(jobsDirectory)) {
      try (Stream<Path> stream = Files.list(jobsDirectory)) {
        stream.forEach(path -> {
          try {
            Files.delete(path);
          } catch (IOException e) {
            e.printStackTrace();
          }
        });
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public AbstractExecutor(int timeout, int marginTime) throws IOException {
    isAlive = true;
    this.timeout = timeout;
    this.marginTime = marginTime;
    Files.createDirectories(JOBS_PATH);
    Files.createDirectories(ENTITIES_PATH);
    //Files.setPosixFilePermissions(JOBS_PATH, PosixFilePermissions.fromString("rwxrwxrwx"));
    //Files.setPosixFilePermissions(ENTITIES_PATH, PosixFilePermissions.fromString("rwxrwxrwx"));

    try {
      if (!Files.exists(HASH_IGNORE_FILE_PATH)) {
        Files.createFile(HASH_IGNORE_FILE_PATH);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    //fileWatchService = FileSystems.getDefault().newWatchService();
    //JOBS_PATH.register(fileWatchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
  }

  public void startPolling(Thread shutdownTimer) {
    this.shutdownTimer = shutdownTimer;
    /*
    Thread waitTimer = getWaitTimer();
    try {
      WatchKey watchKey = null;
      while ((watchKey = fileWatchService.take()) != null) {
        if (waitTimer != null) {
          waitTimer.interrupt();
          waitTimer = null;
        }

        for (WatchEvent<?> event : watchKey.pollEvents()) {
          System.err.println(event.kind().name() + " : " + event.context().toString());
        }

        checkJobs();
        watchKey.reset();

        if (runningJobList.isEmpty()) {
          waitTimer = getWaitTimer();
        }
      }
    } catch (InterruptedException | ClosedWatchServiceException e) {
      if (waitTimer != null) {
        waitTimer.interrupt();
      }
      return;
    }
     */
    System.err.println("Executor started at " + System.currentTimeMillis());

    try {
      Files.createDirectories(ALIVE_NOTIFIER_PATH);
    } catch (IOException e) {
      e.printStackTrace();
    }

    while (isAlive && waitingTimeCounter <= timeout) {
      boolean isChanged = false;

      try {
        Files.writeString(UPDATE_FILE_PATH, String.valueOf(System.currentTimeMillis()));
      } catch (IOException e) {
        e.printStackTrace();
      }

      synchronized (objectLocker) {
        for (File file : JOBS_PATH.toFile().listFiles()) {
          if (!runningJobList.contains(file.getName())) {
            isChanged = true;
          }
        }
        for (String id : runningJobList) {
          if (!JOBS_PATH.resolve(id).toFile().exists()) {
            isChanged = true;
          }
        }
      }

      if (isChanged) {
        checkJobs();
      }

      synchronized (objectLocker) {
        if (runningJobList.isEmpty()) {
          waitingTimeCounter += 1;
        } else {
          waitingTimeCounter = 0;
        }
      }

      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        isAlive = false;
      }
    }

    if (isAlive) {
      shutdown();
    }
  }

  /*
  private Thread getWaitTimer() {
    Thread waitTimer = new Thread() {
      @Override
      public void run() {
        try {
          sleep(timeout * 1000);
          shutdown();
        } catch (InterruptedException e) {
        }
      }
    };
    waitTimer.start();
    return waitTimer;
  }
   */

  public void shutdown() {
    isAlive = false;
    System.err.println("Executor will shutdown (" + System.currentTimeMillis() + ")");

    try {
      Files.createFile(LOCKOUT_FILE_PATH);
    } catch (IOException e) {
      e.printStackTrace();
    }

    /*
    synchronized (objectLocker) {
      try {
        fileWatchService.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
     */

    checkJobs();

    for (int countDown = marginTime; countDown >= 0 && !runningJobList.isEmpty(); countDown--) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        //NP
      }
      checkJobs();
    }

    HashSet<String> temporaryJobList = new HashSet<>(runningJobList);
    for (String jobName : temporaryJobList) {
      if (Files.exists(JOBS_PATH.resolve(jobName))) {
        synchronized (objectLocker) {
          jobRemoved(jobName);
          runningJobList.remove(jobName);
        }
      }
    }

    if (shutdownTimer != null && shutdownTimer.isAlive()) {
      shutdownTimer.interrupt();
    }

    System.err.println("Executor shutdown at " + System.currentTimeMillis());
  }

  protected void checkJobs() {
    synchronized (objectLocker) {
      HashSet<String> temporaryJobList = new HashSet<>(runningJobList);
      for (String jobName : temporaryJobList) {
        if (!Files.exists(JOBS_PATH.resolve(jobName))) {
          jobRemoved(jobName);
          runningJobList.remove(jobName);
        }
      }
      for (File file : JOBS_PATH.toFile().listFiles()) {
        if (!runningJobList.contains(file.getName())) {
          if (Files.exists(ENTITIES_PATH.resolve(file.getName()))) {
            jobAdded(file.getName());
            runningJobList.add(file.getName());
          }
        }
      }
    }
  }

  private int getNextSlotIndex() {
    synchronized (objectLocker) {
      int index = slotArray.indexOf(null);
      if (index == -1) {
        slotArray.add(null);
      }
      return slotArray.lastIndexOf(null);
    }
  }

  private int getSlotIndex(String jobName) {
    synchronized (objectLocker) {
      return slotArray.indexOf(jobName);
    }
  }

  protected void jobAdded(String jobName) {
    System.err.println("'" + jobName + "' was added at " + System.currentTimeMillis());
    slotArray.set(getNextSlotIndex(), jobName);
    ENTITIES_PATH.resolve(jobName).toFile().deleteOnExit();
    JOBS_PATH.resolve(jobName).toFile().deleteOnExit();
  }

  protected void jobRemoved(String jobName) {
    System.err.println("'" + jobName + "' was forcibly removed at " + System.currentTimeMillis());
    synchronized (objectLocker) {
      slotArray.set(getSlotIndex(jobName), null);
      try {
        Files.deleteIfExists(ENTITIES_PATH.resolve(jobName));
        Files.deleteIfExists(JOBS_PATH.resolve(jobName));
      } catch (IOException e) {
      }
    }
  }

  protected void jobFinished(String jobName) {
    synchronized (objectLocker) {
      slotArray.set(getSlotIndex(jobName), null);
      runningJobList.remove(jobName);
      try {
        Files.deleteIfExists(ENTITIES_PATH.resolve(jobName));
        Files.deleteIfExists(JOBS_PATH.resolve(jobName));
      } catch (IOException e) {
      }
    }
  }

  protected Thread startJobThread(String jobName) {
    Thread thread = new Thread() {
      boolean isCanceled = false;
      Process process = null;

      @Override
      public void run() {
        if (isCanceled) {
          return;
        }

        try {
          Path entityPath = ENTITIES_PATH.resolve(jobName);
          while (!Files.exists(entityPath)) {
            System.err.println(entityPath.toString() + " is not exist; wait a second and retry.");
            TimeUnit.SECONDS.sleep(1);
          }
          Path directoryPath = Paths.get(Files.readString(entityPath).trim());
          while (!Files.isWritable(directoryPath)) {
            System.err.println(directoryPath.toString() + " is not writable; wait a second and retry.");
            TimeUnit.SECONDS.sleep(1);
          }

          int slotIndex = getSlotIndex(jobName);

          process = new ProcessBuilder().directory(directoryPath.toFile())
            .command("sh", "-c", Constants.WAFFLE_SLOT_INDEX + "=" + slotIndex + " sh " + BATCH_FILE).inheritIO().start();

          process.waitFor();
        } catch (Exception e) {
          System.err.println("'" + jobName + "' was failed to execute at " + System.currentTimeMillis());
          e.printStackTrace();
        }

        jobFinished(jobName);

        System.err.println("'" + jobName + "' was finished at " + System.currentTimeMillis());
      }

      @Override
      public void interrupt() {
        isCanceled = true;
        if (process != null) {
          process.destroyForcibly();
          System.err.println("'" + jobName + "' was canceled at " + System.currentTimeMillis());
        }
      }
    };

    thread.start();


    return thread;
  }
}
