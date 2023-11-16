package jp.tkms.waffle.data.computer;

import com.eclipsesource.json.JsonValue;
import jp.tkms.utils.concurrent.LockByKey;
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
import java.util.concurrent.atomic.AtomicBoolean;

public class Computer implements DataDirectory, PropertyFile, HasNote {
  private static final String KEY_LOCAL = "LOCAL";
  private static final String KEY_XSUB_TEMPLATE = "xsub_template";
  private static final String KEY_TYPE = "type";
  private static final String KEY_STATE = "state";
  private static final String KEY_ENVIRONMENTS = "environments";
  private static final String KEY_MESSAGE = "message";
  private static final String KEY_PARAMETERS_JSON = "PARAMETERS" + Constants.EXT_JSON;

  private static Set<String> xsubOptions = new HashSet<>();

  private static final InstanceCache<String, Computer> instanceCache = new InstanceCache<>();

  private static boolean isUpdatingXsubOptions = false;

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
  private WrappedJson parameters = null;
  private WrappedJson xsubTemplate = null;

  public Computer(String name) {
    this.name = name;
    instanceCache.put(name, this);

    initialize();

    Main.registerFileChangeEventListener(getBaseDirectoryPath().resolve(name), () -> {
      synchronized (this) {
        submitterType = null;
        parameters = null;
        xsubTemplate = null;
        reloadPropertyStore();
      }
    });
  }

  public static void updateXsubOptions(String options) {
    try {
      WrappedJsonArray array = new WrappedJsonArray(options);
      for (JsonValue v : array.toJsonArray()) {
        xsubOptions.add(v.asString());
      }
    } catch (Exception e) {
      WarnLogMessage.issue(e);
    }
  }

  public static List<String> getXsubOptions() {
    return new ArrayList<>(xsubOptions);
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
      try (LockByKey lock = LockByKey.acquire(Computer.class.getCanonicalName() + name)) {
        Computer computer = instanceCache.get(name);
        if (computer == null) {
          computer = new Computer(name);
        }
        return computer;
      }
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
      try (LockByKey lock = LockByKey.acquire(Computer.class.getCanonicalName() + name)) {
        computer = instanceCache.get(name);
        if (computer == null) {
          computer = new Computer(name);
        }
      }
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
      //e.printStackTrace();
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
          if (value != null) {
            setParameter(name, value);
          }
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
      Computer local = create(KEY_LOCAL, JobNumberLimitedLocalSubmitter.class.getCanonicalName());
      Path localWorkBaseDirectoryPath = Paths.get(".").toAbsolutePath().relativize(Constants.WORK_DIR.resolve(KEY_LOCAL));
      try {
        Files.createDirectories(localWorkBaseDirectoryPath);
      } catch (IOException e) {
        ErrorLogMessage.issue(e);
      }
      local.setParameter(AbstractSubmitter.KEY_WORKBASE, localWorkBaseDirectoryPath.toString());
      local.update();
      InfoLogMessage.issue(local, "was added automatically");
    } else if (!isUpdatingXsubOptions && xsubOptions.isEmpty()) {
      isUpdatingXsubOptions = true;
      Computer local = Computer.getInstance(KEY_LOCAL);
      local.update();
      isUpdatingXsubOptions = false;
    }
  }
}
