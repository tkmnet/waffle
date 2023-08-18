package jp.tkms.waffle.data.project.workspace.run;

import jp.tkms.waffle.Constants;
import jp.tkms.waffle.Main;
import jp.tkms.waffle.data.DataDirectory;
import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.log.message.WarnLogMessage;
import jp.tkms.waffle.data.project.conductor.Conductor;
import jp.tkms.waffle.data.project.workspace.HasLocalPath;
import jp.tkms.waffle.data.project.workspace.Workspace;
import jp.tkms.waffle.data.project.workspace.archive.ArchivedConductor;
import jp.tkms.waffle.data.project.workspace.archive.ArchivedExecutable;
import jp.tkms.waffle.data.project.workspace.conductor.StagedConductor;
import jp.tkms.waffle.data.util.*;
import jp.tkms.waffle.exception.RunNotFoundException;
import jp.tkms.waffle.manager.ManagerMaster;
import jp.tkms.waffle.web.Key;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;

public class ConductorRun extends AbstractRun implements DataDirectory {
  public static final String CONDUCTOR_RUN = "CONDUCTOR_RUN";
  public static final String JSON_FILE = CONDUCTOR_RUN + Constants.EXT_JSON;
  public static final String VARIABLES_JSON_FILE = "VARIABLES" + Constants.EXT_JSON;
  public static final String KEY_CONDUCTOR = "conductor";
  public static final String KEY_ACTIVE_RUN = "active_run";
  public static final String KEY_ACTIVE_EXECUTABLE_RUN = "active_executable_run";
  private static final String TRASHBIN_DIR = ".TRASH";
  private static final String ESCAPING_WAFFLE_WORKSPACE_NAME = "<#WAFFLE_WORKSPACE_NAME>";
  private static final String ESCAPING_WAFFLE_UUID = "<#WAFFLE_UUID>";

  private ArchivedConductor conductor;

  private static final InstanceCache<String, ConductorRun> instanceCache = new InstanceCache<>();

  public static String debugReport() {
    return ConductorRun.class.getSimpleName() + ": instanceCacheSize=" + instanceCache.size();
  }

  @Override
  public String getName() {
    return this.getPath().getFileName().toString();
  }

  @Override
  public String getId() {
    return getLocalPath().toString();
  }

  public void appendErrorNote(String note) {
    createNewFile(KEY_ERROR_NOTE_TXT);
    updateFileContents(KEY_ERROR_NOTE_TXT, getErrorNote().concat(note).concat("\n"));
  }

  public String getErrorNote() {
    return getFileContents(KEY_ERROR_NOTE_TXT);
  }

  public void start(boolean async) {
    if (async) {
      Main.systemThreadPool.submit(new Runnable() {
        @Override
        public void run() {
          start();
        }
      });
    } else {
      start();
    }
  }

  @Override
  public void start() {
    started();
    setState(State.Running);
    if (conductor != null) {
      expandEscaping();
      ProcedureRun procedureRun = ProcedureRun.create(this, conductor, Key.MAIN_PROCEDURE);
      procedureRun.start();

      //ProcedureRun procedureRun = ProcedureRun.create(this, Conductor.MAIN_PROCEDURE_FILENAME.replaceFirst("\\..*?$", ""), conductor, Conductor.MAIN_PROCEDURE_ALIAS);
      //procedureRun.startFinalizer(ScriptProcessor.ProcedureMode.START_OR_FINISHED_ALL, getParent());
    }
    /*
    if (getParent() != null) {
      getParent().registerChildRun(this);
    }
    if (!getResponsible().getId().equals(getId())) {
      getResponsible().registerChildActiveRun(this);
    }
     */
    //reportFinishedRun(null);
    updateRunningStatus();
  }

  @Override
  public void finish(State nextState) {
    setState(State.Finalizing);
    /*
    setState(State.Finalizing);
    processFinalizers();
    if (!getResponsible().getId().equals(getId())) {
      getResponsible().reportFinishedRun(this);
    }
    setState(State.Finished);
     */

    ManagerMaster.signalFinished(this);

    if (isRoot()) {
      getWorkspace().finish();
    }

    setState(nextState);
  }

  protected Path getVariablesStorePath() {
    return this.getPath().resolve(VARIABLES_JSON_FILE);
  }

  protected void updateVariablesStore(WrappedJson variables) {
    //protected void updateParametersStore() {
    if (! Files.exists(this.getPath())) {
      try {
        Files.createDirectories(this.getPath());
      } catch (IOException e) {
        ErrorLogMessage.issue(e);
      }
    }

    if (variables == null) {
      variables = new WrappedJson();
    }

    Path storePath = getVariablesStorePath();
    variables.writePrettyFile(storePath);

    ManagerMaster.signalUpdated(this);
  }

