package jp.tkms.waffle.sub.servant.processor;

import jp.tkms.waffle.sub.servant.Constants;
import jp.tkms.waffle.sub.servant.DirectoryHash;
import jp.tkms.waffle.sub.servant.Envelope;
import jp.tkms.waffle.sub.servant.message.request.SubmitPodTaskMessage;
import jp.tkms.waffle.sub.servant.message.response.*;
import jp.tkms.waffle.sub.servant.pod.AbstractExecutor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

public class SubmitPodTaskRequestProcessor extends RequestProcessor<SubmitPodTaskMessage> {
  private final static String POD_RUN_FLAG = ".POD_RUN" + DirectoryHash.IGNORE_FLAG;

  protected SubmitPodTaskRequestProcessor() {
  }

  @Override
  protected void processIfMessagesExist(Path baseDirectory, Envelope request, Envelope response, ArrayList<SubmitPodTaskMessage> messageList) throws ClassNotFoundException, IOException {
    ArrayList<Path> removingList = new ArrayList<>();

    messageList.stream().parallel().forEach(message -> {
      Path podPath = baseDirectory.resolve(message.getPodDirectory());
      Path workingDirectory = baseDirectory.resolve(message.getWorkingDirectory()).toAbsolutePath().normalize();

      if (!Files.exists(workingDirectory)) {
        response.add(new RequestRepreparingMessage(message));
        return;
      }

      if (Files.exists(workingDirectory.resolve(POD_RUN_FLAG))) { // If already executed, remove and request resubmit
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

      if (!Files.exists(podPath.resolve(AbstractExecutor.UPDATE_FILE_PATH)) || Files.exists(podPath.resolve(AbstractExecutor.LOCKOUT_FILE_PATH))) {
        Path lostFlagPath = workingDirectory.resolve(CheckJobIdRequestProcessor.LOST_JOBID_FLAG);
        try {
          Files.deleteIfExists(lostFlagPath);
        } catch (IOException e) {
          //NOP
        }
        response.add(new PodTaskRefusedMessage(message.getJobId()));
        return;
      }

      if (message.getExecutableDirectory() != null) {
        DirectoryHash executableDirectoryHash = new DirectoryHash(baseDirectory, message.getExecutableDirectory(), false);
        if (!executableDirectoryHash.hasHashFile()) {
          executableDirectoryHash.save();
        } else {
          if (executableDirectoryHash.update()) {
            response.add(new ExceptionMessage("EXECUTABLE FILES HAS CHANGED: " + message.getExecutableDirectory()));
          }
        }
      }

      try {
        Files.createFile(workingDirectory.resolve(POD_RUN_FLAG));
      } catch (IOException e) {
        e.printStackTrace();
      }

      new DirectoryHash(baseDirectory, workingDirectory).save();
      try {
        Files.setPosixFilePermissions(workingDirectory, PosixFilePermissions.fromString("rwxrwx---"));
      } catch (IOException e) {
        //NOP
      }

      try {
        Path jobsDirectory = podPath.resolve(AbstractExecutor.JOBS_PATH);
        Path entitiesDirectory = podPath.resolve(AbstractExecutor.ENTITIES_PATH);

        Files.createDirectories(jobsDirectory);
        Files.createDirectories(entitiesDirectory);
        try {
          Files.setPosixFilePermissions(jobsDirectory, PosixFilePermissions.fromString("rwxrwx---"));
          Files.setPosixFilePermissions(entitiesDirectory, PosixFilePermissions.fromString("rwxrwx---"));
        } catch (IOException e) {
          //NOP
        }
        //Runtime.getRuntime().exec("chmod 777 '" + jobsDirectory.toString() + "'").waitFor();
        //Runtime.getRuntime().exec("chmod 777 '" + entitiesDirectory.toString() + "'").waitFor();

        if (!Files.exists(podPath.resolve(AbstractExecutor.HASH_IGNORE_FILE_PATH))) {
          Files.createFile(podPath.resolve(AbstractExecutor.HASH_IGNORE_FILE_PATH));
        }

        Files.writeString(entitiesDirectory.resolve(message.getId()), workingDirectory.toString());
        Files.createFile(jobsDirectory.resolve(message.getId()));
        try {
          Files.setPosixFilePermissions(entitiesDirectory.resolve(message.getId()), PosixFilePermissions.fromString("rw-rw----"));
          Files.setPosixFilePermissions(jobsDirectory.resolve(message.getId()), PosixFilePermissions.fromString("rw-rw----"));
        } catch (IOException e) {
          //NOP
        }
        //System.err.println(jsonObject.toString());
        Files.writeString(workingDirectory.resolve(Constants.JOBID_FILE), message.getJobId());
        response.add(new UpdateJobIdMessage(message, message.getJobId(), workingDirectory));
      } catch (Exception e) {
        e.printStackTrace();
        response.add(new JobExceptionMessage(message, e.getMessage()));
      }
    });

    if (!removingList.isEmpty()) {
      try {
        TimeUnit.SECONDS.sleep(Constants.WATCHDOG_INTERVAL);
      } catch (InterruptedException e) {
        //NOP
      }
      removingList.stream().parallel().forEach(path -> {
        for (int count = 0; count < SubmitJobRequestProcessor.MAX_REMOVING_RETRYING; count += 1) {
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
}
