package jp.tkms.waffle.inspector;

import jp.tkms.waffle.Main;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.internal.task.ExecutableRunTask;
import jp.tkms.waffle.data.internal.task.SystemTask;
import jp.tkms.waffle.exception.FailedToControlRemoteException;
import jp.tkms.waffle.data.log.message.InfoLogMessage;
import jp.tkms.waffle.data.log.message.WarnLogMessage;
import jp.tkms.waffle.communicator.AbstractSubmitter;

import java.util.concurrent.TimeUnit;

public class Inspector extends Thread {
  protected static long waitingStep = 200;

  public enum Mode {System, Normal}

  protected Mode mode;
  protected Computer computer;
  protected long waitCount;

  Inspector(Mode mode, Computer computer) {
    super("Waffle_Polling(" + getThreadName(mode, computer) + ")");
    this.mode = mode;
    this.computer = computer;
  }

  long toMilliSecond(long second) {
    return second * 1000;
  }

  @Override
  public void run() {
    InfoLogMessage.issue(computer, "started a " + mode.name() + " submitter");

    AbstractSubmitter submitter = AbstractSubmitter.getInstance(mode, computer).connect();

    waitCount = toMilliSecond(submitter.getPollingInterval() - 1);
    do {
      while (waitCount < toMilliSecond(submitter.getPollingInterval())) {
        try {
          TimeUnit.MILLISECONDS.sleep(waitingStep);
        } catch (InterruptedException e) {
          break;
        }
        waitCount += waitingStep;
        if (Main.hibernatingFlag) {
          break;
        }
      }
      if (Main.hibernatingFlag) {
        break;
      }
      waitCount = 0;
      if (!Main.hibernatingFlag) {
        /*
        if (submitter == null || !submitter.isConnected()) {
          InfoLogMessage.issue(computer, "will be reconnected");
          if (submitter != null) {
            submitter.close();
          }
          try {
            submitter = AbstractSubmitter.getInstance(mode, computer).connect();
          } catch (Exception e) {
            WarnLogMessage.issue(e.getMessage());
            submitter.close();
            InspectorMaster.removeInspector(getThreadName(mode, computer));
            InfoLogMessage.issue(computer, mode.name() + ") submitter closed");
            return;
          }
          if (!submitter.isConnected()) {
            WarnLogMessage.issue("Failed to connect to " + computer.getName());
            continue;
          }
        }
         */
        try {
          submitter.checkSubmitted();
        } catch (FailedToControlRemoteException e) {
          WarnLogMessage.issue(computer, "scanned jobs with error");
          break;
        }
        InfoLogMessage.issue(computer, "scanned jobs");
      }

      if (submitter.isClosed()) {
        break;
      }
    } while ((mode.equals(Mode.Normal) ? ExecutableRunTask.getList(computer).size() : SystemTask.getList(computer).size()) > 0);

    if (submitter != null) {
      submitter.close();
    }
    InspectorMaster.removeInspector(getThreadName(mode, computer));

    InfoLogMessage.issue(computer, "closed a " + mode.name() + " submitter");
    return;
  }

  static String getThreadName(Mode mode, Computer computer) {
    return mode.name() + ":" + computer.getName();
  }
}
