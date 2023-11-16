package jp.tkms.waffle.communicator;

import com.eclipsesource.json.JsonValue;
import jp.tkms.utils.concurrent.LockByKey;
import jp.tkms.waffle.Constants;
import jp.tkms.waffle.communicator.annotation.CommunicatorDescription;
import jp.tkms.waffle.communicator.process.RemoteProcess;
import jp.tkms.waffle.data.internal.InternalFiles;
import jp.tkms.waffle.data.internal.ServantScript;
import jp.tkms.waffle.data.util.*;
import jp.tkms.waffle.inspector.Inspector;
import jp.tkms.waffle.data.ComputerTask;
import jp.tkms.waffle.data.DataDirectory;
import jp.tkms.waffle.data.PropertyFile;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.internal.SystemTaskRun;
import jp.tkms.waffle.data.internal.task.AbstractTask;
import jp.tkms.waffle.data.internal.task.ExecutableRunTask;
import jp.tkms.waffle.data.internal.task.SystemTask;
import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.log.message.InfoLogMessage;
import jp.tkms.waffle.data.log.message.WarnLogMessage;
import jp.tkms.waffle.data.project.executable.Executable;
import jp.tkms.waffle.exception.FailedToControlRemoteException;
import jp.tkms.waffle.exception.FailedToTransferFileException;
import jp.tkms.waffle.exception.OccurredExceptionsException;
import jp.tkms.waffle.exception.RunNotFoundException;
import jp.tkms.waffle.inspector.InspectorMaster;
import jp.tkms.waffle.sub.servant.Envelope;
import jp.tkms.waffle.sub.servant.message.request.*;
import jp.tkms.waffle.sub.servant.message.response.*;
import jp.tkms.waffle.sub.servant.pod.PodTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

@CommunicatorDescription("Pod Wrapper (pre-allocating workspace by a job)")
public class PodWrappedSubmitter extends AbstractSubmitterWrapper {
  public static final String KEY_TARGET_COMPUTER = "target_computer";
  public static final String JOB_MANAGER = "JOB_MANAGER";
  public static final String BIN = "BIN";
  public static final String JOB_MANAGER_JSON_FILE = JOB_MANAGER + Constants.EXT_JSON;
  public static final String RUNNING = "running";
  public static final String ACTIVE = "active";
  public static final String JOB_COUNT = "job_count";
  public static final String RUN_SH_FILE = "sh ./run.sh";
  public static final String KEY_REMOTE_DIRECTORY = "remote_directory";
  public static final Path JOBS_PATH = Paths.get("JOBS");
  public static final Path ENTITIES_PATH = Paths.get("ENTITIES");
  public static final Path ALIVE_NOTIFIER_PATH = Paths.get("ALIVE_NOTIFIER");
  public static final Path LOCKOUT_FILE_PATH = ALIVE_NOTIFIER_PATH.resolve("LOCKOUT");
  public static final String KEY_ADDITIONAL_PREPARE_COMMAND = "additional_preparation_command";
  public static final String KEY_EMPTY_TIMEOUT = "empty_timeout";
  public static final String KEY_FORCE_SHUTDOWN = "force_shutdown";
  public static final String KEY_SHUTDOWN_PREPARATION_MARGIN = "shutdown_preparation_margin";

  //private static final InstanceCache<String, Boolean> existsCheckCache = new InstanceCache<>();

  private JobManager jobManager;
  AbstractSubmitter targetSubmitter;

  public PodWrappedSubmitter(Computer computer) {
    super(computer);
    jobManager = new JobManager(this);
  }

  @Override
  public boolean checkTargets() {
    if (Computer.getInstance(computer.getParameters().getString(KEY_TARGET_COMPUTER, "")) == null) {
      computer.setMessage("The target computer is not found.");
      return false;
    }
    return true;
  }

  @Override
  public WrappedJsonArray getFormSettings() {
    WrappedJsonArray settings = super.getFormSettings();
    {
      WrappedJson entry = new WrappedJson();
      entry.put(KEY_NAME, KEY_MAX_JOBS);
      entry.put(KEY_LABEL, "Maximum number of jobs (Number of pods)");
      entry.put(KEY_TYPE, "number");
      entry.put(KEY_CAST, "Integer");
      entry.put(KEY_DEFAULT, 1);
      settings.add(entry);
    }
    {
      WrappedJson entry = new WrappedJson();
      entry.put(KEY_NAME, KEY_MAX_THREADS);
      entry.put(KEY_LABEL, "Maximum number of threads (per a pod)");
      entry.put(KEY_TYPE, "text");
      entry.put(KEY_CAST, "Double");
      entry.put(KEY_DEFAULT, 1.0);
      settings.add(entry);
    }
    {
      WrappedJson entry = new WrappedJson();
      entry.put(KEY_NAME, KEY_ALLOCABLE_MEMORY);
      entry.put(KEY_LABEL, "Allocable memory size [GB] (per a pod)");
      entry.put(KEY_TYPE, "text");
      entry.put(KEY_CAST, "Double");
      entry.put(KEY_DEFAULT, 1.0);
      settings.add(entry);
    }
    return settings;
  }

