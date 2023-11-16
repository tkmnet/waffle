package jp.tkms.waffle.communicator;

import com.eclipsesource.json.JsonValue;
import jp.tkms.waffle.communicator.annotation.CommunicatorDescription;
import jp.tkms.waffle.data.util.WrappedJsonArray;
import jp.tkms.waffle.inspector.Inspector;
import jp.tkms.waffle.data.ComputerTask;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.internal.task.AbstractTask;
import jp.tkms.waffle.exception.FailedToControlRemoteException;
import jp.tkms.waffle.exception.RunNotFoundException;
import jp.tkms.waffle.data.log.message.WarnLogMessage;
import jp.tkms.waffle.data.util.ComputerState;
import jp.tkms.waffle.inspector.InspectorMaster;
import jp.tkms.waffle.sub.servant.Envelope;

import java.util.*;

@CommunicatorDescription("Load Balancer (round robin)")
public class LoadBalancingSubmitter extends MultiComputerSubmitter {

  public LoadBalancingSubmitter(Computer computer) {
    super(computer);
  }

  /*
  public double getMaximumNumberOfThreads(Computer computer) {
    double num = 0.0;

    WrappedJsonArray targetComputers = computer.getParameters().getWrappedJsonArray(KEY_TARGET_COMPUTERS);
    if (targetComputers != null) {
      for (Object object : targetComputers.toList()) {
        Computer targetComputer = Computer.getInstance(object.toString());
        if (targetComputer != null && targetComputer.getState().equals(ComputerState.Viable)) {
          num += targetComputer.getMaximumNumberOfThreads();
        }
      }
    }

    return (num < computer.getMaximumNumberOfThreads() ? num : computer.getMaximumNumberOfThreads());
  }

  public double getAllocableMemorySize(Computer computer) {
    double size = 0.0;

    WrappedJsonArray targetComputers = computer.getParameters().getWrappedJsonArray(KEY_TARGET_COMPUTERS);
    if (targetComputers != null) {
      for (Object object : targetComputers.toList()) {
        Computer targetComputer = Computer.getInstance(object.toString());
        if (targetComputer != null && targetComputer.getState().equals(ComputerState.Viable)) {
          size += targetComputer.getAllocableMemorySize();
        }
      }
    }

    return (size < computer.getAllocableMemorySize() ? size : computer.getAllocableMemorySize());
  }
   */

  @Override
  public boolean processPreparing(Envelope envelope, ArrayList<AbstractTask> createdJobList, ArrayList<AbstractTask> preparedJobList) throws FailedToControlRemoteException {
    double globalFreeThread = 0.0;
    {
      JsonValue jsonValue = (JsonValue) computer.getParameter(KEY_MAX_THREADS, this);
      if (jsonValue.isNumber()) {
        globalFreeThread = jsonValue.asDouble();
      }
    }

    double globalFreeMemory = 0.0;
    {
      JsonValue jsonValue = (JsonValue) computer.getParameter(KEY_ALLOCABLE_MEMORY, this);
      if (jsonValue.isNumber()) {
        globalFreeMemory = jsonValue.asDouble();
      }
    }

    int globalFreeJobSlot = 0;
    JsonValue jsonValue = (JsonValue) computer.getParameter(KEY_MAX_JOBS, this);
    if (jsonValue.isNumber()) {
      globalFreeJobSlot = jsonValue.asInt();
    }

    ArrayList<Computer> passableComputerList = new ArrayList<>();
    WrappedJsonArray targetComputers = computer.getParameters().getArray(KEY_TARGET_COMPUTERS, null);
    if (targetComputers != null) {
      for (Object object : targetComputers) {
        Computer targetComputer = Computer.getInstance(object.toString());
        if (targetComputer != null && targetComputer.getState().equals(ComputerState.Viable)) {
          passableComputerList.add(targetComputer);

          for (AbstractTask job : getJobList(Inspector.Mode.Normal, targetComputer)) {
            try {
              ComputerTask run = job.getRun();
              if (run.getComputer().equals(computer)) {
                globalFreeThread -= run.getRequiredThread();
                globalFreeMemory -= run.getRequiredMemory();
                globalFreeJobSlot -= 1;
              }
            } catch (RunNotFoundException e) {
            }
          }
        }
      }
    }

    int targetHostCursor = 0;
    if (globalFreeThread > 0.0 && globalFreeMemory > 0.0 && globalFreeJobSlot > 0 && passableComputerList.size() > 0) {
      if (passableComputerList.size() > 0) {
        for (AbstractTask job : createdJobList) {
          Computer targetComputer = passableComputerList.get(targetHostCursor);

          ComputerTask run = null;
          try {
            run = job.getRun();
          } catch (RunNotFoundException e) {
            continue;
          }

          if (!isSubmittable(targetComputer, run)) {
            int startOfTargetHostCursor = targetHostCursor;
            targetHostCursor += 1;
            if (targetHostCursor >= passableComputerList.size()) {
              targetHostCursor = 0;
            }
            do {
              targetComputer = passableComputerList.get(targetHostCursor);
            } while (targetHostCursor != startOfTargetHostCursor && !isSubmittable(targetComputer, run));
          }

          if (isSubmittable(targetComputer, run)) {
            try {
              job.replaceComputer(targetComputer);
            } catch (RunNotFoundException e) {
              WarnLogMessage.issue(e);
              job.remove();
            }
          }

          targetHostCursor += 1;
          if (targetHostCursor >= passableComputerList.size()) {
            targetHostCursor = 0;
          }
        }
      }
    }

    InspectorMaster.forceCheck();

    return true;
  }
}
