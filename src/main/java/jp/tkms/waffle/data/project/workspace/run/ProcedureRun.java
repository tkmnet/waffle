package jp.tkms.waffle.data.project.workspace.run;

import jp.tkms.utils.debug.DebugElapsedTime;
import jp.tkms.waffle.Constants;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.internal.guard.ValueGuard;
import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.log.message.InfoLogMessage;
import jp.tkms.waffle.data.project.conductor.Conductor;
import jp.tkms.waffle.data.project.executable.Executable;
import jp.tkms.waffle.data.project.workspace.HasLocalPath;
import jp.tkms.waffle.data.project.workspace.Registry;
import jp.tkms.waffle.data.project.workspace.Workspace;
import jp.tkms.waffle.data.project.workspace.archive.ArchivedConductor;
import jp.tkms.waffle.data.util.*;
import jp.tkms.waffle.exception.ChildProcedureNotFoundException;
import jp.tkms.waffle.exception.RunNotFoundException;
import jp.tkms.waffle.manager.ManagerMaster;
import jp.tkms.waffle.script.ScriptProcessor;
import jp.tkms.waffle.web.Key;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;

public class ProcedureRun extends AbstractRun {
  //public static final String PROCEDURE_RUN = "PROCEDURE_RUN";
  public static final String PROCEDURE_RUN = ".PROCEDURE_RUN";
  public static final String KEY_CONDUCTOR = "conductor";
  public static final String KEY_PROCEDURE_NAME = "procedure_name";
  public static final String KEY_WORKING_DIRECTORY = "working_directory";
  public static final String KEY_GUARD = "guard";
  public static final String KEY_SUCCESS = "success";
  public static final String KEY_ACTIVE_GUARD = "active_guard";
  public static final String KEY_REFERABLE = "referable";

  private ArchivedConductor conductor;
  private String procedureName;
  private Registry registry;
  private Path workingDirectory;
  private StringKeyHashMap<HasLocalPath> referableMap;

  private static final InstanceCache<String, ProcedureRun> instanceCache = new InstanceCache<>();

  public ProcedureRun(Workspace workspace, ConductorRun parentConductorRun, Path path, Path workingDirectory, ArchivedConductor conductor, String procedureName) {
    super(workspace, parentConductorRun, path);
    instanceCache.put(getPath().toString(), this);
    setConductor(conductor);
    setProcedureName(procedureName);
    setWorkingDirectory(workingDirectory);
    registry = new Registry(getWorkspace());
  }

  public static String debugReport() {
    return ProcedureRun.class.getSimpleName() + ": instanceCacheSize=" + instanceCache.size();
  }

  private Path cd(Path directory) {
    workingDirectory = directory;
    try {
      Files.createDirectories(workingDirectory);
      ChildElementsArrayList.createSortingFlag(workingDirectory);
    } catch (IOException e) {
      ErrorLogMessage.issue(e);
    }
    setWorkingDirectory(workingDirectory);
    return workingDirectory;
  }

  public Path cd(String directoryName) {
    return cd(resolveWorkingDirectory(false, directoryName));
  }

  public Path mkcd(String directoryName) {
    return cd(resolveWorkingDirectory(true, directoryName));
  }

  Path resolveWorkingDirectory(boolean forceMake, String target) {
    Path runDir = getParentConductorRun().getPath().toAbsolutePath().normalize();
    Path virtualDir = Paths.get("/").resolve(
      runDir.relativize(getWorkingDirectory())
    ).resolve(target).normalize();
    if (virtualDir.getParent() != null) {
      Path cleanVirtualParentDir = Paths.get("/");
      for (Path child : virtualDir.getParent()) {
        cleanVirtualParentDir = cleanVirtualParentDir.resolve(FileName.removeRestrictedCharacters(child.toString()));
      }
      Path result = runDir.resolve(Paths.get("/").relativize(cleanVirtualParentDir));
      if (forceMake) {
        result = result.resolve(FileName.generateUniqueFileName(result, virtualDir.getFileName().toString()));
      } else {
        result = result.resolve(FileName.removeRestrictedCharacters(virtualDir.getFileName().toString()));
      }
      return result;
    } else {
      return runDir;
    }
  }

  public void setWorkingDirectory(Path workingDirectory) {
    if (workingDirectory.isAbsolute()) {
      workingDirectory = Constants.WORK_DIR.relativize(workingDirectory);
    }
    this.workingDirectory = workingDirectory;
    setToProperty(KEY_WORKING_DIRECTORY, workingDirectory.toString());
  }