  public long getVariablesStoreSize() {
    return getVariablesStorePath().toFile().length();
  }

  public void expandEscaping() {
    String json = getFromVariablesStore();
    json = json.replace(ESCAPING_WAFFLE_WORKSPACE_NAME, getWorkspace().getName());
    while (json.contains(ESCAPING_WAFFLE_UUID)) {
      json = json.replaceFirst(ESCAPING_WAFFLE_UUID, UUID.randomUUID().toString());
    }
    putVariablesByJson(json);
  }

  private String getFromVariablesStore() {
    Path storePath = getVariablesStorePath();
    String json = "{}";
    if (Files.exists(storePath)) {
      json = StringFileUtil.read(storePath);
    }
    return json;
  }

  public void putVariables(WrappedJson valueMap) {
    getVariables(); // init.
    WrappedJson map = new WrappedJson(getFromVariablesStore());
    if (valueMap != null) {
      map.merge(valueMap);
      /*
      for (String key : valueMap.names()) {
        map.add(key, valueMap.get(key));
      }
       */
      updateVariablesStore(map);
    }
  }

  public void putVariablesByJson(String json) {
    if (json == null) { return; }

    try {
      putVariables(new WrappedJson(json));
    } catch (Exception e) {
      System.err.println(json);
      WarnLogMessage.issue(e);
    }
  }

  public void putVariable(String key, Object value) {
    WrappedJson json = new WrappedJson();
    json.put(key, value);
    putVariables(json);
  }

  public WrappedJson getVariables() {
    return new WrappedJson(getFromVariablesStore()).withUpdateHandler((v)->{ updateVariablesStore(v); });
  }

  public Object getVariable(String key) {
    return getVariables().get(key);
  }

  public String getVariableKey(String key) {
    //return getLocalPath().toString() + RESULT_PATH_SEPARATOR + key;
    return IndirectValue.getVariableIndirectValue(this, key).getKey();
  }

  void loadDefaultVariables() {
    if (conductor != null) {
      putVariables(conductor.getDefaultVariables());
    }
  }

  public ConductorRun(Workspace workspace, ArchivedConductor conductor, ConductorRun parent, Path path) {
    super(workspace, parent, path);
    instanceCache.put(getLocalPath().toString(), this);
    setConductor(conductor);
  }

  private void setConductor(ArchivedConductor conductor) {
    this.conductor = conductor;
    if (conductor == null) {
      removeFromProperty(KEY_CONDUCTOR);
    } else {
      setToProperty(KEY_CONDUCTOR, conductor.getArchiveName());
    }
  }

  public ArchivedConductor getConductor() {
    return conductor;
  }

  @Override
  public Path getPropertyStorePath() {
    return this.getPath().resolve(JSON_FILE);
  }

  public boolean isRoot() {
    return getParentConductorRun() == null;
  }

  private static ConductorRun create(Workspace workspace, ArchivedConductor conductor, ConductorRun parent, Path path) {
    ConductorRun instance = new ConductorRun(workspace, conductor, parent, path);
    instance.setState(State.Created);
    instance.loadDefaultVariables();
    return instance;
  }

  public static ConductorRun create(Workspace workspace, Conductor conductor, String name) {
    Path path = getBaseDirectoryPath(workspace);
    if (name != null) {
      path = path.resolve(name);
    }

    return create(workspace,
      (conductor == null ? null : StagedConductor.getInstance(workspace, conductor).getArchivedInstance()),
      null, path);
  }

  public static ConductorRun create(Workspace workspace, Conductor conductor) {
    return create(workspace, conductor, null);
  }

  public static ConductorRun create(ProcedureRun procedureRun, Conductor conductor, String expectedName) {
    Path path = FileName.generateUniqueFilePath(procedureRun.getWorkingDirectory().resolve(expectedName));

    return create(procedureRun.getWorkspace(),
      StagedConductor.getInstance(procedureRun.getWorkspace(), conductor).getArchivedInstance(),
      procedureRun.getParentConductorRun(), path);
  }