  @Override
  public Envelope sendAndReceiveEnvelope(Envelope envelope) throws Exception {
    return sendAndReceiveEnvelope(targetSubmitter, envelope);
  }

  @Override
  public void submit(Envelope envelope, AbstractTask job) throws RunNotFoundException {
    VirtualJobExecutor executor = jobManager.getNextExecutor(job);
    if (executor != null) {
      try {
        if (job.getState().equals(State.Submitted)) return;
        InfoLogMessage.issue(job.getRun(), "will be submitted");
        if (job.getState().equals(State.Created)) {
          return;
        }
        executor.submit(envelope, job);
        job.setState(State.Submitted);
      } catch (Exception e) {
        WarnLogMessage.issue(job.getComputer(), e.getMessage());
        job.setState(State.Excepted);
      }
    }
  }

  @Override
  protected boolean isSubmittable(Computer computer, ComputerTask next, ArrayList<ComputerTask> list) {
    if (this.isConnected()) {
      JobManager.Submittable submittable = jobManager.getSubmittable(next);
      return (submittable.usableExecutor != null || submittable.isNewExecutorCreatable);
    } else {
      return jobManager.isReceptacle(next, list);
    }
  }

  @Override
  public void update(Envelope envelope, AbstractTask job) throws RunNotFoundException, FailedToControlRemoteException {
    String executorId = job.getJobId().replaceFirst("\\..*$", "");
    Path podDirectory = InternalFiles.getLocalPath(computer.getLocalPath().resolve(JOB_MANAGER).resolve(executorId));
    envelope.add(new CollectPodTaskStatusMessage(job.getTypeCode(), job.getHexCode(), !jobManager.isContainKeyOfRunningExecutor(executorId), podDirectory, getRunDirectory(job.getRun())));
  }

  @Override
  public Envelope processResponse(Envelope response) {
    // super.processResponse(response);

    if (response != null) {
      for (RequestRepreparingMessage message : response.getMessageBundle().getCastedMessageList(RequestRepreparingMessage.class)) {
        AbstractTask job = findJobFromStore(message.getType(), message.getId());
        if (job != null) {
          try {
            String executorId = job.getJobId().replaceFirst("\\..*$", "");
            VirtualJobExecutor executor = jobManager.getRunningExecutor(executorId);
            if (executor == null) {
              executor = VirtualJobExecutor.getInstance(jobManager, WaffleId.valueOf(executorId));
            }
            if (executor != null) {
              executor.removeRunning(job.getJobId());
            }
            InfoLogMessage.issue(job.getRun(), "will re-prepare");
          } catch (RunNotFoundException e) {
            //NOP
          }
        }
      }

      for (PodTaskFinishedMessage message : response.getMessageBundle().getCastedMessageList(PodTaskFinishedMessage.class)) {
        AbstractTask job = findJobFromStore(message.getType(), message.getId());
        if (job != null) {
          String executorId = job.getJobId().replaceFirst("\\..*$", "");
          VirtualJobExecutor executor = jobManager.getRunningExecutor(executorId);
          if (executor == null) {
            executor = VirtualJobExecutor.getInstance(jobManager, WaffleId.valueOf(executorId));
          }
          if (executor != null) {
            executor.removeRunning(job.getJobId());
          }
        }
      }

      for (PodTaskRefusedMessage message : response.getMessageBundle().getCastedMessageList(PodTaskRefusedMessage.class)) {
        String executorId = message.getJobId().replaceFirst("\\..*$", "");
        VirtualJobExecutor executor = jobManager.getRunningExecutor(executorId);
        if (executor == null) {
          executor = VirtualJobExecutor.getInstance(jobManager, WaffleId.valueOf(executorId));
        }
        if (executor != null) {
          AbstractTask task = executor.removeRunning(message.getJobId());
          try {
            task.setState(State.Prepared);
          } catch (RunNotFoundException e) {
            ErrorLogMessage.issue(e);
          }
        }
        InfoLogMessage.issue("PodTask(" + message.getJobId() + ") is refused by the pod; The task will be retried.");
      }

      for (PodTaskCanceledMessage message : response.getMessageBundle().getCastedMessageList(PodTaskCanceledMessage.class)) {
        AbstractTask job = findJobFromStore(message.getType(), message.getId());
        if (job != null) {
          try {
            if (job.getState().equals(State.Abort)) {
              job.setState(State.Aborted);
            }
            if (job.getState().equals(State.Cancel)) {
              job.setState(State.Canceled);
            }
          } catch (RunNotFoundException e) {
            WarnLogMessage.issue(e);
          }
          String executorId = job.getJobId().replaceFirst("\\..*$", "");
          VirtualJobExecutor executor = jobManager.getRunningExecutor(executorId);
          if (executor == null) {
            executor = VirtualJobExecutor.getInstance(jobManager, WaffleId.valueOf(executorId));
          }
          if (executor != null) {
            executor.removeRunning(job.getJobId());
          }
        }
      }

      for (UpdatePodStatusMessage message : response.getMessageBundle().getCastedMessageList(UpdatePodStatusMessage.class)) {
        if (message.getState() == UpdatePodStatusMessage.LOCKED) {
          jobManager.deactivateExecutor(message.getId());
        } else if (message.getState() == UpdatePodStatusMessage.FINISHED) {
          jobManager.removeExecutor(message.getId());
        }
      }
    }

    return response;
  }

