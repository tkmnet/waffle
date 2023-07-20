package jp.tkms.waffle.communicator;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonValue;
import jp.tkms.utils.abbreviation.Simple;
import jp.tkms.utils.concurrent.LockByKey;
import jp.tkms.waffle.Constants;
import jp.tkms.waffle.communicator.annotation.CommunicatorDescription;
import jp.tkms.waffle.communicator.process.RemoteProcess;
import jp.tkms.waffle.communicator.processor.ResponseProcessor;
import jp.tkms.waffle.communicator.util.SelfCommunicativeEnvelope;
import jp.tkms.waffle.data.internal.ServantScript;
import jp.tkms.waffle.data.util.*;
import jp.tkms.waffle.inspector.Inspector;
import jp.tkms.waffle.Main;
import jp.tkms.waffle.data.ComputerTask;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.internal.task.AbstractTask;
import jp.tkms.waffle.data.internal.task.ExecutableRunTask;
import jp.tkms.waffle.data.internal.task.SystemTask;
import jp.tkms.waffle.data.internal.task.SystemTaskStore;
import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.log.message.InfoLogMessage;
import jp.tkms.waffle.data.log.message.LogMessage;
import jp.tkms.waffle.data.log.message.WarnLogMessage;
import jp.tkms.waffle.data.project.executable.Executable;
import jp.tkms.waffle.data.project.workspace.run.ExecutableRun;
import jp.tkms.waffle.exception.*;
import jp.tkms.waffle.sub.servant.Envelope;
import jp.tkms.waffle.sub.servant.ExecKey;
import jp.tkms.waffle.sub.servant.TaskJson;
import jp.tkms.waffle.sub.servant.message.request.*;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@CommunicatorDescription("ABSTRACT SUBMITTER")
abstract public class AbstractSubmitter {
  protected static final String DOT_ENVELOPE = ".ENVELOPE";
  protected static final String TASK_JSON = "task.json";
  protected static final String RUN_DIR = "run";
  protected static final String BATCH_FILE = "batch.sh";
  protected static final String XSUB_TYPE = "XSUB_TYPE";
  static final int INITIAL_PREPARING = 100;
  static final int TIMEOUT = 120000; // 2min
  public static final String KEY_NAME = "name";
  public static final String KEY_LABEL = "label";
  public static final String KEY_TYPE = "type";
  public static final String KEY_CAST = "cast";
  public static final String KEY_DEFAULT = "default";
  public static final String KEY_PLACEHOLDER = "placeholder";
  public static final String KEY_MAX_JOBS = "maximum_jobs";
  public static final String KEY_MAX_THREADS = "maximum_threads";
  public static final String KEY_ALLOCABLE_MEMORY = "allocable_memory";

  boolean isRunning = false;
  Computer computer;
  private int pollingInterval = 5;
  private int preparingSize = INITIAL_PREPARING;
  private Inspector.Mode mode;


  ProcessorManager<PreparingProcessor> preparingProcessorManager = new ProcessorManager<>(PreparingProcessor::new);
  ProcessorManager<SubmittingProcessor> submittingProcessorManager = new ProcessorManager<>(SubmittingProcessor::new);
  ProcessorManager<FinishedProcessor> finishedProcessorManager = new ProcessorManager<>(FinishedProcessor::new);

  private boolean isBroken = false;
  private boolean isClosed = false;
  protected SelfCommunicativeEnvelope selfCommunicativeEnvelope = null;
  private AtomicLong remoteSyncedTime = new AtomicLong(-1);

  private static Path tempDirectoryPath = null;

  public int getPollingInterval() {
    return pollingInterval;
  }

  public void skipPolling() {
    pollingInterval = 0;
  }

  public Computer getComputer() {
    return computer;
  }

  abstract public AbstractSubmitter connect(boolean retry);
  abstract public boolean isConnected();

  abstract public WrappedJson getDefaultParameters(Computer computer);

  abstract public Path parseHomePath(String pathString);

  abstract public Path getAbsolutePath(Path path) throws FailedToControlRemoteException;
  abstract public void createDirectories(Path path) throws FailedToControlRemoteException;
  abstract boolean exists(Path path) throws FailedToControlRemoteException;
  abstract boolean deleteFile(Path path) throws FailedToControlRemoteException;
  abstract public String exec(String command) throws FailedToControlRemoteException;
  abstract protected RemoteProcess startProcess(String command) throws FailedToControlRemoteException;
  abstract public void chmod(int mod, Path path) throws FailedToControlRemoteException;
  abstract public String getFileContents(ComputerTask run, Path path) throws FailedToTransferFileException;
  abstract public void transferFilesToRemote(Path localPath, Path remotePath) throws FailedToTransferFileException;
  abstract public void transferFilesFromRemote(Path remotePath, Path localPath, Boolean isDir) throws FailedToTransferFileException;

  public AbstractSubmitter(Computer computer) {
    this.computer = computer;
  }

  public AbstractSubmitter connect() {
    return connect(true);
  }