  public Path getWorkingDirectory() {
    if (this.workingDirectory == null) {
      this.workingDirectory = Paths.get(getStringFromProperty(KEY_WORKING_DIRECTORY));
    }
    return Constants.WORK_DIR.resolve(this.workingDirectory);
  }

  private int emptyKeyCount = 0;
  public void addReferable(String key, HasLocalPath run) {
    if (key == null) {
      key = StringKeyHashMap.toEmptyKey(emptyKeyCount++);
    }
    getReferables().put(key, run);
    WrappedJson jsonObject = getObjectFromProperty(KEY_REFERABLE);
    jsonObject.put(key, run.getLocalPath().toString());
    setToProperty(KEY_REFERABLE, jsonObject);
  }

  public void addReferable(HasLocalPath run) {
    addReferable(null, run);
  }

  public HasLocalPath getReferable(String key) {
    return getReferables().get(key);
  }

  public HasLocalPath getReferable() {
    return getReferables().get();
  }

  public StringKeyHashMap<HasLocalPath> getReferables() {
    if (referableMap == null) {
      WrappedJson jsonObject = getObjectFromProperty(KEY_REFERABLE);
      referableMap = new StringKeyHashMap<>();
      if (jsonObject == null) {
        setToProperty(KEY_REFERABLE, new WrappedJson());
      } else {
        for (Map.Entry entry : jsonObject.entrySet()) {
          try {
            referableMap.put(entry.getKey().toString(),
              (HasLocalPath) AbstractRun.getInstance(getWorkspace(), entry.getValue().toString()));
          } catch (RunNotFoundException e) {
            ErrorLogMessage.issue(e);
          }
        }
      }
    }
    return referableMap;
  }

  public void addGuard(String guard) {
    /* SYNTAX:
          R  := ConductorRun/ExecutableRun
          OP := {==, !=, <=, >=, <, >}

          if R finished
            "<Path of R>"

          if R had a specific value
            "<Path of CR> <Key of Variable/Result> <OP> <Value>"
     */
    if (!guard.startsWith(Constants.WORK_DIR.relativize(getBaseDirectoryPath(getWorkspace())).toString())) {
      //throw new OutOfDomainException(getWorkspace(), guard);
      throw new RuntimeException("Out of domain: " + guard);
    }

    //Todo: do refactoring
    String[] slicedGuard = guard.split(" ", 2);
    if (slicedGuard.length != 1) {
      try {
        new ValueGuard(guard);
      } catch (ValueGuard.InsufficientStatementException e) {
        throw new RuntimeException("Insufficient statement: " + guard);
      } catch (ValueGuard.InvalidOperatorException e) {
        throw new RuntimeException("Invalid operator: " + guard);
      }
    }

    if (getArrayFromProperty(KEY_GUARD) == null) {
      putNewArrayToProperty(KEY_GUARD);
    }
    putToArrayOfProperty(KEY_GUARD, guard);

    if (getArrayFromProperty(KEY_ACTIVE_GUARD) == null) {
      putNewArrayToProperty(KEY_ACTIVE_GUARD);
    }
    putToArrayOfProperty(KEY_ACTIVE_GUARD, guard);
  }

  public void addGuard(HasLocalPath run) {
    addGuard(run.getLocalPath().toString());
  }

  public void addGuard(HasLocalPath run, String valueKey, String operator, Object value) {
    addGuard(run.getLocalPath().toString() + " " + valueKey + " " + operator + " " + value.toString());
  }

  public void addReferableGuard(String key, HasLocalPath run) {
    addReferable(key, run);
    addGuard(run);
  }

  public void addReferableGuard(HasLocalPath run) {
    addReferableGuard(null, run);
  }

  public void addReferableGuard(String key, HasLocalPath run, String valueKey, String operator, Object value) {
    addReferable(key, run);
    addGuard(run, valueKey, operator, value);
  }

  public void addReferableGuard(HasLocalPath run, String valueKey, String operator, String value) {
    addReferableGuard(null, run, valueKey, operator, value);
  }

  public ArrayList<String> getGuardList() {
    ArrayList<String> list = new ArrayList<>();
    WrappedJsonArray jsonArray = getArrayFromProperty(KEY_GUARD);
    if (jsonArray == null) {
      putNewArrayToProperty(KEY_GUARD);
      return list;
    }

    for (Object object : jsonArray) {
      list.add(object.toString());
    }

    return list;
  }