  @Override
  protected Envelope processRequestAndResponse(Envelope envelope) {
    jobManager.processEachRunningExecutor(entry -> {
      envelope.add(new CollectPodStatusMessage(entry.getKey(), entry.getValue().getLocalPath()));
    });

    return processResponse(super.processRequestAndResponse(envelope));
  }

  @Override
  protected void prepareJob(Envelope envelope, AbstractTask job) throws RunNotFoundException, FailedToControlRemoteException, FailedToTransferFileException {
    super.prepareJob(envelope, job);

    try {
      if (computer.getParameters().keySet().contains(KEY_ADDITIONAL_PREPARE_COMMAND)) {
        ComputerTask run = job.getRun();
        exec("cd '" + getRunDirectory(run) + "';" +computer.getParameter(KEY_ADDITIONAL_PREPARE_COMMAND));
        InfoLogMessage.issue("cd '" + getRunDirectory(run) + "';" +computer.getParameter(KEY_ADDITIONAL_PREPARE_COMMAND));
      }
    } catch (Exception e) {
      ErrorLogMessage.issue(e);
    }
  }

  @Override
  public void cancel(Envelope envelope, AbstractTask job) throws RunNotFoundException, FailedToControlRemoteException {
    try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
      if (jobManager.removeJob(envelope, job)) {
        if (job.getState().equals(State.Abort)) {
          job.setState(State.Aborted);
        }
        if (job.getState().equals(State.Cancel)) {
          job.setState(State.Canceled);
        }
      }
    }
  }

  @Override
  public void close() {
    super.close();
    jobManager.close();
    targetSubmitter.close();
  }


  public static class JobManager implements DataDirectory, PropertyFile {
    private PodWrappedSubmitter submitter;
    private Map<String, VirtualJobExecutor> runningExecutorList;
    private Map<String, VirtualJobExecutor> activeExecutorList;
    private VirtualJobExecutor submittableCache = null;

    public JobManager(PodWrappedSubmitter submitter) {
      this.submitter = submitter;

      try {
        Files.createDirectories(getPath());
      } catch (IOException e) {
        ErrorLogMessage.issue(e);
      }
      if (getArrayFromProperty(RUNNING) == null) {
        putNewArrayToProperty(RUNNING);
      }
      if (getArrayFromProperty(ACTIVE) == null) {
        putNewArrayToProperty(ACTIVE);
      }
      runningExecutorList = new LinkedHashMap<>();
      activeExecutorList = new LinkedHashMap<>();
      WrappedJsonArray runningArray = getArrayFromProperty(RUNNING);
      for (Object running : runningArray) {
        WaffleId waffleId = WaffleId.valueOf(running.toString());
        VirtualJobExecutor executor = VirtualJobExecutor.getInstance(this, waffleId);
        if (executor != null) {
          runningExecutorList.put(waffleId.getReversedBase36Code(), executor);
        } else {
          removeFromArrayOfProperty(RUNNING, waffleId.getReversedBase36Code());
        }
      }
      WrappedJsonArray activeArray = getArrayFromProperty(ACTIVE);
      for (Object active : activeArray) {
        WaffleId waffleId = WaffleId.valueOf(active.toString());
        VirtualJobExecutor executor = VirtualJobExecutor.getInstance(this, waffleId);
        if (executor != null) {
          activeExecutorList.put(waffleId.getReversedBase36Code(), executor);
        } else {
          removeFromArrayOfProperty(ACTIVE, waffleId.getReversedBase36Code());
        }
      }
    }

    public void close() {
      ArrayList<VirtualJobExecutor> removingList = new ArrayList<>();
      for (VirtualJobExecutor executor : runningExecutorList.values()) {
        try {
          if (!executor.getPath().toFile().exists()) {
            removingList.add(executor);
          }
        } catch (Exception e) {
          ErrorLogMessage.issue(e);
        }
      }
      for (VirtualJobExecutor executor : removingList) {
        removeExecutor(executor);
      }
    }

    public VirtualJobExecutor getRunningExecutor(String id) {
      synchronized (runningExecutorList) {
        return runningExecutorList.get(id);
      }
    }

    public boolean isContainKeyOfRunningExecutor(String id) {
      synchronized (runningExecutorList) {
        return runningExecutorList.containsKey(id);
      }
    }

    public void processEachRunningExecutor(Consumer<Map.Entry<String, VirtualJobExecutor>> consumer) {
      synchronized (runningExecutorList) {
        for (Map.Entry<String, VirtualJobExecutor> entry : runningExecutorList.entrySet()) {
          consumer.accept(entry);
        }
      }
    }

    public VirtualJobExecutor getNextExecutor(AbstractTask next) throws RunNotFoundException {
      synchronized (runningExecutorList) {
        double globalFreeThread = 0.0;
        {
          JsonValue jsonValue = (JsonValue) getComputer().getParameter(KEY_MAX_THREADS, submitter);
          if (jsonValue.isNumber()) {
            globalFreeThread = jsonValue.asDouble();
          }
        }

        double globalFreeMemory = 0.0;
        {
          JsonValue jsonValue = (JsonValue) getComputer().getParameter(KEY_ALLOCABLE_MEMORY, submitter);
          if (jsonValue.isNumber()) {
            globalFreeMemory = jsonValue.asDouble();
          }
        }

        Submittable submittable = getSubmittable(next.getRun());
        VirtualJobExecutor result = submittable.usableExecutor;
        if (result == null && submittable.isNewExecutorCreatable) {
          SystemTask executorJob = SystemTask.addRun(VirtualJobExecutor.create(this, globalFreeThread, globalFreeMemory,
            true,
            Integer.parseInt(submitter.computer.getParameter(KEY_EMPTY_TIMEOUT).toString()),
            Integer.parseInt(submitter.computer.getParameter(KEY_FORCE_SHUTDOWN).toString()),
            Integer.parseInt(submitter.computer.getParameter(KEY_SHUTDOWN_PREPARATION_MARGIN).toString())
          ));
          executorJob.replaceComputer(submitter.targetSubmitter.computer);
          result = (VirtualJobExecutor) executorJob.getRun();
          registerExecutor(result);
        }
        return result;
      }
    }

    static class Submittable {
      Computer targetComputer = null;
      boolean isNewExecutorCreatable = false;
      VirtualJobExecutor usableExecutor = null;
      Submittable() {
      }
    }

    protected Submittable getSubmittable(ComputerTask next) {
      synchronized (runningExecutorList) {
        double globalFreeThread = 0.0;
        {
          JsonValue jsonValue = (JsonValue) getComputer().getParameter(KEY_MAX_THREADS, submitter);
          if (jsonValue.isNumber()) {
            globalFreeThread = jsonValue.asDouble();
          }
        }

        double globalFreeMemory = 0.0;
        {
          JsonValue jsonValue = (JsonValue) getComputer().getParameter(KEY_ALLOCABLE_MEMORY, submitter);
          if (jsonValue.isNumber()) {
            globalFreeMemory = jsonValue.asDouble();
          }
        }

        double threadSize = (next == null ? 0 : next.getRequiredThread());
        double memorySize = (next == null ? 0 : next.getRequiredMemory());

        Submittable result = new Submittable();

        for (VirtualJobExecutor executor : new ArrayList<>(runningExecutorList.values())) {
          Path directoryPath = executor.getPath();
          if (!directoryPath.toFile().exists() || !executor.isRunning()) {
            removeExecutor(executor);
            if (directoryPath.toFile().exists()) {
              executor.deleteDirectory();
            }
          }
        }

        ArrayList<VirtualJobExecutor> shuffledList = new ArrayList<>(activeExecutorList.values());
        Collections.shuffle(shuffledList);
        ArrayList<VirtualJobExecutor> cacheAppliedList = new ArrayList<>();
        if (submittableCache != null && shuffledList.contains(submittableCache)) {
          cacheAppliedList.add(submittableCache);
          shuffledList.remove(submittableCache);
          cacheAppliedList.addAll(shuffledList);
        } else {
          cacheAppliedList.addAll(shuffledList);
        }

        for (VirtualJobExecutor executor : cacheAppliedList) {
          Path directoryPath = executor.getPath();
          if (!directoryPath.toFile().exists()) {
            //if (!existsCheckCache.getOrCreate(directoryPath.toString(), k -> directoryPath.toFile().exists())) {
            removeExecutor(executor);
            continue;
          }
          //System.out.println(executor.getId());
          double usedThread = 0.0;
          double usedMemory = 0.0;
          for (AbstractTask abstractTask : executor.getRunningList()) {
            usedThread += abstractTask.getRequiredThread();
            usedMemory += abstractTask.getRequiredMemory();
          }
          if (threadSize <= (globalFreeThread - usedThread)
            && memorySize <= (globalFreeMemory - usedMemory)) {
            if (executor.isAcceptable()) {
              result.usableExecutor = executor;
              submittableCache = executor;
              break;
            } else {
              deactivateExecutor(executor);
            }
          }
        }

        result.targetComputer = Computer.getInstance(getComputer().getParameters().getString(KEY_TARGET_COMPUTER, ""));
        if (result.targetComputer != null) {
          VirtualJobExecutor executor = VirtualJobExecutor.create(this, globalFreeThread, globalFreeMemory, true, 0, 0, 0);
          AbstractSubmitter targetSubmitter = AbstractSubmitter.getInstance(Inspector.Mode.Normal, result.targetComputer);
          result.isNewExecutorCreatable = targetSubmitter.isSubmittable(result.targetComputer, executor);
          executor.deleteDirectory();
          executor.finish();
        }
      /*
      if (runningExecutorList.size() < getComputer().getMaximumNumberOfJobs()) {
        result.isNewExecutorCreatable = true;
      }
       */

        return result;
      }
    }

    public boolean isReceptacle(ComputerTask next, ArrayList<ComputerTask> list) {
      synchronized (runningExecutorList) {
        double globalFreeThread = 0.0;
        {
          JsonValue jsonValue = (JsonValue) getComputer().getParameter(KEY_MAX_THREADS, submitter);
          if (jsonValue.isNumber()) {
            globalFreeThread = jsonValue.asDouble();
          }
        }

        double globalFreeMemory = 0.0;
        {
          JsonValue jsonValue = (JsonValue) getComputer().getParameter(KEY_ALLOCABLE_MEMORY, submitter);
          if (jsonValue.isNumber()) {
            globalFreeMemory = jsonValue.asDouble();
          }
        }

        for (VirtualJobExecutor executor : new ArrayList<>(runningExecutorList.values())) {
          Path directoryPath = executor.getPath();
          if (!directoryPath.toFile().exists() || !executor.isRunning()) {
            removeExecutor(executor);
            if (directoryPath.toFile().exists()) {
              executor.deleteDirectory();
            }
          }
        }

        ArrayList<ComputerTask> queuedList = new ArrayList<>();
        queuedList.add(next);
        for (ComputerTask task : list) {
          switch (task.getState()) {
            case Created:
            case Prepared:
              queuedList.add(task);
          }
        }

        ArrayList<VirtualJobExecutor> shuffledList = new ArrayList<>(activeExecutorList.values());
        Collections.shuffle(shuffledList);
        ArrayList<VirtualJobExecutor> cacheAppliedList = new ArrayList<>();
        if (submittableCache != null && shuffledList.contains(submittableCache)) {
          cacheAppliedList.add(submittableCache);
          shuffledList.remove(submittableCache);
          cacheAppliedList.addAll(shuffledList);
        } else {
          cacheAppliedList.addAll(shuffledList);
        }
        for (VirtualJobExecutor executor : cacheAppliedList) {
          Path directoryPath = executor.getPath();
          if (!directoryPath.toFile().exists()) {
            //if (!existsCheckCache.getOrCreate(directoryPath.toString(), k -> directoryPath.toFile().exists())) {
            removeExecutor(executor);
            continue;
          }

          double usedThread = 0.0;
          double usedMemory = 0.0;
          for (AbstractTask abstractTask : executor.getRunningList()) {
            usedThread += abstractTask.getRequiredThread();
            usedMemory += abstractTask.getRequiredMemory();
          }
          //System.out.println(executor.getId());
          for (int i = queuedList.size() - 1; i >= 0; i -= 1) {
            double threadSize = (next == null ? 0 : next.getRequiredThread());
            double memorySize = (next == null ? 0 : next.getRequiredMemory());

            if (threadSize <= (globalFreeThread - usedThread)
              && memorySize <= (globalFreeMemory - usedMemory)) {
              if (queuedList.size() <= 1) {
                return true;
              } else {
                queuedList.remove(i);
                usedThread += threadSize;
                usedMemory += memorySize;
              }
            }
          }
        }

        int globalFreeJobSlot = 0;
        JsonValue jsonValue = (JsonValue) getComputer().getParameter(KEY_MAX_JOBS, submitter);
        if (jsonValue.isNumber()) {
          globalFreeJobSlot = jsonValue.asInt();
        }
        for (int count = 0; count < globalFreeJobSlot - runningExecutorList.size(); count += 1) {
          double usedThread = 0.0;
          double usedMemory = 0.0;

          for (int i = queuedList.size() - 1; i >= 0; i -= 1) {
            double threadSize = (next == null ? 0 : next.getRequiredThread());
            double memorySize = (next == null ? 0 : next.getRequiredMemory());

            if (threadSize <= (globalFreeThread - usedThread)
              && memorySize <= (globalFreeMemory - usedMemory)) {
              if (queuedList.size() <= 1) {
                return true;
              } else {
                queuedList.remove(i);
                usedThread += threadSize;
                usedMemory += memorySize;
              }
            } else {
              break;
            }
          }
        }

        return false;
      }
    }

    boolean removeJob(Envelope envelope, AbstractTask job) throws RunNotFoundException {
      synchronized (runningExecutorList) {
        for (VirtualJobExecutor executor : new ArrayList<>(runningExecutorList.values())) {
          if (executor.getRunningList().contains(job)) {
            executor.cancel(envelope, job);
            return false;
          }
        }
        return true;
      }
    }

    void registerExecutor(VirtualJobExecutor executor) {
      synchronized (runningExecutorList) {
        putToArrayOfProperty(RUNNING, executor.getId());
        runningExecutorList.put(executor.getId(), executor);
        putToArrayOfProperty(ACTIVE, executor.getId());
        activeExecutorList.put(executor.getId(), executor);
      }
    }

    void deactivateExecutor(VirtualJobExecutor executor) {
      deactivateExecutor(executor.getId());
    }

    void deactivateExecutor(String id) {
      removeFromArrayOfProperty(ACTIVE, id);
      activeExecutorList.remove(id);
    }

    void removeExecutor(VirtualJobExecutor executor) {
      removeExecutor(executor.getId());
    }

    void removeExecutor(String id) {
      synchronized (runningExecutorList) {
        deactivateExecutor(id);
        removeFromArrayOfProperty(RUNNING, id);
        runningExecutorList.remove(id);
      }
    }

    public Computer getComputer() {
      return submitter.computer;
    }

    @Override
    public Path getPath() {
      return InternalFiles.getPath(getComputer().getLocalPath().resolve(JOB_MANAGER));
    }

    @Override
    public Path getPropertyStorePath() {
      return getPath().resolve(JOB_MANAGER_JSON_FILE);
    }

    WrappedJson jsonObject = null;
    @Override
    public WrappedJson getPropertyStoreCache() {
      return jsonObject;
    }

    @Override
    public void setPropertyStoreCache(WrappedJson cache) {
      jsonObject = cache;
    }

    public Path getBinDirectory() {
      Path dirPath = getPath().resolve(BIN);
      if (!dirPath.toFile().exists()) {
        try {
          Files.createDirectories(dirPath);
        } catch (IOException e) {
          ErrorLogMessage.issue(e);
        }
      }
      return dirPath;
    }

    /*
    public boolean checkStat(AbstractJob job) throws RunNotFoundException {
      String executorId = job.getJobId().replaceFirst("\\..*$", "");
      VirtualJobExecutor executor = runningExecutorList.get(executorId);
      if (executor == null) {
        executor = VirtualJobExecutor.getInstance(this, WaffleId.valueOf(executorId));
      }
      boolean finished = true;
      if (executor != null) {
        finished = executor.checkStat(submitter, job);
      }
      return finished;
    }
     */
  }

  public static class VirtualJobExecutor extends SystemTaskRun {
    WaffleId id;
    //ArrayList<AbstractJob> runningList;
    int jobCount;
    HashMap<String, AbstractTask> jobCache = new HashMap<>();

    public VirtualJobExecutor(Path path) {
      super(path);
      //this.jobManager = jobManager;
      this.id = WaffleId.valueOf(path.getFileName().toString());
      this.jobCount = getIntFromProperty(JOB_COUNT, -1);
      if (getArrayFromProperty(RUNNING) == null) {
        putNewArrayToProperty(RUNNING);
      }
    }

    public int getNextJobCount() {
      jobCount += 1;
      setToProperty(JOB_COUNT, jobCount);
      return jobCount;
    }

    public void submit(Envelope envelope, AbstractTask job) throws RunNotFoundException {
      synchronized (jobCache) {
        try {
          if (job.getState().equals(State.Submitted)) return;

          String jobId = id.getReversedBase36Code() + '.' + getNextJobCount();
          Path podDirectory = InternalFiles.getLocalPath(getComputer().getLocalPath().resolve(JOB_MANAGER).resolve(id.getReversedBase36Code()));
          envelope.add(new SubmitPodTaskMessage(job.getTypeCode(), job.getHexCode(), jobId, podDirectory, job.getRun().getLocalPath(), job.getRun().getRemoteBinPath()));
        /*
        Path runDirectoryPath = submitter.getRunDirectory(job.getRun());
        submitter.createDirectories(getRemoteEntitiesDirectory());
        submitter.createDirectories(getRemoteJobsDirectory());
        submitter.chmod(777, getRemoteEntitiesDirectory());
        submitter.chmod(777, getRemoteJobsDirectory());
        submitter.putText(job, getRemoteEntitiesDirectory().resolve(job.getId().toString()), runDirectoryPath.toString());
        submitter.chmod(666, getRemoteEntitiesDirectory().resolve(job.getId().toString()));
        submitter.putText(job, getRemoteJobsDirectory().resolve(job.getId().toString()), "");
        submitter.chmod(666, getRemoteJobsDirectory().resolve(job.getId().toString()));
        job.setJobId(jobId);
         */
          putToArrayOfProperty(RUNNING, jobId);
          jobCache.put(jobId, job);
          job.setState(State.Submitted);
        } catch (Exception e) {
          job.setState(State.Excepted);
        }
      }
    }

    public void cancel(Envelope envelope, AbstractTask job) throws RunNotFoundException {
      Path podDirectory = InternalFiles.getLocalPath(getComputer().getLocalPath().resolve(JOB_MANAGER).resolve(id.getReversedBase36Code()));
      try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
        envelope.add(new CancelPodTaskMessage(job.getTypeCode(), job.getHexCode(), podDirectory, job.getRun().getLocalPath()));
      }
    }

    String getId() {
      return id.getReversedBase36Code();
    }

    public static VirtualJobExecutor getInstance(JobManager jobManager, WaffleId id) {
      if (id != null && getDirectoryPath(jobManager, id).toFile().exists()) {
        VirtualJobExecutor run = null;
        try {
          run = (VirtualJobExecutor) SystemTaskRun.getInstance(getLocalDirectoryPath(jobManager, id).toString());
        } catch (RunNotFoundException e) {
          ErrorLogMessage.issue(e);
        }
        return run;
      }
      return null;
    }

    public static VirtualJobExecutor create(JobManager jobManager, double threadSize, double memorySize, boolean isLocal, int emptyTimeout, int forceShutdownTime, int shutdownPreparationTime) {
      WaffleId id = WaffleId.newId();
      VirtualJobExecutor executor = new VirtualJobExecutor(getDirectoryPath(jobManager, id));
      executor.setCommand(PodTask.PODTASK);
      executor.addArgument(isLocal);
      executor.addArgument(emptyTimeout);
      executor.addArgument(forceShutdownTime);
      executor.addArgument(shutdownPreparationTime);
      executor.setBinPath(null);
      executor.setRequiredThread(threadSize);
      executor.setRequiredMemory(memorySize);
      executor.setComputer(jobManager.getComputer());
      executor.setState(State.Created);
      executor.setToProperty(KEY_EXIT_STATUS, -1);
      executor.setToProperty(KEY_CREATED_AT, DateTime.getCurrentEpoch());
      executor.setToProperty(KEY_SUBMITTED_AT, DateTime.getEmptyEpoch());
      executor.setToProperty(KEY_FINISHED_AT, DateTime.getEmptyEpoch());
      /*
      Path workBasePath = Paths.get(jobManager.getComputer().getWorkBaseDirectory());
      try {
        workBasePath = jobManager.submitter.parseHomePath(jobManager.getComputer().getWorkBaseDirectory());
      } catch (FailedToControlRemoteException e) {
        ErrorLogMessage.issue(e);
      }
      executor.setToProperty(KEY_REMOTE_DIRECTORY, workBasePath.resolve(executor.getLocalDirectoryPath()).toString());
       */
      executor.setToProperty(KEY_REMOTE_DIRECTORY, executor.getLocalPath().toString());
      try {
        Files.createDirectories(executor.getBasePath());
      } catch (IOException e) {
        ErrorLogMessage.issue(e);
      }
      return executor;
    }

    @Override
    public void setState(State state) {
      if (getPath().toFile().exists()) {
        super.setState(state);
      }
    }

    @Override
    public void finish() {
      super.finish();
      deleteDirectory();

      //TODO: if is run not finished
    }

    @Override
    public void specializedPostProcess(AbstractSubmitter submitter, AbstractTask job) throws OccurredExceptionsException, RunNotFoundException {
      try {
        submitter.exec("rm -rf '" + getRemoteBaseDirectory().getParent().toString() + "'");
      } catch (FailedToControlRemoteException e) {
        ErrorLogMessage.issue(e);
      }
    }

    public AbstractTask removeRunning(String jobId) {
      synchronized (jobCache) {
        removeFromArrayOfProperty(RUNNING, jobId);
        return jobCache.remove(jobId);
      }
    }

    public boolean isAcceptable() {
      switch (getState()) {
        case Created:
        case Prepared:
        case Submitted:
        case Running:
          return true;
      }
      return false;
    }

    public ArrayList<AbstractTask> getRunningList() {
      synchronized (jobCache) {
        ArrayList<AbstractTask> runningList = new ArrayList<>();
        WrappedJsonArray runningArray = getArrayFromProperty(RUNNING);
        for (Object o : runningArray) {
          if (jobCache.containsKey(o.toString())) {
            runningList.add(jobCache.get(o.toString()));
          }
        }
        for (ExecutableRunTask job : InspectorMaster.getExecutableRunTaskList()) {
          if (runningList.size() >= runningArray.size()) {
            break;
          }
          if (runningArray.contains(job.getJobId())) {
            runningList.add(job);
            jobCache.put(job.getJobId(), job);
          }
        }
        return runningList;
      }
    }

    public Path getRemoteBaseDirectory() {
      return Paths.get(getStringFromProperty(KEY_REMOTE_DIRECTORY)).resolve(Executable.BASE);
    }

    public Path getRemoteJobsDirectory() {
      return getRemoteBaseDirectory().resolve(JOBS_PATH);
    }

    public Path getRemoteEntitiesDirectory() {
      return getRemoteBaseDirectory().resolve(ENTITIES_PATH);
    }

    public Path getRemoteAliveNotifierDirectory() {
      return getRemoteBaseDirectory().resolve(ALIVE_NOTIFIER_PATH);
    }

    public Path getRemoteLockoutFilePath() {
      return getRemoteBaseDirectory().resolve(LOCKOUT_FILE_PATH);
    }

    public static Path getDirectoryPath(JobManager jobManager, WaffleId id) {
      return jobManager.getPath().resolve(id.getReversedBase36Code());
    }

    public static Path getLocalDirectoryPath(JobManager jobManager, WaffleId id) {
      return Constants.WORK_DIR.relativize(getDirectoryPath(jobManager, id)).normalize();
    }

    /*
    public boolean checkStat(AbstractSubmitter submitter, AbstractJob job) throws RunNotFoundException {
      boolean finished = true;
      try {
        finished = !submitter.exists(getRemoteJobsDirectory().resolve(job.getId().toString()));
        if (finished) {
          removeFromArrayOfProperty(RUNNING, job.getJobId());
          jobCache.remove(job.getJobId());
        }
      } catch (FailedToControlRemoteException e) {
        ErrorLogMessage.issue(e);
      }
      return finished;
    }
     */
  }

  /*
  @Override
  public String getJvmActivationCommand() {
    return targetSubmitter.getJvmActivationCommand();
  }
   */

  @Override
  public AbstractSubmitter connect(boolean retry) {
    Computer targetComputer = Computer.getInstance(computer.getParameters().getString(KEY_TARGET_COMPUTER, ""));
    targetSubmitter = AbstractSubmitter.getInstance(Inspector.Mode.Normal, targetComputer).connect(retry);
    selfCommunicativeEnvelope = targetSubmitter.selfCommunicativeEnvelope;
    if (selfCommunicativeEnvelope != null) {
      selfCommunicativeEnvelope.setAdditionalEnvelopeConsumer(envelope -> processResponse(envelope));
    }
    return this;
  }

  @Override
  public boolean isConnected() {
    if (targetSubmitter == null) {
      return false;
    }
    return targetSubmitter.isConnected();
  }

  @Override
  public Path parseHomePath(String pathString) {
    return targetSubmitter.parseHomePath(pathString);
  }

  @Override
  public Path getAbsolutePath(Path path) throws FailedToControlRemoteException {
    return targetSubmitter.getWorkBaseDirectory().resolve(path);
  }

  @Override
  protected ServantScript getServantScript() {
    return new ServantScript(targetSubmitter.computer);
  }

  @Override
  public void createDirectories(Path path) throws FailedToControlRemoteException {
    targetSubmitter.createDirectories(path);
  }

  @Override
  boolean exists(Path path) throws FailedToControlRemoteException {
    return targetSubmitter.exists(path);
  }

  @Override
  boolean deleteFile(Path path) throws FailedToControlRemoteException {
    return targetSubmitter.deleteFile(path);
  }

  @Override
  public String exec(String command) throws FailedToControlRemoteException {
    return targetSubmitter.exec(command);
  }

  @Override
  protected RemoteProcess startProcess(String command) throws FailedToControlRemoteException {
    return targetSubmitter.startProcess(command);
  }

  @Override
  public void chmod(int mod, Path path) throws FailedToControlRemoteException {
    targetSubmitter.chmod(mod, path);
  }

  @Override
  public String getFileContents(ComputerTask run, Path path) throws FailedToTransferFileException {
    return targetSubmitter.getFileContents(run, path);
  }

  @Override
  public void transferFilesToRemote(Path localPath, Path remotePath) throws FailedToTransferFileException {
    targetSubmitter.transferFilesToRemote(localPath, remotePath);
  }

  @Override
  public void transferFilesFromRemote(Path remotePath, Path localPath, Boolean isDir) throws FailedToTransferFileException {
    targetSubmitter.transferFilesFromRemote(remotePath, localPath, isDir);
  }

  @Override
  public Path getWorkBaseDirectory() throws FailedToControlRemoteException {
    return targetSubmitter.getWorkBaseDirectory();
  }

  @Override
  public WrappedJson getDefaultParameters(Computer computer) {
    WrappedJson jsonObject = new WrappedJson();
    jsonObject.put(KEY_TARGET_COMPUTER, "LOCAL");
    jsonObject.put(KEY_EMPTY_TIMEOUT, 120);
    jsonObject.put(KEY_FORCE_SHUTDOWN, 21500);
    jsonObject.put(KEY_SHUTDOWN_PREPARATION_MARGIN, 3600);
    return jsonObject;
  }
}