  public WrappedJsonArray getFormSettings() {
    WrappedJsonArray settings = new WrappedJsonArray("[]");
    {
      WrappedJson entry = new WrappedJson();
      entry.put(KEY_NAME, "work_base_dir");
      entry.put(KEY_LABEL, "Work base directory on the computer");
      entry.put(KEY_TYPE, "text");
      entry.put(KEY_DEFAULT, "/tmp/waffle");
      settings.add(entry);
    }
    {
      WrappedJson entry = new WrappedJson();
      entry.put(KEY_NAME, "polling_interval");
      entry.put(KEY_LABEL, "Polling interval (seconds)");
      entry.put(KEY_TYPE, "number");
      entry.put(KEY_CAST, "Integer");
      entry.put(KEY_DEFAULT, 10);
      settings.add(entry);
    }
    return settings;
  }

  public void close() {
    synchronized (this) {
      if (isClosed) {
        return;
      }

      try {
        Simple.waitUntil(() -> isRunning, TimeUnit.MILLISECONDS, 500);
      } catch (InterruptedException e) {
        ErrorLogMessage.issue(e);
      }

      if (isStreamMode()) {
        selfCommunicativeEnvelope.close();
      }

      preparingProcessorManager.close();
      submittingProcessorManager.close();
      finishedProcessorManager.close();

      isClosed = true;
    }
  }

  protected void switchToStreamMode() {
    try {
      RemoteProcess remoteProcess = startProcess(getServantCommand(this, null));
      selfCommunicativeEnvelope = new SelfCommunicativeEnvelope(Constants.WORK_DIR, remoteProcess, this);
    } catch (FailedToControlRemoteException e) {
      WarnLogMessage.issue(e);
    }
  }

  public Envelope getNextEnvelope() {
    if (isStreamMode()) {
      if (selfCommunicativeEnvelope.isClosed()) {
        brake();
      }
      return selfCommunicativeEnvelope;
    }
    return new Envelope(Constants.WORK_DIR);
  }

  private void brake() {
    if (!isBroken) {
      new Thread(() -> {
        isBroken = true;
        close();
      }).start();
    }
  }

  public void setRemoteSyncedTime(long value) {
    remoteSyncedTime.set(value);
  }

  public static AbstractSubmitter getInstance(Inspector.Mode mode, Computer computer) {
    AbstractSubmitter submitter = null;
    try {
      Class<?> clazz = Class.forName(computer.getSubmitterType());
      Constructor<?> constructor = clazz.getConstructor(Computer.class);
      submitter = (AbstractSubmitter) constructor.newInstance(new Object[]{computer});
    }catch(Exception e) {
      ErrorLogMessage.issue(e);
    }

    if (submitter == null) {
      submitter = new JobNumberLimitedSshSubmitter(computer);
    }

    submitter.mode = mode;

    return submitter;
  }

  public void transferFilesFromRemote(Path remotePath, Path localPath) throws FailedToTransferFileException {
    transferFilesFromRemote(remotePath, localPath, null);
  }

  private static Path getTempDirectoryPath() throws IOException {
    if (tempDirectoryPath == null) {
      synchronized (AbstractSubmitter.class) {
        if (tempDirectoryPath == null) {
          tempDirectoryPath = Files.createTempDirectory(Constants.APP_NAME);
          tempDirectoryPath.toFile().deleteOnExit();
        }
      }
    }
    return tempDirectoryPath;
  }

  private static String getServantCommand(AbstractSubmitter submitter, Path remoteEnvelopePath) throws FailedToControlRemoteException {
    int timeout = 100;
    {
      JsonValue value = (JsonValue) submitter.getComputer().getParameter(Computer.KEY_POLLING, submitter);
      if (value != null && value.isNumber()) {
        timeout = value.asInt() * 10;
      }
    }
    return XSUB_TYPE + "=" + submitter.getComputer().getXsubType() + " sh '" + submitter.getAbsolutePath(submitter.getServantScript().getScriptPath()) + "' main '" + (remoteEnvelopePath == null ? "-" : remoteEnvelopePath) + "' " + timeout;
  }

  protected static Envelope sendAndReceiveEnvelope(AbstractSubmitter submitter, Envelope envelope) throws Exception {
    if (envelope instanceof SelfCommunicativeEnvelope) {
      submitter.syncServantProcess();
    } else if (!envelope.isEmpty()) {
      Path tmpFile = getTempDirectoryPath().resolve(UUID.randomUUID().toString());
      Path remoteEnvelopePath = submitter.getRemoteWorkBasePath().resolve(DOT_ENVELOPE).resolve(tmpFile.getFileName());
      Files.createDirectories(tmpFile.getParent());

      envelope.save(tmpFile);
      submitter.createDirectories(remoteEnvelopePath.getParent());
      submitter.transferFilesToRemote(tmpFile, remoteEnvelopePath);
      Files.delete(tmpFile);

      String message = submitter.exec(getServantCommand(submitter, remoteEnvelopePath)).trim();
      if (!"".equals(message)) {
        InfoLogMessage.issue("REMOTE(SERVANT)> " + message);
      }
      Path remoteResponsePath = Envelope.getResponsePath(remoteEnvelopePath);
      try {
        submitter.transferFilesFromRemote(remoteResponsePath, tmpFile, false);
      } catch (FailedToTransferFileException e) {
        if (submitter.exists(remoteResponsePath)) {
          try {
            submitter.transferFilesFromRemote(remoteResponsePath, tmpFile, false);
          } catch (FailedToTransferFileException ex) {
            InfoLogMessage.issue(submitter.computer, "Servant does not respond (comm) : " + remoteResponsePath.getFileName());
          }
        } else {
          InfoLogMessage.issue(submitter.computer, "Servant does not respond (exec) : " + remoteResponsePath.getFileName());
          return null;
        }
      }
      submitter.deleteFile(remoteResponsePath);
      Envelope response = Envelope.loadAndExtract(Constants.WORK_DIR, tmpFile);
      Files.delete(tmpFile);
      return response;
    }

    return null;
  }