  public ArrayList<String> getActiveGuardList() {
    ArrayList<String> list = new ArrayList<>();
    WrappedJsonArray jsonArray = getArrayFromProperty(KEY_ACTIVE_GUARD);
    if (jsonArray == null) {
      putNewArrayToProperty(KEY_ACTIVE_GUARD);
      return list;
    }

    for (Object object : jsonArray) {
      list.add(object.toString());
    }

    return list;
  }

  public void deactivateGuard(String guard) {
    if (getArrayFromProperty(KEY_ACTIVE_GUARD) == null) {
      putNewArrayToProperty(KEY_ACTIVE_GUARD);
    }
    removeFromArrayOfProperty(KEY_ACTIVE_GUARD, guard);
  }

  public void isSuccess(boolean isSuccess) {
    setToProperty(KEY_SUCCESS, isSuccess);
  }

  public boolean isSuccess() {
    return getBooleanFromProperty(KEY_SUCCESS, false);
  }

  @Override
  public void start() {
    if (!isStarted()) {
      started();
      if (getActiveGuardList().size() <= 0) {
        run();
      } else {
        ManagerMaster.register(this);
      }
    }
  }

  public void run() {
    setState(State.Running);

    if (conductor != null) {
      if (Key.MAIN_PROCEDURE.equals(procedureName)) {
        ScriptProcessor.getProcessor(conductor.getMainProcedureScriptPath()).processProcedure(this, getReferables(), conductor.getMainProcedureScript());
      } else {
        try {
          ScriptProcessor.getProcessor(conductor.getChildProcedureScriptPath(procedureName)).processProcedure(this, getReferables(), conductor.getChildProcedureScript(procedureName));
        } catch (ChildProcedureNotFoundException e) {
          //NOOP
        }
      }
    }
    /*
    if (getChildrenRunSize() <= 0) {
      reportFinishedRun(null);
    }
     */

    commit();

    setState(State.Finished);
  }

  /*
  public void startHandler(ScriptProcessor.ProcedureMode mode, AbstractRun caller, ArrayList<Object> arguments) {
    if (caller == null) {
      caller = this;
    }
    if (conductor != null) {
      if (Conductor.MAIN_PROCEDURE_ALIAS.equals(procedureName)) {
        ScriptProcessor.getProcessor(conductor.getMainProcedureScriptPath()).processProcedure(this, mode, caller, conductor.getMainProcedureScript(), arguments);
      } else {
        try {
          ScriptProcessor.getProcessor(conductor.getChildProcedureScriptPath(procedureName)).processProcedure(this, mode, caller, conductor.getChildProcedureScript(procedureName), arguments);
        } catch (ChildProcedureNotFoundException e) {
          //NOOP
        }
      }
    }
  }
   */

