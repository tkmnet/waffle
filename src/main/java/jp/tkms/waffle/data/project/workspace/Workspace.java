package jp.tkms.waffle.data.project.workspace;

import jp.tkms.utils.concurrent.LockByKey;
import jp.tkms.utils.file.UpdatableLogFile;
import jp.tkms.waffle.Constants;
import jp.tkms.waffle.data.DataDirectory;
import jp.tkms.waffle.data.HasNote;
import jp.tkms.waffle.data.PropertyFile;
import jp.tkms.waffle.data.internal.task.ExecutableRunTask;
import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.log.message.WarnLogMessage;
import jp.tkms.waffle.data.project.Project;
import jp.tkms.waffle.data.project.ProjectData;
import jp.tkms.waffle.data.project.workspace.run.AbstractRun;
import jp.tkms.waffle.data.project.workspace.run.ExecutableRun;
import jp.tkms.waffle.data.util.*;
import jp.tkms.waffle.exception.RunNotFoundException;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Workspace extends ProjectData implements DataDirectory, PropertyFile, HasNote, Serializable {
  public static final String WORKSPACE = "WORKSPACE";
  public static final String JSON_FILE = WORKSPACE + Constants.EXT_JSON;
  public static final String TESTRUN_WORKSPACE = ".TESTRUN_WORKSPACE";
  public static final String ARCHIVE = ".ARCHIVE";
  public static final String SCRIPT_OUTPUT_FILE = "SCRIPT_OUTPUT.txt";
  public static final String KEY_EXECUTABLE_LOCK = "executable_lock#";
  private static final String KEY_STARTED_AT = "started_at";
  private static final String KEY_FINISHED = "finished";
  private static final Pattern PATH_RESOLVER = Pattern.compile("^"+ Project.PROJECT + "/(.+)/" + WORKSPACE + "/([^/]+)");

  private static final InstanceCache<String, Workspace> instanceCache = new InstanceCache<>();

  private String name = null;
  private Path recordPath;
  private long startedAt = -1;

  public Workspace(Project project, String name) {
    super(project);
    this.name = name;
    this.recordPath = getPath().resolve("RECORD.csv");
    instanceCache.put(getBaseDirectoryPath(project).resolve(name).toString(), this);
    initialise();
  }

  public static Workspace resolveFromLocalPathString(String localPathString) {
    Matcher matcher = PATH_RESOLVER.matcher(localPathString);
    if (matcher.find() && matcher.groupCount() == 2) {
      return getInstance(Project.getInstance(matcher.group(1)), matcher.group(2));
    }
    return null;
  }

    public String getName() {
    return name;
  }

  @Override
  public Path getPropertyStorePath() {
    return getPath().resolve(JSON_FILE);
  }

  public static Path getBaseDirectoryPath(Project project) {
    return project.getPath().resolve(WORKSPACE);
  }

  public static Workspace getInstance(Project project, String name) {
    if (project != null && name != null && !name.equals("") && Files.exists(getBaseDirectoryPath(project).resolve(name))) {
      Workspace instance = instanceCache.get(getBaseDirectoryPath(project).resolve(name).toString());
      if (instance != null) {
        return instance;
      }
      synchronized (instanceCache) {
        instance = instanceCache.get(getBaseDirectoryPath(project).resolve(name).toString());
        if (instance != null) {
          return instance;
        }
        return new Workspace(project, name);
      }
    }
    return null;
  }

  public static Workspace find(Project project, String key) {
    return getInstance(project, key);
  }

  public static ArrayList<Workspace> getList(Project project) {
    return new ChildElementsArrayList().getList(getBaseDirectoryPath(project), ChildElementsArrayList.Mode.OnlyNormalFavoriteFirst, name -> {
      return getInstance(project, name.toString());
    });
  }

  public static ArrayList<Workspace> getHiddenList(Project project) {
    return new ChildElementsArrayList().getList(getBaseDirectoryPath(project), ChildElementsArrayList.Mode.OnlyHidden, name -> {
      return getInstance(project, name.toString());
    });
  }

  public static Workspace createForce(Project project, String name) {
    synchronized (instanceCache) {
      Workspace workspace = getInstance(project, name);
      if (workspace == null) {
        workspace = new Workspace(project, name);
      }
      ChildElementsArrayList.createSortingFlag(workspace.getPath());

      return workspace;
    }
  }

  public static Workspace getTestRunWorkspace(Project project) {
    return createForce(project, TESTRUN_WORKSPACE);
  }

  public static Workspace create(Project project, String name) {
    return createForce(project, FileName.generateUniqueFileName(getBaseDirectoryPath(project), name));
  }

  private void initialise() {
    try {
      Files.createDirectories(getPath());
      Files.createDirectories(getPath().resolve(AbstractRun.RUN));
    } catch (IOException e) {
      ErrorLogMessage.issue(e);
    }
  }

  public long getStartedAt() {
    if (startedAt < 0) {
      startedAt = getLongFromProperty(KEY_STARTED_AT, DateTime.getCurrentEpoch());
      setToProperty(KEY_STARTED_AT, startedAt);
    }
    return startedAt;
  }

  public String getScriptOutput() {
    return getFileContents(SCRIPT_OUTPUT_FILE);
  }

  public void appendScriptOutput(String text) {
    createNewFile(SCRIPT_OUTPUT_FILE);
    appendFileContents(SCRIPT_OUTPUT_FILE, text);
  }

  @Override
  public Path getPath() {
    return getBaseDirectoryPath(getProject()).resolve(name);
  }

  WrappedJson propertyStoreCache = null;
  @Override
  public WrappedJson getPropertyStoreCache() {
    return propertyStoreCache;
  }
  @Override
  public void setPropertyStoreCache(WrappedJson cache) {
    propertyStoreCache = cache;
  }

  public void finish() {
    setToProperty(KEY_FINISHED, true);
  }

  public boolean isFinished() {
    return getBooleanFromProperty(KEY_FINISHED, false);
  }

  public void abort() {
    finish();

    for (ExecutableRunTask task : ExecutableRunTask.getList()) {
      try {
        ExecutableRun run = task.getRun();
        if (getLocalPath().equals(run.getWorkspace().getLocalPath())) {
          run.abort();
        }
      } catch (RunNotFoundException e) {
        //NOP
      }
    }
  }

  private static final Map<State, Integer> stateRecordSlots = new HashMap<>() {{
    put(State.Created, 1);
    put(State.Submitted, 2);
    put(State.Finished, 3);
    put(State.Excepted, 4);
  }};

  public void recordChildState(State state) {
    // LOCALTIME, CREATED, SUBMITTED, FINISHED, FAILED
    int slot = stateRecordSlots.get(state);

    try (LockByKey lock = LockByKey.acquire(recordPath)) {
      for (int c = 0; c < 10; c += 1) {
        try (UpdatableLogFile file = new UpdatableLogFile(recordPath)) {
          String line = file.getLastLine().trim();
          if (line.equals("")) {
            line = "0,0,0,0,0";
            file.updateLastLine(line);
          }
          String entries[] = line.split(",");
          long localTime = DateTime.getCurrentEpoch() - getStartedAt();
          if (localTime >= 0 && !entries[0].equals(String.valueOf(localTime))) {
            entries[0] = String.valueOf(localTime);
            entries[slot] = String.valueOf(Long.valueOf(entries[slot].trim()) + 1);
            file.addLine(String.join(",", entries));
          } else {
            entries[slot] = String.valueOf(Long.valueOf(entries[slot].trim()) + 1);
            file.updateLastLine(String.join(",", entries));
          }
        } catch (Exception e) {
          WarnLogMessage.issue(e);
          System.err.println("SLOT: " + slot);
        }
        break;
      }
    }
  }

  public boolean hasChildStateRecord() {
    return Files.exists(recordPath);
  }

  public String getChildStateRecord(int from) {
    try {
      StringBuilder builder = new StringBuilder();
      List<String> lines = Files.readAllLines(recordPath);
      for (int i = from; i < lines.size(); i += 1) {
        builder.append(lines.get(i));
        builder.append('\n');
      }
      return builder.toString();
    } catch (IOException e) {
      return "";
    }
  }
}