  private void syncServantProcess() {
    long syncStartTime = System.currentTimeMillis();
    try {
      do {
        long localTime = System.currentTimeMillis();
        SelfCommunicativeEnvelope envelope = (SelfCommunicativeEnvelope) getNextEnvelope();
        envelope.add(new SyncRequestMessage(localTime));
        envelope.flush();
        do {
          TimeUnit.MILLISECONDS.sleep(50);
        } while (!(remoteSyncedTime.get() >= localTime || System.currentTimeMillis() >= localTime + (TIMEOUT / 10)));
      } while (!(remoteSyncedTime.get() >= syncStartTime || System.currentTimeMillis() >= syncStartTime + TIMEOUT));
    } catch (InterruptedException e) {
      ErrorLogMessage.issue(e);
    }
  }

  public static String getSanitizedEnvironments(Computer computer) {
    String environments = "";
    for (Object key : computer.getEnvironments().keySet()) {
      String name = key.toString().trim().replace("\"", "\\\"").replaceAll(" |#|;", "_");
      String value = computer.getEnvironments().getString(name, "\"\"").replace("\\", "\\\\").replace("\"", "\\\"");
      environments += name + "=\"" + value + "\" ";
    }
    return environments;
  }

  public Envelope sendAndReceiveEnvelope(Envelope envelope) throws Exception {
    return sendAndReceiveEnvelope(this, envelope);
  }

