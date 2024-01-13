package jp.tkms.waffle.sub.servant.processor;

import jp.tkms.waffle.sub.servant.*;
import jp.tkms.waffle.sub.servant.message.request.CollectPodTaskStatusMessage;
import jp.tkms.waffle.sub.servant.message.response.*;
import jp.tkms.waffle.sub.servant.pod.AbstractExecutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class CollectPodTaskStatusRequestProcessor extends RequestProcessor<CollectPodTaskStatusMessage> {
  protected CollectPodTaskStatusRequestProcessor() {
  }

  @Override
  protected void processIfMessagesExist(Path baseDirectory, Envelope request, Envelope response, ArrayList<CollectPodTaskStatusMessage> messageList) throws ClassNotFoundException, IOException {
    messageList.stream().parallel().forEach(message -> {
      try {
        GetValueCommand.process(message.getWorkingDirectory(), (m) -> response.add(m) );
        EventDirReader eventDirReader = new EventDirReader(baseDirectory, message.getWorkingDirectory());

        Path jobsDirectory = baseDirectory.resolve(message.getPodDirectory()).resolve(AbstractExecutor.JOBS_PATH);
        if (message.isForceFinish() || !Files.exists(jobsDirectory.resolve(message.getId()))) {
          int exitStatus = -2;
          Path exitStatusFile = baseDirectory.resolve(message.getWorkingDirectory()).resolve(Constants.EXIT_STATUS_FILE);
          try {
            exitStatus = Integer.parseInt(new String(Files.readAllBytes(exitStatusFile)));
          } catch (Exception | Error e) {
            exitStatus = -1;
          }
          if ((exitStatus >= 0 && new DirectoryHash(baseDirectory, message.getWorkingDirectory()).isMatchToHashFile() && eventDirReader.isFoundAllRemains()) || exitStatus < 0 || CollectStatusRequestProcessor.isSynchronizationTimeout(exitStatusFile)) {
            //(new DirectoryHash(baseDirectory, message.getWorkingDirectory())).waitToMatch(Constants.DIRECTORY_SYNCHRONIZATION_TIMEOUT);
            response.add(new PodTaskFinishedMessage(message));
            response.add(new UpdateStatusMessage(message, exitStatus));
            response.add(message.getWorkingDirectory().resolve(Constants.STDOUT_FILE));
            response.add(message.getWorkingDirectory().resolve(Constants.STDERR_FILE));
          } else {
            //response.add(new ExceptionMessage(message.getWorkingDirectory().toString()));
            response.add(new UpdateStatusMessage(message));
          }
        } else {
          response.add(new UpdateStatusMessage(message));
        }

        new EventReader(baseDirectory, message.getWorkingDirectory()).process((name, value) -> {
          response.add(new UpdateResultMessage(message, name, value));
        });
        eventDirReader.process((name, value) -> {
          response.add(new UpdateResultMessage(message, name, value));
        });

        PushFileCommand.process(message.getWorkingDirectory(), (m) -> response.add(m) );
      } catch (Exception e) {
        response.add(new JobExceptionMessage(message, e.getMessage()));
        response.add(message.getWorkingDirectory().resolve(Constants.STDOUT_FILE));
        response.add(message.getWorkingDirectory().resolve(Constants.STDERR_FILE));
      }
    });
  }
}
