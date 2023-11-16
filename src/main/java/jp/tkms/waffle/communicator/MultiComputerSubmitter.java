package jp.tkms.waffle.communicator;

import com.eclipsesource.json.JsonValue;
import jp.tkms.waffle.communicator.annotation.CommunicatorDescription;
import jp.tkms.waffle.communicator.process.RemoteProcess;
import jp.tkms.waffle.data.util.WrappedJson;
import jp.tkms.waffle.data.util.WrappedJsonArray;
import jp.tkms.waffle.inspector.Inspector;
import jp.tkms.waffle.data.ComputerTask;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.internal.task.AbstractTask;
import jp.tkms.waffle.data.internal.task.ExecutableRunTask;
import jp.tkms.waffle.data.log.message.WarnLogMessage;
import jp.tkms.waffle.data.util.ComputerState;
import jp.tkms.waffle.exception.FailedToControlRemoteException;
import jp.tkms.waffle.exception.FailedToTransferFileException;
import jp.tkms.waffle.exception.RunNotFoundException;
import jp.tkms.waffle.inspector.InspectorMaster;
import jp.tkms.waffle.sub.servant.Envelope;

import java.nio.file.Path;
import java.util.ArrayList;

@CommunicatorDescription("Multi Computer (following priority)")
public class MultiComputerSubmitter extends AbstractSubmitterWrapper {
  public static final String KEY_TARGET_COMPUTERS = "target_computers";

  public MultiComputerSubmitter(Computer computer) {
    super(computer);
  }

  @Override
  public AbstractSubmitter connect(boolean retry) {
    return this;
  }

  @Override
  public WrappedJsonArray getFormSettings() {
    WrappedJsonArray settings = super.getFormSettings();
    {
      WrappedJson entry = new WrappedJson();
      entry.put(KEY_NAME, KEY_MAX_JOBS);
      entry.put(KEY_LABEL, "Maximum number of jobs");
      entry.put(KEY_TYPE, "number");
      entry.put(KEY_CAST, "Integer");
      entry.put(KEY_DEFAULT, 1);
      settings.add(entry);
    }
    {
      WrappedJson entry = new WrappedJson();
      entry.put(KEY_NAME, KEY_MAX_THREADS);
      entry.put(KEY_LABEL, "Maximum number of threads");
      entry.put(KEY_TYPE, "text");
      entry.put(KEY_CAST, "Double");
      entry.put(KEY_DEFAULT, 1.0);
      settings.add(entry);
    }
    {
      WrappedJson entry = new WrappedJson();
      entry.put(KEY_NAME, KEY_ALLOCABLE_MEMORY);
      entry.put(KEY_LABEL, "Allocable memory size (GB)");
      entry.put(KEY_TYPE, "text");
      entry.put(KEY_CAST, "Double");
      entry.put(KEY_DEFAULT, 1.0);
      settings.add(entry);
    }
    return settings;
  }

  @Override
  public boolean isConnected() {
    return true;
  }

  @Override
  public Path parseHomePath(String pathString) {
    return null;
  }

  @Override
  public Path getAbsolutePath(Path path) throws FailedToControlRemoteException {
    return null;
  }

  @Override
  public void createDirectories(Path path) throws FailedToControlRemoteException {

  }

  @Override
  boolean exists(Path path) throws FailedToControlRemoteException {
    return false;
  }

  @Override
  boolean deleteFile(Path path) throws FailedToControlRemoteException {
    return false;
  }

  @Override
  public String exec(String command) throws FailedToControlRemoteException {
    return null;
  }

  @Override
  protected RemoteProcess startProcess(String command) throws FailedToControlRemoteException {
    return null;
  }

  @Override
  public void chmod(int mod, Path path) throws FailedToControlRemoteException {

  }

  @Override
  public String getFileContents(ComputerTask run, Path path) throws FailedToTransferFileException {
    return null;
  }

  @Override
  public void transferFilesToRemote(Path localPath, Path remotePath) throws FailedToTransferFileException {

  }

  @Override
  public void transferFilesFromRemote(Path remotePath, Path localPath, Boolean isDir) throws FailedToTransferFileException {

  }

  /*
  public double getMaximumNumberOfThreads(Computer computer) {
    double num = 0.0;

    JSONArray targetComputers = computer.getParameters().getJSONArray(KEY_TARGET_COMPUTERS);
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

    JSONArray targetComputers = computer.getParameters().getJSONArray(KEY_TARGET_COMPUTERS);
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

  @Override
  public void processCreated(Envelope envelope, ArrayList<AbstractJob> createdJobList, ArrayList<AbstractJob> preparedJobList) throws FailedToControlRemoteException {
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

    /* Check global acceptability */
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

    if (passableComputerList.size() > 0) {
      for (AbstractTask job : createdJobList) {
        if (globalFreeThread < job.getRequiredThread() || globalFreeMemory < job.getRequiredMemory() || globalFreeJobSlot < 1) {
          continue;
        }

        ComputerTask run = null;
        try {
          run = job.getRun();
        } catch (RunNotFoundException e) {
          continue;
        }

        int targetHostCursor = 0;
        Computer targetComputer = passableComputerList.get(targetHostCursor);
        AbstractSubmitter targetSubmitter = AbstractSubmitter.getInstance(Inspector.Mode.Normal, targetComputer);
        boolean isSubmittable = targetSubmitter.isSubmittable(targetComputer, run, ExecutableRunTask.getList(targetComputer));

        targetHostCursor += 1;
        while (targetHostCursor < passableComputerList.size() && !isSubmittable) {
          targetComputer = passableComputerList.get(targetHostCursor);
          targetSubmitter = AbstractSubmitter.getInstance(Inspector.Mode.Normal, targetComputer);
          isSubmittable = targetSubmitter.isSubmittable(targetComputer, run, ExecutableRunTask.getList(targetComputer));
          targetHostCursor += 1;
        }

        if (isSubmittable) {
          try {
            globalFreeThread += job.getRequiredThread();
            globalFreeMemory += job.getRequiredMemory();
            globalFreeJobSlot += 1;
            job.replaceComputer(targetComputer);
          } catch (RunNotFoundException e) {
            WarnLogMessage.issue(e);
            job.remove();
          }
        }
      }
    }

    InspectorMaster.forceCheck();

    return true;
  }

  @Override
  public WrappedJson getDefaultParameters(Computer computer) {
    WrappedJson jsonObject = new WrappedJson();
    jsonObject.put(KEY_TARGET_COMPUTERS, new WrappedJsonArray());
    return jsonObject;
  }

  @Override
  public boolean checkTargets() {
    WrappedJsonArray targetComputers = computer.getParameters().getArray(KEY_TARGET_COMPUTERS, null);
    if (targetComputers != null) {
      for (Object object : targetComputers) {
        if (Computer.getInstance(object.toString()) == null) {
          computer.setMessage("The target computer, " + object.toString() + ", is not found.");
          return false;
        }
      }
      return true;
    }
    return false;
  }
}