  public void submit(Envelope envelope, AbstractTask job) throws RunNotFoundException {
    try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
      if (job.getState().equals(State.Submitted)) return;
      InfoLogMessage.issue(job.getRun(), "will be submitted");
      if (job.getState().equals(State.Created)) {
        return;
      }
      envelope.add(new SubmitJobMessage(job.getTypeCode(), job.getHexCode(), getRunDirectory(job.getRun()), job.getRun().getRemoteBinPath(), BATCH_FILE, computer.getXsubParameters().toString()));
      job.setState(State.Submitted);
    } catch (FailedToControlRemoteException e) {
      WarnLogMessage.issue(job.getComputer(), e.getMessage());
      job.setState(State.Excepted);
    } catch (Exception e) {
      WarnLogMessage.issue(e);
      job.setState(State.Excepted);
    }
  }

  public void checkJobId(Envelope envelope, AbstractTask job) throws RunNotFoundException, FailedToControlRemoteException {
    envelope.add(new CheckJobIdMessage(job.getTypeCode(), job.getHexCode(), job.getJobId(), getRunDirectory(job.getRun())));
  }

  public void update(Envelope envelope, AbstractTask job) throws RunNotFoundException, FailedToControlRemoteException {
    envelope.add(new CollectStatusMessage(job.getTypeCode(), job.getHexCode(), job.getJobId(), getRunDirectory(job.getRun())));
  }

  public void cancel(Envelope envelope, AbstractTask job) throws RunNotFoundException, FailedToControlRemoteException {
    if (! job.getJobId().equals("-1")) {
      envelope.add(new CancelJobMessage(job.getTypeCode(), job.getHexCode(), job.getJobId(), getRunDirectory(job.getRun())));
    } else {
      if (job.getState().equals(State.Cancel)) {
        job.setState(State.Canceled);
      } else {
        job.setState(State.Aborted);
      }
    }
  }

  protected void prepareJob(Envelope envelope, AbstractTask job) throws RunNotFoundException, FailedToControlRemoteException,FailedToTransferFileException {
    synchronized (job) {
      if (job.getState().equals(State.Created)) {
        ComputerTask run = job.getRun();
        run.setRemoteWorkingDirectoryLog(getRunDirectory(run).toString());

        String projectName = (run instanceof ExecutableRun ? ((ExecutableRun)run).getProject().getName() : ".SYSTEM_TASK");
        String workspaceName = (run instanceof ExecutableRun ? ((ExecutableRun)run).getWorkspace().getName() : ".SYSTEM_TASK");
        String executableName = (run instanceof ExecutableRun ? ((ExecutableRun)run).getExecutable().getName() : ".SYSTEM_TASK");

        run.specializedPreProcess(this);

        JsonArray arguments = new JsonArray();
        for (Object object : run.getArguments()) {
          arguments.add(object.toString());
        }
        WrappedJson environments = new WrappedJson();
        environments.merge(run.getActualComputer().getEnvironments());
        environments.merge(run.getEnvironments());

        ExecKey execKey = new ExecKey();
        Path remoteBinPath = run.getRemoteBinPath();
        TaskJson taskJson = new TaskJson(projectName, workspaceName, executableName,
          remoteBinPath == null ? null : remoteBinPath.toString(),
          run.getCommand(), arguments, environments.toJsonObject(),
          execKey
        );
        //putText(job, TASK_JSON, taskJson.toString());
        envelope.add(new PutTextFileMessage(run.getLocalPath().resolve(TASK_JSON), taskJson.toString()));
        envelope.add(new PutTextFileMessage(run.getLocalPath().resolve(jp.tkms.waffle.sub.servant.Constants.EXEC_KEY), execKey.toString()));

        /*
        String jvmActivationCommand = getJvmActivationCommand().replaceAll("\"", "\\\"");
        if (!jvmActivationCommand.trim().equals("")) {
          jvmActivationCommand += ";";
        }
         */

        //String wsp = getAbsolutePath(getServantScript().getJarPath()).toString();
        //putText(job, BATCH_FILE, "java -jar '" + getWaffleServantPath(this, computer) + "' '" + parseHomePath(computer.getWorkBaseDirectory()) + "' exec '" + getRunDirectory(job.getRun()).resolve(TASK_JSON) + "'");
        //envelope.add(new PutTextFileMessage(run.getLocalPath().resolve(BATCH_FILE), "sh -c \"" + jvmActivationCommand + "java -mx100m -XX:+UseSerialGC -jar '" + wsp + "' '" + getWorkBaseDirectory() + "' exec '" + getRunDirectory(job.getRun()).resolve(TASK_JSON) + "'\""));
        //+ "' '" + parseHomePath(computer.getWorkBaseDirectory()) + "' exec '" + getRunDirectory(job.getRun()).resolve(TASK_JSON) + "'"));
        envelope.add(new PutTextFileMessage(run.getLocalPath().resolve(BATCH_FILE), "sh '" + getWaffleServantPath() + "' exec '" + getRunDirectory(job.getRun()).resolve(TASK_JSON) + "'"));

        envelope.add(run.getBasePath());

        Path remoteExecutableBaseDirectory = getExecutableBaseDirectory(job);
        synchronized (envelope) {
          Path binPath = run.getBinPath();
          if (binPath != null && !envelope.exists(binPath)) {
            if (remoteExecutableBaseDirectory != null && !exists(remoteExecutableBaseDirectory.toAbsolutePath())) {
              envelope.add(binPath);
              envelope.add(new ChangePermissionMessage(remoteExecutableBaseDirectory.resolve(Executable.BASE), "a-w"));
            }
          }
        }

        //job.setState(State.Prepared);
        //InfoLogMessage.issue(job.getRun(), "was prepared");
        envelope.add(new ConfirmPreparingMessage(job.getTypeCode(), job.getHexCode()));
      }
    }
  }

  public Path getWorkBaseDirectory() throws FailedToControlRemoteException {
    return parseHomePath(computer.getParameter(Computer.KEY_WORKBASE, this).toString());
  }

  private Path getRemoteWorkBasePath() {
    return parseHomePath(computer.getParameter(Computer.KEY_WORKBASE, this).toString());
  }

  public Path getBaseDirectory(ComputerTask run) throws FailedToControlRemoteException {
    return getRunDirectory(run).resolve(Executable.BASE);
  }

  public Path getRunDirectory(ComputerTask run) throws FailedToControlRemoteException {
    //Computer computer = run.getActualComputer();
    //Path path = parseHomePath(computer.getWorkBaseDirectory()).resolve(run.getLocalDirectoryPath());
    Path path = parseHomePath(getWorkBaseDirectory().toString()).resolve(run.getLocalPath());
    //createDirectories(path);
    return path;
  }

  Path getExecutableBaseDirectory(AbstractTask job) throws FailedToControlRemoteException, RunNotFoundException {
    Path remoteBinPath = job.getRun().getRemoteBinPath();
    if (remoteBinPath == null) {
      return null;
    } else {
      return getWorkBaseDirectory().resolve(remoteBinPath);
    }
  }

  void jobFinalizing(AbstractTask job) throws WaffleException {
    try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
      int exitStatus = job.getRun().getExitStatus();

      if (exitStatus == 0) {
        InfoLogMessage.issue(job.getRun(), "results will be collected");

        boolean isNoException = true;
        try {
          job.getRun().specializedPostProcess(this, job);
        } catch (Exception e) {
          isNoException = false;
          job.setState(State.Excepted);
          job.getRun().appendErrorNote(LogMessage.getStackTrace(e));
          WarnLogMessage.issue(e);
        }

        if (isNoException) {
          job.setState(State.Finished);
        }
      } else {
        job.setState(State.Failed);
      }

      ComputerTask run = job.getRun();
      if (run instanceof ExecutableRun) {
        switch (run.getState()) {
          case Failed:
          case Excepted:
            ((ExecutableRun) run).tryAutomaticRetry();
        }
      }

      job.remove();
    } catch (Exception e) {
      try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
        job.getRun().appendErrorNote(LogMessage.getStackTrace(e));
      } catch (RunNotFoundException runNotFoundException) {
        ErrorLogMessage.issue(e);
        return;
      }
      ErrorLogMessage.issue(e);
    }
  }

  Path getContentsPath(ComputerTask run, Path path) throws FailedToControlRemoteException {
    if (path.isAbsolute()) {
      return path;
    }
    return getBaseDirectory(run).resolve(path);
  }

  public Path getWaffleServantPath() throws FailedToControlRemoteException {
    //return submitter.parseHomePath(computer.getWorkBaseDirectory()).resolve(ServantJarFile.JAR_FILE);
    return getAbsolutePath(getServantScript().getScriptPath());
  }

  protected ServantScript getServantScript() {
    return new ServantScript(computer);
  }

  public static boolean checkWaffleServant(Computer computer, boolean retry) throws RuntimeException, WaffleException {
    AbstractSubmitter submitter = getInstance(Inspector.Mode.Normal, computer);
    if (submitter instanceof AbstractSubmitterWrapper) {
      return false;
    }
    submitter.connect(retry);
    ServantScript servantScript = submitter.getServantScript();
    boolean result = false;

    //if (submitter.exists(remoteServantPath)) {
    //String remoteHash = submitter.exec("md5sum '" + remoteServantPath + "'  | sed -e 's/ .*//'").trim(); //TODO:
    String remoteHash = submitter.exec("sh \"" + submitter.getAbsolutePath(servantScript.getScriptPath()).toString() + "\" version").trim(); //TODO:
    //String localHash = ServantJarFile.getMD5Sum().trim();
    String localHash = Main.VERSION;
    if (localHash.equals(remoteHash)) {
      result = true;
    }
    //}

    if (!result) {
      submitter.createDirectories(submitter.getAbsolutePath(servantScript.getScriptPath().getParent()));
      submitter.transferFilesToRemote(servantScript.generate(), submitter.getAbsolutePath(servantScript.getScriptPath()));
      submitter.transferFilesToRemote(servantScript.getJre(), submitter.getAbsolutePath(servantScript.getJrePath()));
      submitter.transferFilesToRemote(servantScript.getJar(), submitter.getAbsolutePath(servantScript.getJarPath()));
      result = submitter.exists(submitter.getAbsolutePath(servantScript.getScriptPath()));
    }
    submitter.close();
    return result;
  }

  public static void updateXsubTemplate(Computer computer, boolean retry) throws RuntimeException, WaffleException {
    AbstractSubmitter submitter = getInstance(Inspector.Mode.Normal, computer);
    if (submitter instanceof AbstractSubmitterWrapper) {
      AbstractSubmitterWrapper wrapper = (AbstractSubmitterWrapper)submitter;
      if (wrapper.checkTargets()) {
        computer.setState(ComputerState.Viable);
      } else {
        computer.setState(ComputerState.Unviable);
      }
    } else {
      submitter.connect(retry);
      Envelope request = submitter.getNextEnvelope();
      request.add(new SendXsubTemplateMessage(computer.getName(), computer.getXsubType()));
      submitter.processRequestAndResponse(request);
      submitter.close();
    }
  }

  public static WrappedJson getParameters(Computer computer) {
    AbstractSubmitter submitter = getInstance(Inspector.Mode.Normal, computer);
    WrappedJson jsonObject = submitter.getDefaultParameters(computer);
    return jsonObject;
  }

  protected final boolean isSubmittable(Computer computer, ComputerTask next) {
    return isSubmittable(computer, next, getJobList(Inspector.Mode.Normal, computer), getJobList(Inspector.Mode.System, computer));
  }

  protected final boolean isSubmittable(Computer computer, ComputerTask next, ArrayList<AbstractTask>... lists) {
    ArrayList<ComputerTask> combinedList = new ArrayList<>();
    for (ArrayList<AbstractTask> list : lists) {
      for (AbstractTask job : list) {
        try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
          combinedList.add(job.getRun());
        } catch (RunNotFoundException e) {
          //NOP
        }
      }
    }
    return isSubmittable(computer, next, combinedList);
  }

  protected boolean isSubmittable(Computer computer, ComputerTask next, ArrayList<ComputerTask> list) {
    return false;
  }

  protected static ArrayList<AbstractTask> getJobList(Inspector.Mode mode, Computer computer) {
    if (mode.equals(Inspector.Mode.Normal)) {
      return ExecutableRunTask.getList(computer);
    } else {
      return SystemTask.getList(computer);
    }
  }

  public static AbstractTask findJobFromStore(byte type, String id) {
    WaffleId targetId = WaffleId.valueOf(id);
    if (type == SystemTaskStore.TYPE_CODE) {
      for (SystemTask job : SystemTask.getList()) {
        if (job.getId().equals(targetId)) {
          return job;
        }
      }
    } else {
      for (ExecutableRunTask job : ExecutableRunTask.getList()) {
        if (job.getId().equals(targetId)) {
          return job;
        }
      }
    }
    return null;
  }

  protected Envelope processRequestAndResponse(Envelope envelope) {
    Envelope response = null;
    try {
      response = processResponse(sendAndReceiveEnvelope(envelope));
    } catch (Exception e) {
      InfoLogMessage.issue(computer, "Communication was failed: " + e.getMessage());
    }

    return response;
  }


  public Envelope processResponse(Envelope response) {
    try {
      if (response == null || response.getMessageBundle().isEmpty()) {
        return null;
      }
      ResponseProcessor.processMessages(this, response);
    } catch (Exception e) {
      InfoLogMessage.issue(computer, "Communication was failed: " + e.getMessage());
    }
    return response;
  }

  public void checkSubmitted() throws FailedToControlRemoteException {
    try {
      isRunning = true;
      Envelope envelope = getNextEnvelope();
      pollingInterval = 10;
      {
        JsonValue value = (JsonValue) getComputer().getParameter(Computer.KEY_POLLING, this);
        if (value != null && value.isNumber()) {
          pollingInterval = value.asInt();
        }
      }

      startupPreparingProcessorManager();
      startupSubmittingProcessorManager();
      startupFinishedProcessorManager();

      ArrayList<AbstractTask> submittedJobList = new ArrayList<>();
      ArrayList<AbstractTask> runningJobList = new ArrayList<>();
      ArrayList<AbstractTask> cancelJobList = new ArrayList<>();

      for (AbstractTask job : new ArrayList<>(getJobList(mode, computer))) {
        if (Main.hibernatingFlag || isBroken) {
          break;
        }

        try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
          if (!job.exists() && job.getRun().isRunning()) {
            job.abort();
            WarnLogMessage.issue(job.getRun(), "The task file is not exists; The task will cancel.");
            continue;
          }
          switch (job.getState(true)) {
            case Submitted:
              submittedJobList.add(job);
              break;
            case Running:
              runningJobList.add(job);
              break;
            case Abort:
            case Cancel:
              cancelJobList.add(job);
          }
        } catch (RunNotFoundException e) {
          try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
            cancel(envelope, job);
          } catch (RunNotFoundException ex) {
          }
          job.remove();
          WarnLogMessage.issue("ExecutableRun(" + job.getId() + ") is not found; The task was removed.");
        }
      }

      if (!(Main.hibernatingFlag || isBroken)) {
        processSubmitted(envelope, submittedJobList, runningJobList, cancelJobList);
      }

      if (!(Main.hibernatingFlag || isBroken)) {
        startupSubmittingProcessorManager();
      }

      isRunning = false;

      if (!isBroken) {
        processRequestAndResponse(envelope);
      }
      return;
    } catch (FailedToControlRemoteException e) {
      isRunning = false;
      throw e;
    }
  }

  public void processSubmitted(Envelope envelope, ArrayList<AbstractTask> submittedJobList, ArrayList<AbstractTask> runningJobList, ArrayList<AbstractTask> cancelJobList) throws FailedToControlRemoteException {
    for (AbstractTask job : cancelJobList) {
      if (Main.hibernatingFlag || isBroken) { return; }
      try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
        cancel(envelope, job);
      } catch (RunNotFoundException e) {
        job.remove();
      }
    }

    for (AbstractTask job : submittedJobList) {
      if (Main.hibernatingFlag || isBroken) { return; }
      try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
        checkJobId(envelope, job);
      } catch (RunNotFoundException e) {
        job.remove();
        WarnLogMessage.issue("ExecutableRun(" + job.getId() + ") is not found; The task was removed." );
      }
    }

    for (AbstractTask job : runningJobList) {
      if (Main.hibernatingFlag || isBroken) { return; }
      try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
        update(envelope, job);
      } catch (RunNotFoundException e) {
        job.remove();
        WarnLogMessage.issue("ExecutableRun(" + job.getId() + ") is not found; The task was removed." );
      }
    }
  }

  public void startupFinishedProcessorManager() {
    finishedProcessorManager.startup();
  }

  public void startupSubmittingProcessorManager() {
    submittingProcessorManager.startup();
  }

  public void startupPreparingProcessorManager() {
    preparingProcessorManager.startup();
  }

  public boolean isClosed() {
    return isClosed;
  }

  private boolean isStreamMode() {
    return selfCommunicativeEnvelope != null;
  }

  class PreparingProcessor extends Thread {

    public PreparingProcessor() {
      super("PreparingProcessor");
    }

    @Override
    public void run() {
      Envelope envelope = getNextEnvelope();

      ArrayList<AbstractTask> createdJobList = new ArrayList<>();
      ArrayList<AbstractTask> preparedJobList = new ArrayList<>();

      for (AbstractTask job : new ArrayList<>(getJobList(mode, computer))) {
        if (Main.hibernatingFlag || isBroken) { return; }

        try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
          if (!job.exists() && job.getRun().isRunning()) {
            job.abort();
            WarnLogMessage.issue(job.getRun(), "The task file is not exists; The task will cancel.");
            continue;
          }
          switch (job.getState(true)) {
            case Created:
              createdJobList.add(job);
              break;
            case Prepared:
              preparedJobList.add(job);
              break;
          }
        } catch (RunNotFoundException e) {
          try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
            cancel(envelope, job);
          } catch (RunNotFoundException | FailedToControlRemoteException ex) { }
          job.remove();
          WarnLogMessage.issue("ExecutableRun(" + job.getId() + ") is not found; The task was removed." );
        }
      }

      boolean isRemained = true;
      while (isRemained) {
        if (!(Main.hibernatingFlag || isBroken)) {
          try {
            isRemained = !processPreparing(envelope, createdJobList, preparedJobList);
          } catch (FailedToControlRemoteException e) {
            ErrorLogMessage.issue(e);
          }
        }

        if (!isBroken) {
          processRequestAndResponse(envelope);
        }

        if (Main.hibernatingFlag || isBroken) { break; }
        if (isRemained) {
          envelope = getNextEnvelope();
        }
      }
    }
  }

  public boolean processPreparing(Envelope envelope, ArrayList<AbstractTask> createdJobList, ArrayList<AbstractTask> preparedJobList) throws FailedToControlRemoteException {
    int nextPreparing = INITIAL_PREPARING - preparedJobList.size();
    int triedCount = 0;
    ArrayList<AbstractTask> queuedJobList = new ArrayList<>(createdJobList);
    for (AbstractTask job : queuedJobList) {
      if (Main.hibernatingFlag || isBroken) { return true; }
      if (triedCount >= nextPreparing ) { return true; }

      try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
        prepareJob(envelope, job);
        createdJobList.remove(job);
        preparedJobList.add(job);
      } catch (Exception e) {
        ErrorLogMessage.issue(e);
      }

      if (triedCount % 10 == 9) {
        if (isStreamMode()) {
          ((SelfCommunicativeEnvelope) envelope).flush();
        } else {
          return false;
        }
      }

      triedCount += 1;
    }

    return true;
  }

  class SubmittingProcessor extends Thread {

    public SubmittingProcessor() {
      super("SubmittingProcessor");
    }

    @Override
    public void run() {
      Envelope envelope = getNextEnvelope();

      ArrayList<AbstractTask> submittedJobList = new ArrayList<>();
      ArrayList<AbstractTask> preparedJobList = new ArrayList<>();
      ArrayList<AbstractTask> retryingJobList = new ArrayList<>();

      for (AbstractTask job : new ArrayList<>(getJobList(mode, computer))) {
        if (Main.hibernatingFlag || isBroken) { return; }

        try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
          if (!job.exists() && job.getRun().isRunning()) {
            job.abort();
            WarnLogMessage.issue(job.getRun(), "The task file is not exists; The task will cancel.");
            continue;
          }
          switch (job.getState(true)) {
            case Retrying:
              retryingJobList.add(job);
              break;
            case Prepared:
              preparedJobList.add(job);
              break;
            case Submitted:
            case Running:
            case Abort:
            case Cancel:
              submittedJobList.add(job);
          }
        } catch (RunNotFoundException e) {
          try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
            cancel(envelope, job);
          } catch (RunNotFoundException | FailedToControlRemoteException ex) { }
          job.remove();
          WarnLogMessage.issue("ExecutableRun(" + job.getId() + ") is not found; The task was removed." );
        }
      }

      boolean isRemained = true;
      while (isRemained) {
        if (!(Main.hibernatingFlag || isBroken)) {
          try {
            isRemained = !processSubmitting(envelope, submittedJobList, preparedJobList);
          } catch (FailedToControlRemoteException e) {
            ErrorLogMessage.issue(e);
          }
        }

        if (!isBroken) {
          processRequestAndResponse(envelope);
        }

        if (Main.hibernatingFlag || isBroken) { break; }
        if (isRemained) {
          envelope = getNextEnvelope();
        }
      }

      for (AbstractTask task : retryingJobList) {
        try {
          if (computer.getName().equals(task.getComputerName())) {
            task.setState(jp.tkms.waffle.data.util.State.Created);
          }
        } catch (Exception e) {
          WarnLogMessage.issue(e);
        }
      }
    }
  }

  public boolean processSubmitting(Envelope envelope, ArrayList<AbstractTask> submittedJobList, ArrayList<AbstractTask> preparedJobList) throws FailedToControlRemoteException {
    Executable skippedExecutable = null;
    HashSet<String> lockedExecutableNameSet = new HashSet<>();

    ArrayList<AbstractTask> queuedJobList = new ArrayList<>(preparedJobList);
    for (AbstractTask job : queuedJobList) {
      if (Main.hibernatingFlag || isBroken) { return true; }

      try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
        ComputerTask run = job.getRun();

        if (skippedExecutable != null && run instanceof ExecutableRun) {
          if (((ExecutableRun) run).getExecutable() == skippedExecutable) {
            continue;
          }
        }

        boolean isSubmittable = isSubmittable(computer, run, submittedJobList);

        if (isSubmittable) {
          if (run instanceof ExecutableRun) {
            ExecutableRun executableRun = (ExecutableRun) run;
            String executableName = executableRun.getExecutable().getName();
            if (executableRun.getExecutable().isParallelProhibited()) {
              if (lockedExecutableNameSet.contains(executableName) || isRunningSameExecutableInWorkspace(executableRun)) {
                isSubmittable = false;
              }
              lockedExecutableNameSet.add(executableName);
            }
          }
        }

        if (isSubmittable) {
          skippedExecutable = null;
          submit(envelope, job);
          preparedJobList.remove(job);
          submittedJobList.add(job);
          startupPreparingProcessorManager();
        } else {
          if (run instanceof ExecutableRun) {
            skippedExecutable = ((ExecutableRun) run).getExecutable();
          }
        }
      } catch (NullPointerException | WaffleException e) {
        WarnLogMessage.issue(e);
        try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
          job.setState(State.Excepted);
        } catch (RunNotFoundException ex) { }
        throw new FailedToControlRemoteException(e);
      }
    }

    return true;
  }

  private boolean isRunningSameExecutableInWorkspace(ExecutableRun executableRun) {
    if (mode.equals(Inspector.Mode.Normal)) {
      for (ExecutableRunTask job : ExecutableRunTask.getList()) {
        try {
          ComputerTask computerTask = job.getRun();
          if (computerTask instanceof ExecutableRun) {
            ExecutableRun run = (ExecutableRun) computerTask;
            if (!run.getWorkspace().getLocalPath().equals(executableRun.getWorkspace().getLocalPath())) {
              continue;
            }
            if (!run.getExecutable().getName().equals(executableRun.getExecutable().getName())) {
              continue;
            }
            try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
              switch (job.getState()) {
                case Submitted:
                case Running:
                case Finalizing:
                  return true;
              }
            }
          }
        } catch (RunNotFoundException e) {
          //NOP
        }
      }
    }
    return false;
  }

  class FinishedProcessor extends Thread {
    public FinishedProcessor() {
      super("FinishedProcessor");
    }

    @Override
    public void run() {
      ArrayList<AbstractTask> finalizingJobList = new ArrayList<>();

      do {
        if (Main.hibernatingFlag || isBroken) { return; }

        Envelope envelope = getNextEnvelope();

        finalizingJobList.clear();

        for (AbstractTask job : new ArrayList<>(getJobList(mode, computer))) {
          if (Main.hibernatingFlag || isBroken) { return; }

          try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
            ComputerTask run = job.getRun();

            if (!job.exists() && run.isRunning()) {
              job.abort();
              WarnLogMessage.issue(run, "The task file is not exists; The task will cancel.");
              continue;
            }
            switch (job.getState(true)) {
              case Finalizing:
                finalizingJobList.add(job);
                break;
              case Finished:
              case Failed:
              case Excepted:
                //WarnLogMessage.issue("ExecutableRun(" + job.getId() + ") is not running; The task was removed." );
              case Aborted:
              case Canceled:
                job.remove();
            }
          } catch (RunNotFoundException e) {
            try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
              cancel(envelope, job);
            } catch (RunNotFoundException | FailedToControlRemoteException ex) { }
            job.remove();
            WarnLogMessage.issue("ExecutableRun(" + job.getId() + ") is not found; The task was removed." );
          }
        }

        processRequestAndResponse(envelope);

        if (!finalizingJobList.isEmpty()) {
          try {
            processFinished(finalizingJobList);
          } catch (FailedToControlRemoteException e) {
            ErrorLogMessage.issue(e);
          }
        }

      } while (!finalizingJobList.isEmpty());
      return;
    }
  }

  public void processFinished(ArrayList<AbstractTask> finalizingJobList) throws FailedToControlRemoteException {
    for (AbstractTask job : finalizingJobList) {
      if (Main.hibernatingFlag || isBroken) { return; }

      try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
        jobFinalizing(job);
      } catch (WaffleException e) {
        WarnLogMessage.issue(e);
        try (LockByKey lock = LockByKey.acquire(job.getHexCode())) {
          job.setState(State.Excepted);
        } catch (RunNotFoundException ex) { }
        throw new FailedToControlRemoteException(e);
      }
    }
  }

  static class ProcessorManager<P extends Thread> {
    Supplier<P> supplier;
    P processor;

    ProcessorManager(Supplier<P> supplier) {
      this.supplier = supplier;
      processor = null;
    }

    protected void startup() {
      if (processor == null || !processor.isAlive()) {
        synchronized (this) {
          if (processor == null || !processor.isAlive()) {
            processor = supplier.get();
            processor.start();
          }
        }
      }
    }

    protected void close() {
      if (processor != null && processor.isAlive()) {
        int count = 0;
        while (processor.isAlive()) {
          try {
            TimeUnit.MILLISECONDS.sleep(500);
          } catch (InterruptedException e) {
            ErrorLogMessage.issue(e);
          }
          if (count++ == 20) {
            InfoLogMessage.issue("Submitter is closing; wait few minutes.");
          }
        }
      }
    }
  }
}
