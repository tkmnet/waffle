package jp.tkms.waffle.data.computer;

import com.eclipsesource.json.JsonValue;
import jp.tkms.waffle.Constants;
import jp.tkms.waffle.data.HasNote;
import jp.tkms.waffle.data.util.*;
import jp.tkms.waffle.inspector.Inspector;
import jp.tkms.waffle.Main;
import jp.tkms.waffle.data.web.Data;
import jp.tkms.waffle.data.DataDirectory;
import jp.tkms.waffle.data.PropertyFile;
import jp.tkms.waffle.exception.WaffleException;
import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.log.message.InfoLogMessage;
import jp.tkms.waffle.data.log.message.WarnLogMessage;
import jp.tkms.waffle.communicator.*;
import jp.tkms.waffle.web.template.Lte;
import net.schmizz.sshj.connection.ConnectionException;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Computer implements DataDirectory, PropertyFile, HasNote {
  private static final String KEY_LOCAL = "LOCAL";
  public static final String KEY_WORKBASE = "work_base_dir";
  //private static final String KEY_XSUB = "xsub_dir";
  private static final String KEY_XSUB_TEMPLATE = "xsub_template";
  public static final String KEY_POLLING = "polling_interval";
  private static final String KEY_TYPE = "type";
  private static final String KEY_STATE = "state";
  private static final String KEY_ENVIRONMENTS = "environments";
  private static final String KEY_MESSAGE = "message";
  private static final String KEY_JVM_ACTIVATION_COMMAND = "jvm_activation_commnad";
  private static final String KEY_PARAMETERS_JSON = "PARAMETERS" + Constants.EXT_JSON;

  private static final InstanceCache<String, Computer> instanceCache = new InstanceCache<>();

  public static final ArrayList<Class<AbstractSubmitter>> submitterTypeList = new ArrayList(Arrays.asList(
    JobNumberLimitedSshSubmitter.class,
    ThreadAndMemoryLimitedSshSubmitter.class,
    JobNumberLimitedLocalSubmitter.class,
    MultiComputerSubmitter.class,
    LoadBalancingSubmitter.class,
    DeadlineWrapper.class,
    BarrierWrapper.class,
    PodWrappedSubmitter.class
  ));

  private String name;
  private String submitterType = null;
  private String workBaseDirectory = null;
  private String jvmActivationCommand = null;
  private SecretKeySpec encryptKey = null;
  private Integer pollingInterval = null;
  private Double maximumNumberOfThreads = null;
  private Double allocableMemorySize = null;
  private Integer maximumNumberOfJobs = null;
  private WrappedJson parameters = null;
  private WrappedJson xsubTemplate = null;

  public Computer(String name) {
    this.name = name;
    instanceCache.put(name, this);

    initialize();

    Main.registerFileChangeEventListener(getBaseDirectoryPath().resolve(name), () -> {
      synchronized (this) {
        submitterType = null;
        workBaseDirectory = null;
        pollingInterval = null;
        maximumNumberOfThreads = null;
        allocableMemorySize = null;
        maximumNumberOfJobs = null;
        parameters = null;
        xsubTemplate = null;
        reloadPropertyStore();
      }
    });
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Computer) {
      return getName().equals(((Computer) o).getName());
    }
    return false;
  }

  public String getName() {
    return name;
  }

  public static Computer getInstance(String name) {
    if (name != null && !name.equals("") && Files.exists(getBaseDirectoryPath().resolve(name))) {
      Computer computer = instanceCache.get(name);
      if (computer == null) {
        computer = new Computer(name);
      }
      return computer;
    }
    return null;
  }

  public static Computer find(String key) {
    return getInstance(key);
  }

  public static ArrayList<Computer> getList() {
    initializeWorkDirectory();

    return new ChildElementsArrayList<>().getList(getBaseDirectoryPath(), name -> {
      return getInstance(name);
    });
  }

  public void initialize() {
    if (! Files.exists(getPath())) {
      try {
        Files.createDirectories(getPath());
      } catch (IOException e) {
        ErrorLogMessage.issue(e);
      }
    }

    initializeWorkDirectory();

    if (getState() == null) { setState(ComputerState.Unviable); }
    //if (getXsubDirectory() == null) { setXsubDirectory(""); }
    //if (getWorkBaseDirectory() == null) { setWorkBaseDirectory("/tmp/waffle"); }
    //if (getMaximumNumberOfThreads() == null) { setMaximumNumberOfThreads(1.0); }
    //if (getAllocableMemorySize() == null) { setAllocableMemorySize(1.0); }
    //if (getPollingInterval() == null) { setPollingInterval(10); }
    //if (getParameter(AbstractSubmitter.KEY_MAX_JOBS) == null) { setMaximumNumberOfJobs(1); }
  }

  public static ArrayList<Computer> getViableList() {
    ArrayList<Computer> list = new ArrayList<>();

    for (Computer computer : getList()) {
      if (computer.getState().equals(ComputerState.Viable)) {
        list.add(computer);
      }
    }

    return list;
  }

  public static Computer create(String name, String submitterClassName) {
    Data.initializeWorkDirectory();

    name = FileName.removeRestrictedCharacters(name);

    Computer computer = getInstance(name);
    if (computer == null) {
      computer = new Computer(name);
    }
    computer.setSubmitterType(submitterClassName);

    return computer;
  }

  public static ArrayList<Class<AbstractSubmitter>> getSubmitterTypeList() {
    return submitterTypeList;
  }

  public void update() {
    try {
      setMessage("");
      AbstractSubmitter.checkWaffleServant(this, false);
      AbstractSubmitter.updateXsubTemplate(this, false);
    } catch (RuntimeException | WaffleException e) {
      e.printStackTrace();
      String message = e.getMessage();
      if (message != null) {
        if (message.startsWith("java.io.FileNotFoundException: ")) {
          message = message.replaceFirst("java\\.io\\.FileNotFoundException: ", "");
          setState(ComputerState.KeyNotFound);
        } else if (message.startsWith("invalid privatekey: ")) {
          if (getParameters().keySet().contains(JobNumberLimitedSshSubmitter.KEY_IDENTITY_FILE)) {
            String keyPath = getParameters().getString(JobNumberLimitedSshSubmitter.KEY_IDENTITY_FILE, "");
            if (keyPath.indexOf('~') == 0) {
              keyPath = keyPath.replaceFirst("^~", System.getProperty("user.home"));
            }
            try {
              if (!"".equals(keyPath) && (new String(Files.readAllBytes(Paths.get(keyPath)))).indexOf("OPENSSH PRIVATE KEY") > 0) {
                message = keyPath + " is a OpenSSH private key type and WAFFLE does not support the key type.\nYou can change the key file type to a supported type by following command if the key path is ~/.ssh/id_rsa:\n$ ssh-keygen -p -f ~/.ssh/id_rsa -t rsa2 -m PEM";
                setState(ComputerState.UnsupportedKey);
              }
            } catch (IOException ioException) {
              ErrorLogMessage.issue(ioException);
            }
          }
        } else if (message.contains("UnresolvedAddressException")) {
          message = "Host not found";
          setState(ComputerState.Unviable);
        } else {
          message = message.replaceFirst("Auth fail", "[Auth fail]\nProbably, invalid user or key.\nYou should setup the host to login with public key authentication.");
          message = message.replaceFirst("USERAUTH fail", "[USERAUTH fail]\nProbably, invalid key passphrase (identity_pass).");
          message = message.replaceFirst("java\\.net\\.UnknownHostException: (.*)", "$1 is unknown host");
          message = message.replaceFirst("java\\.net\\.ConnectException: Connection refused \\(Connection refused\\)", "Connection refused (could not connect to the SSH server)");
          message = message.replaceFirst("jp.tkms.utils.crypt.DecryptingException: .*", "Could not decrypt passphrases because your master password was changed. Re-register passphrases which is displayed \"#*# = ENCRYPTED = #*#\".");
          setState(ComputerState.Unviable);
        }
        setMessage(message);
      }
    }
  }

  public void setMessage(String message) {
    setToProperty(KEY_MESSAGE, message);
  }

  public String getMessage() {
    return getStringFromProperty(KEY_MESSAGE, "");
  }

  public void setState(ComputerState state) {
    setToProperty(KEY_STATE, state.ordinal());
  }

  public ComputerState getState() {
    Integer state = getIntFromProperty(KEY_STATE, ComputerState.Unviable.ordinal());
    if (state == null) {
      return null;
    }
    return ComputerState.valueOf(state);
  }

  public boolean isLocal() {
    //return LOCAL_UUID.equals(id);
    return getName().equals(KEY_LOCAL);
  }

  public String getSubmitterType() {
    synchronized (this) {
      if (submitterType == null) {
        submitterType = getStringFromProperty(KEY_TYPE, submitterTypeList.get(0).getCanonicalName());
      }
      return submitterType;
    }
  }

  public void setSubmitterType(String submitterClassName) {
    synchronized (this) {
      setToProperty(KEY_TYPE, submitterClassName);
      submitterType = submitterClassName;
    }
  }

  /*
  public String getWorkBaseDirectory() {
    synchronized (this) {
      if (workBaseDirectory == null) {
        workBaseDirectory = getStringFromProperty(KEY_WORKBASE);
      }
      return workBaseDirectory;
    }
  }

  public void setWorkBaseDirectory(String workBaseDirectory) {
    synchronized (this) {
      setToProperty(KEY_WORKBASE, workBaseDirectory);
      this.workBaseDirectory = workBaseDirectory;
    }
  }

  public String getXsubDirectory() {
    synchronized (this) {
      if (xsubDirectory == null) {
        xsubDirectory = getStringFromProperty(KEY_XSUB);
      }
      return xsubDirectory;
    }
  }

  public void setXsubDirectory(String xsubDirectory) {
    synchronized (this) {
      setToProperty(KEY_XSUB, xsubDirectory);
      this.xsubDirectory = xsubDirectory;
    }
  }
   */


  /*
  public String getOs() {
    if (os == null) {
      os = getStringFromDB(KEY_OS);
    }
    return os;
  }

  public String getDirectorySeparetor() {
    String directorySeparetor = "/";
    if (getOs().equals("U")) {
      directorySeparetor = "/";
    }
    return directorySeparetor;
  }

  public String getJvmActivationCommand() {
    synchronized (this) {
      if (jvmActivationCommand == null) {
        jvmActivationCommand = getStringFromProperty(KEY_JVM_ACTIVATION_COMMAND, "");
      }
    }
    return jvmActivationCommand;
  }

  public void setJvmActivationCommand(String jvmActivationCommand) {
    synchronized (this) {
      setToProperty(KEY_JVM_ACTIVATION_COMMAND, jvmActivationCommand);
      this.jvmActivationCommand = jvmActivationCommand;
    }
  }

  public Integer getPollingInterval() {
    synchronized (this) {
      if (pollingInterval == null) {
        pollingInterval = getIntFromProperty(KEY_POLLING);
      }
      return pollingInterval;
    }
  }

  public void setPollingInterval(Integer pollingInterval) {
    synchronized (this) {
      setToProperty(KEY_POLLING, pollingInterval);
      this.pollingInterval = pollingInterval;
    }
  }
   */

  public WrappedJson getXsubParameters() {
    WrappedJson jsonObject = new WrappedJson();
    for (Object key : getXsubParametersTemplate().keySet()) {
      jsonObject.put(key.toString(), getParameter(key.toString()));
    }
    return jsonObject;
  }

  public WrappedJson getParametersWithDefaultParameters() {
    WrappedJson parameters = AbstractSubmitter.getParameters(this);
    WrappedJson jsonObject = getParameters();
    for (Object key : jsonObject.keySet()) {
      parameters.put(key, jsonObject.get(key));
    }
    return parameters;
  }

  public WrappedJson getParametersWithDefaultParametersFiltered() {
    WrappedJson parameters = AbstractSubmitter.getParameters(this);
    WrappedJson jsonObject = getParameters();
    for (Object key : jsonObject.keySet()) {
      if (! key.toString().startsWith(".")) {
        parameters.put(key, jsonObject.get(key));
      }
    }
    return parameters;
  }

  public WrappedJson getFilteredParameters() {
    WrappedJson jsonObject = new WrappedJson();
    WrappedJson parameters = getParameters();
    for (Object key : parameters.keySet()) {
      if (key.toString().startsWith(".")) {
        jsonObject.put(key, parameters.get(key));
      }
    }
    return jsonObject;
  }

  public WrappedJson getParameters() {
    synchronized (this) {
      if (parameters == null) {
        String json = getFileContents(KEY_PARAMETERS_JSON);
        if (json.equals("")) {
          json = "{}";
          createNewFile(KEY_PARAMETERS_JSON);
          updateFileContents(KEY_PARAMETERS_JSON, json);
        }
        parameters = getXsubParametersTemplate();
        parameters.merge(
          AbstractSubmitter.getInstance(Inspector.Mode.Normal, this)
            .getDefaultParameters(this)
        );
        parameters.merge(new WrappedJson(json));
      }
      return parameters.clone();
    }
  }

  public Object getParameter(String key, AbstractSubmitter submitter) {
    JsonValue value = getParameters().toJsonObject().get(key);
    if (value == null || value.isNull()) {
      value = getJsonValueFromProperty(key);
    }
    if (value == null || value.isNull()) {
      for (JsonValue jsonValue : submitter.getFormSettings().toJsonArray()) {
        WrappedJson entry = new WrappedJson(jsonValue.asObject());
        String name = entry.getString(AbstractSubmitter.KEY_NAME, "_" + System.nanoTime());
        if (name.equals(key)) {
          value = entry.toJsonObject().get(AbstractSubmitter.KEY_DEFAULT);
          break;
        }
      }
    }
    if (value != null && value.isString()) {
      return value.asString();
    }
    return value;
  }

  public Object getParameter(String key) {
    return getParameter(key, AbstractSubmitter.getInstance(Inspector.Mode.Normal, this));
  }

  public void setParameters(WrappedJson jsonObject) {
    synchronized (this) {
      WrappedJson parameters = getFilteredParameters();
      parameters.merge(jsonObject);
      parameters.writePrettyFile(getPath().resolve(KEY_PARAMETERS_JSON));
      this.parameters = null;
    }
  }

  public void setParameters(String json) {
    try {
      setParameters(new WrappedJson(json));
    } catch (Exception e) {
      WarnLogMessage.issue(e);
    }
  }

  public Object setParameter(String key, Object value) {
    synchronized (this) {
      WrappedJson jsonObject = getParameters();
      jsonObject.put(key, value);
      setParameters(jsonObject);
      return value;
    }
  }

  public WrappedJson getEnvironments() {
    synchronized (this) {
      return getObjectFromProperty(KEY_ENVIRONMENTS, new WrappedJson());
    }
  }

  public void setEnvironments(WrappedJson jsonObject) {
    synchronized (this) {
      setToProperty(KEY_ENVIRONMENTS, jsonObject);
    }
  }

  public WrappedJson getXsubTemplate() {
    synchronized (this) {
      if (xsubTemplate == null) {
        xsubTemplate = new WrappedJson(getStringFromProperty(KEY_XSUB_TEMPLATE, "{}"));
      }
      return xsubTemplate;
    }
  }

  public void setXsubTemplate(WrappedJson jsonObject) {
    synchronized (this) {
      this.xsubTemplate = jsonObject;
      setToProperty(KEY_XSUB_TEMPLATE, jsonObject.toString());
    }
  }

  public WrappedJson getXsubParametersTemplate() {
    WrappedJson jsonObject = new WrappedJson();

    try {
      WrappedJson object = getXsubTemplate().getObject("parameters", new WrappedJson());
      for (Object key : object.keySet()) {
        jsonObject.put(key.toString(), object.getObject(key.toString(), new WrappedJson()).get("default"));
      }
    } catch (Exception e) {
    }

    return jsonObject;
  }

  public static Path getBaseDirectoryPath() {
    return Data.getWaffleDirectoryPath().resolve(Constants.COMPUTER);
  }

  @Override
  public Path getPath() {
    return getBaseDirectoryPath().resolve(name);
  }

  private Path getLockFilePath() {
    return getPath().resolve(Constants.DOT_LOCK);
  }

  public boolean isLocked() {
    return Files.exists(getLockFilePath());
  }

  public void lock(boolean isLock) {
    try {
      if (isLock) {
        StringFileUtil.write(getLockFilePath(), "");
        getLockFilePath().toFile().deleteOnExit();
      } else {
        Files.deleteIfExists(getLockFilePath());
      }
    } catch (IOException e) {
      ErrorLogMessage.issue(e);
    }
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

  @Override
  public Path getPropertyStorePath() {
    return getPath().resolve(Constants.COMPUTER + Constants.EXT_JSON);
  }

  public static void initializeWorkDirectory() {
    Data.initializeWorkDirectory();
    if (! Files.exists(getBaseDirectoryPath().resolve(KEY_LOCAL))) {
      Computer computer = create(KEY_LOCAL, JobNumberLimitedLocalSubmitter.class.getCanonicalName());
      Path localWorkBaseDirectoryPath = Paths.get(".").toAbsolutePath().relativize(Constants.WORK_DIR.resolve(KEY_LOCAL));
      try {
        Files.createDirectories(localWorkBaseDirectoryPath);
      } catch (IOException e) {
        ErrorLogMessage.issue(e);
      }
      computer.setParameter(KEY_WORKBASE, localWorkBaseDirectoryPath.toString());
      computer.update();
      InfoLogMessage.issue(computer, "was added automatically");
    } else if (getXsubTypeOptions().size() <= 1) {
      Computer local = Computer.getInstance(KEY_LOCAL);
      local.update();
    }
  }

  private static ArrayList<String> xsubTypeOptions = new ArrayList<>(){{add("None");}};

  public static void updateXsubTypeOptions(WrappedJsonArray optionArray) {
    synchronized (xsubTypeOptions) {
      xsubTypeOptions.clear();
      xsubTypeOptions.add("None");
      for (JsonValue value : optionArray.toJsonArray()) {
        if (!"None".equals(value.asString())) {
          xsubTypeOptions.add(value.asString());
        }
      }
    }
  }

  public static ArrayList<String> getXsubTypeOptions() {
    return xsubTypeOptions;
  }
}