  public static ConductorRun getInstance(Workspace workspace, String localPathString) {
    ConductorRun instance = instanceCache.get(localPathString);
    if (instance != null) {
      return instance;
    }

    synchronized (instanceCache) {
      instance = instanceCache.get(localPathString);
      if (instance != null) {
        return instance;
      }

      Path jsonPath = Constants.WORK_DIR.resolve(localPathString).resolve(JSON_FILE);

      if (Files.exists(jsonPath)) {
        try {
          WrappedJson jsonObject = new WrappedJson(StringFileUtil.read(jsonPath));
          ArchivedConductor conductor = null;
          if (jsonObject.keySet().contains(KEY_CONDUCTOR)) {
            String conductorName = jsonObject.getString(KEY_CONDUCTOR, null);
            conductor = ArchivedConductor.getInstance(workspace, conductorName);
          }
          ConductorRun parent = null;
          if (jsonObject.keySet().contains(KEY_PARENT_CONDUCTOR_RUN)) {
            String parentPath = jsonObject.getString(KEY_PARENT_CONDUCTOR_RUN, null);
            parent = ConductorRun.getInstance(workspace, parentPath);
          }
          instance = new ConductorRun(workspace, conductor, parent, jsonPath.getParent());
        } catch (Exception e) {
          ErrorLogMessage.issue(jsonPath.toString() + " : " + ErrorLogMessage.getStackTrace(e));
        }
      }

      return instance;
    }
  }

  static ConductorRun getTestRunConductorRun(ArchivedExecutable executable) {
    return create(executable.getWorkspace(), null, executable.getName());
  }

  /*
  private Map<String, Object> variablesWrapper = null;
  public Map variables() {
    if (variablesWrapper == null) {
      variablesWrapper = new HashMap<String, Object>() {
        @Override
        public Object get(Object key) {
          return getVariable(key.toString());
        }

        @Override
        public Object put(String key, Object value) {
          super.put(key, value);
          putVariable(key.toString(), value);
          return value;
        }

        @Override
        public String toString() {
          return getVariables().toString();
        }
      };
    }

    return variablesWrapper;
  }
  public Map<String, Object> v() { return variables(); }
   */

  public WrappedJson v() {
    return getVariables();
  }

  public void v(String key, Object value) {
    putVariable(key, value);
  }

  private static final Object lockerObject = new Object(); // TODO: fixit

  void registerChildRun(HasLocalPath run) {
    synchronized (lockerObject) {
      if (getArrayFromProperty(KEY_ACTIVE_RUN) == null) {
        putNewArrayToProperty(KEY_ACTIVE_RUN);
      }
      putToArrayOfProperty(KEY_ACTIVE_RUN, run.getLocalPath().toString());

      // TODO: you should store the runs by an other method.
      if (run instanceof ExecutableRun) {
        if (getArrayFromProperty(KEY_ACTIVE_EXECUTABLE_RUN) == null) {
          putNewArrayToProperty(KEY_ACTIVE_EXECUTABLE_RUN);
        }
        putToArrayOfProperty(KEY_ACTIVE_EXECUTABLE_RUN, run.getLocalPath().toString());
      }
    }
  }

  public void unregisterChildRun(HasLocalPath run) {
    synchronized (lockerObject) {
      if (getArrayFromProperty(KEY_ACTIVE_RUN) == null) {
        putNewArrayToProperty(KEY_ACTIVE_RUN);
      }
      removeFromArrayOfProperty(KEY_ACTIVE_RUN, run.getLocalPath().toString());

      if (run instanceof ExecutableRun) {
        if (getArrayFromProperty(KEY_ACTIVE_EXECUTABLE_RUN) == null) {
          putNewArrayToProperty(KEY_ACTIVE_EXECUTABLE_RUN);
        }
        removeFromArrayOfProperty(KEY_ACTIVE_EXECUTABLE_RUN, run.getLocalPath().toString());
      }

      updateRunningStatus();
    }
  }

  private void updateRunningStatus() {
    WrappedJsonArray jsonArray = getArrayFromProperty(KEY_ACTIVE_RUN, new WrappedJsonArray());

    if (jsonArray.isEmpty()) {
      finish(State.Finished);
    }
  }

  public ArrayList<ExecutableRun> getActiveExecutableRunList() {
    ArrayList<ExecutableRun> list = new ArrayList<>();
    synchronized (lockerObject) {
      WrappedJsonArray array = getArrayFromProperty(KEY_ACTIVE_EXECUTABLE_RUN);
      if (array != null) {
        array.toJsonArray().forEach(v -> {
          try {
            list.add(ExecutableRun.getInstance(v.asString()));
          } catch (RunNotFoundException e) {
            WarnLogMessage.issue("RunNotFound at getActiveExecutableRunList: " + e);
          }
        });
      }
    }
    return list;
  }

  Path getTrashBinPath() {
    return getLocalPath().resolve(TRASHBIN_DIR);
  }
}