  @Override
  public void finish(State nextState) {
    setState(State.Finalizing);
    /*
    processFinalizers();
    getResponsible().reportFinishedRun(this);
     */
    setState(nextState);
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  public String getId() {
    return null;
  }

  @Override
  public Path getPropertyStorePath() {
    Path path = getPath();
    return path.getParent().resolve(path.getFileName().toString() + Constants.EXT_JSON);
  }

  public Registry getRegistry() {
    return registry;
  }

  private static Path getNewProcedureRunPath(ConductorRun conductorRun, String procedureName) {
    Path path = conductorRun.getPath();
    path = path.resolve(PROCEDURE_RUN).resolve(procedureName);
    String id = WaffleId.newId().toString();
    path = path.resolve(id.substring(0, 8)).resolve(id.substring(8, 10)).resolve(id.substring(10, 12)).resolve(id);
    return path;
  }

  public static ProcedureRun create(ConductorRun parent, ArchivedConductor conductor, String procedureName) {
    if (Conductor.MAIN_PROCEDURE_SHORT_ALIAS.equals(procedureName)) {
      procedureName = Key.MAIN_PROCEDURE;
    }
    ProcedureRun instance = new ProcedureRun(parent.getWorkspace(), parent, getNewProcedureRunPath(parent, procedureName), parent.getPath(), conductor, procedureName);
    instance.setState(State.Created);
    return instance;
  }

  public static ProcedureRun create(ProcedureRun parent, ArchivedConductor conductor, String procedureName) {
    if (Conductor.MAIN_PROCEDURE_SHORT_ALIAS.equals(procedureName)) {
      procedureName = Key.MAIN_PROCEDURE;
    }
    ProcedureRun instance = new ProcedureRun(parent.getWorkspace(), parent.getParentConductorRun(), getNewProcedureRunPath(parent.getParentConductorRun(), procedureName), parent.getWorkingDirectory(), conductor, procedureName);
    instance.setState(State.Created);
    return instance;
  }

  public static Path toJsonPath(Path path) {
    return path.getParent().resolve(path.getFileName().toString() + Constants.EXT_JSON);
  }

  public static ProcedureRun getInstance(Workspace workspace, String localPathString) {
    ProcedureRun instance = instanceCache.get(localPathString);
    if (instance != null) {
      return instance;
    }

    synchronized (instanceCache) {
      instance = instanceCache.get(localPathString);
      if (instance != null) {
        return instance;
      }

      Path jsonPath = toJsonPath(Constants.WORK_DIR.resolve(localPathString));

      if (Files.exists(jsonPath)) {
        try {
          WrappedJson jsonObject = new WrappedJson(StringFileUtil.read(jsonPath));
          ConductorRun parent = ConductorRun.getInstance(workspace, jsonObject.getString(KEY_PARENT_CONDUCTOR_RUN, null));
          ArchivedConductor conductor = null;
          if (jsonObject.keySet().contains(KEY_CONDUCTOR)) {
            conductor = ArchivedConductor.getInstance(workspace, jsonObject.getString(KEY_CONDUCTOR, null));
          }
          String procedureName = null;
          if (jsonObject.keySet().contains(KEY_PROCEDURE_NAME)) {
            procedureName = jsonObject.getString(KEY_PROCEDURE_NAME, null);
          }
          Path workingDirectory = null;
          if (jsonObject.keySet().contains(KEY_WORKING_DIRECTORY)) {
            workingDirectory = Paths.get(jsonObject.getString(KEY_WORKING_DIRECTORY, null));
          }
          instance = new ProcedureRun(workspace, parent, Paths.get(localPathString), workingDirectory, conductor, procedureName);
        } catch (Exception e) {
          ErrorLogMessage.issue(e);
        }
      }

      return instance;
    }
  }

  public static ProcedureRun getInstance(Workspace workspace, Path path) {
    if (path.isAbsolute()) {
      path = Constants.WORK_DIR.relativize(path);
    }
    return getInstance(workspace, path.normalize().toString());
  }

  /*
  public static ProcedureRun getTestRunProcedureRun(ArchivedExecutable executable) {
    return create(ConductorRun.getTestRunConductorRun(executable), executable.getArchiveName(), null, null);
  }
   */

  private void setConductor(ArchivedConductor conductor) {
    this.conductor = conductor;
    if (conductor != null) {
      setToProperty(KEY_CONDUCTOR, conductor.getArchiveName());
    } else {
      removeFromProperty(KEY_CONDUCTOR);
    }
  }

  private void setProcedureName(String procedureName) {
    this.procedureName = procedureName;
    if (procedureName != null) {
      setToProperty(KEY_PROCEDURE_NAME, procedureName);
    } else {
      removeFromProperty(KEY_PROCEDURE_NAME);
    }
  }

  private ArrayList<AbstractRun> transactionRunList = new ArrayList<>();

  public ConductorRun createConductorRun(String conductorName, String name) {
    Conductor conductor = Conductor.find(getProject(), conductorName);
    if (conductor == null) {
      throw new RuntimeException("Conductor\"(" + conductorName + "\") is not found");
    }

    ConductorRun conductorRun = ConductorRun.create(this, conductor, name);
    transactionRunList.add(conductorRun);

    return conductorRun;
  }

  public ConductorRun createConductorRun(String conductorName) {
    return createConductorRun(conductorName, conductorName);
  }

  public ProcedureRun createProcedureRun(String procedureName) {
    ConductorRun conductorRun = getParentConductorRun();
    if (!Conductor.MAIN_PROCEDURE_SHORT_ALIAS.equals(procedureName)) {
      if (!conductorRun.getConductor().getChildProcedureNameList().contains(procedureName)) {
        //throw new WorkspaceInternalException(getWorkspace(), "");
        boolean isNotFoundProcedureName = true;

        for (String ext : ScriptProcessor.CLASS_NAME_MAP.keySet()) {
          if (conductorRun.getConductor().getChildProcedureNameList().contains(procedureName + ext)) {
            procedureName = procedureName + ext;
            isNotFoundProcedureName = false;
            break;
          }
        }

        if (isNotFoundProcedureName) {
          throw new RuntimeException("Procedure\"(" + conductorRun.getConductor().getName() + "/" + procedureName + "\") is not found");
        }
      }
    }

    ProcedureRun procedureRun = ProcedureRun.create(this, conductorRun.getConductor(), procedureName);
    transactionRunList.add(procedureRun);
    return procedureRun;
  }

  public ExecutableRun createExecutableRun(String executableName, String computerName, String name) {
    Executable executable = Executable.getInstance(getProject(), executableName);
    if (executable == null) {
      throw new RuntimeException("Executable(\"" + executableName + "\") is not found");
    }
    Computer computer = Computer.find(computerName);
    if (computer == null) {
      throw new RuntimeException("Computer(\"" + computerName + "\") is not found");
    }
    //computer.update();
    if (! computer.getState().equals(ComputerState.Viable)) {
      throw new RuntimeException("Computer(\"" + computerName + "\") is not viable");
    }

    ExecutableRun executableRun = ExecutableRun.create(this, name, executable, computer);
    transactionRunList.add(executableRun);
    return executableRun;
  }

  public ExecutableRun createExecutableRun(String executableName, String computerName) {
    return createExecutableRun(executableName, computerName, executableName);
  }

  /*
  public SyncExecutableRun createSyncExecutableRun(String executableName, String computerName, String name) {
    Executable executable = Executable.getInstance(getProject(), executableName);
    if (executable == null) {
      throw new RuntimeException("Executable(\"" + executableName + "\") is not found");
    }
    Computer computer = Computer.find(computerName);
    if (computer == null) {
      throw new RuntimeException("Computer(\"" + computerName + "\") is not found");
    }
    //computer.update();
    if (! computer.getState().equals(ComputerState.Viable)) {
      throw new RuntimeException("Computer(\"" + computerName + "\") is not viable");
    }

    SyncExecutableRun syncExecutableRun = SyncExecutableRun.create(this, name, executable, computer);
    transactionRunList.add(syncExecutableRun);

    return syncExecutableRun;
  }
   */

  public void commit() {
    /*
    //TODO: do refactor
    if (conductorTemplate != null) {
      RubyScript.process((container) -> {
        String script = listenerTemplate.getScript();
        try {
          container.runScriptlet(RubyConductor.getListenerTemplateScript());
          container.runScriptlet(script);
          container.callMethod(Ruby.newInstance().getCurrentContext(), "exec_conductor_template_script", this, conductorTemplate);
        } catch (Exception e) {
          WarnLogMessage.issue(e);
          getRunNode().appendErrorNote(e.getMessage());
        }
      });
    } else if (listenerTemplate != null) {
      RubyScript.process((container) -> {
        String script = listenerTemplate.getScript();
        try {
          container.runScriptlet(RubyConductor.getListenerTemplateScript());
          container.runScriptlet(script);
          container.callMethod(Ruby.newInstance().getCurrentContext(), "exec_listener_template_script", this, this);
        } catch (Exception e) {
          WarnLogMessage.issue(e);
          getRunNode().appendErrorNote(e.getMessage());
        }
      });
    }

    if (transactionRunList.size() > 1) {
      getRunNode().switchToParallel();
    }
     */

    if (isSuccess()) {
      for (AbstractRun createdRun : transactionRunList) {
        if (!createdRun.isStarted()) {
          createdRun.start();
        }
      }
    }

    transactionRunList.clear();
  }

  public ArrayList<ExecutableRun> getActiveExecutableRunList() {
    return getParentConductorRun().getActiveExecutableRunList();
  }

  public void putVariables(WrappedJson valueMap) {
    getParentConductorRun().putVariables(valueMap);
  }

  public void putVariablesByJson(String json) {
    getParentConductorRun().putVariablesByJson(json);
  }

  public void putVariable(String key, Object value) {
    getParentConductorRun().putVariable(key, value);
  }

  public WrappedJson getVariables() {
    return getParentConductorRun().getVariables();
  }

  public Object getVariable(String key) {
    return getParentConductorRun().getVariable(key);
  }

  public String getVariableKey(String key) {
    return getParentConductorRun().getVariableKey(key);
  }

  /*
  public HashMap variables() { return getParentConductorRun().getVariables(); }
   */
  public WrappedJson v() { return getParentConductorRun().v(); }
  public void v(String key, Object value) { getParentConductorRun().v().put(key, value); }
}
