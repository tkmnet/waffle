package jp.tkms.waffle.data;

import com.eclipsesource.json.JsonValue;
import jp.tkms.utils.concurrent.LockByKey;
import jp.tkms.waffle.Constants;
import jp.tkms.waffle.data.log.message.WarnLogMessage;
import jp.tkms.waffle.data.util.StringFileUtil;
import jp.tkms.waffle.data.util.WrappedJson;
import jp.tkms.waffle.data.util.WrappedJsonArray;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public interface PropertyFile {
  WrappedJson getPropertyStoreCache();
  void setPropertyStoreCache(WrappedJson cache);

  default Path getPropertyStorePath() {
    return Paths.get("waffle.json");
  }

  private Path getAbsolutePropertyStorePath() {
    Path path = getPropertyStorePath();
    if (path.isAbsolute()) {
      return path.normalize();
    } else {
      return Constants.WORK_DIR.resolve(path).normalize();
    }
  }

  private WrappedJson getPropertyStore() {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      WrappedJson cache = getPropertyStoreCache();
      if (cache == null) {
        Path storePath = getAbsolutePropertyStorePath();
        String json = "{}";
        if (Files.exists(storePath)) {
          json = StringFileUtil.read(storePath);
        }
        try {
          if (!"".equals(json)) {
            cache = new WrappedJson(json);
          }
        }catch (Exception e) {
          WarnLogMessage.issue(storePath.toString() + " is broken : " + json);
        }
        if (cache == null) {
          cache = new WrappedJson();
        }
        setPropertyStoreCache(cache);
      }
      return cache;
    }
  }

  default void reloadPropertyStore() {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      setPropertyStoreCache(null);
    }
  }

  private void updatePropertyStore() {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      if (getPropertyStoreCache() != null) {
        getPropertyStoreCache().writePrettyFile(getAbsolutePropertyStorePath());
      }
    }
  }

  default String getStringFromProperty(String key) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      return getPropertyStore().getString(key, null);
    }
  }

  default String getStringFromProperty(String key, String defaultValue) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      String value = getStringFromProperty(key);
      if (value == null) {
        value = defaultValue;
      }
      return value;
    }
  }

  default Boolean getBooleanFromProperty(String key) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      return getPropertyStore().getBoolean(key, null);
    }
  }

  default Boolean getBooleanFromProperty(String key, Boolean defaultValue) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      Boolean value = getBooleanFromProperty(key);
      if (value == null) {
        value = defaultValue;
      }
      return value;
    }
  }

  default Integer getIntFromProperty(String key) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      return getPropertyStore().getInt(key, null);
    }
  }

  default Integer getIntFromProperty(String key, Integer defaultValue) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      Integer value = getIntFromProperty(key);
      if (value == null) {
        value = defaultValue;
      }
      return value;
    }
  }

  default Long getLongFromProperty(String key) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      return getPropertyStore().getLong(key, null);
    }
  }

  default Long getLongFromProperty(String key, Long defaultValue) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      Long value = getLongFromProperty(key);
      if (value == null) {
        value = defaultValue;
      }
      return value;
    }
  }

  default Double getDoubleFromProperty(String key) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      return getPropertyStore().getDouble(key, null);
    }
  }

  default Double getDoubleFromProperty(String key, Double defaultValue) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      Double value = getDoubleFromProperty(key);
      if (value == null) {
        value = defaultValue;
      }
      return value;
    }
  }

  default JsonValue getJsonValueFromProperty(String key) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      return getPropertyStore().toJsonObject().get(key);
    }
  }

  default WrappedJson getObjectFromProperty(String key) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      return getPropertyStore().getObject(key, null);
    }
  }

  default WrappedJson getObjectFromProperty(String key, WrappedJson defaultValue) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      WrappedJson value = getObjectFromProperty(key);
      if (value == null) {
        value = defaultValue;
      }
      return value;
    }
  }

  default WrappedJsonArray getArrayFromProperty(String key) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      return getPropertyStore().getArray(key, null);
    }
  }

  default WrappedJsonArray getArrayFromProperty(String key, WrappedJsonArray defaultValue) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      WrappedJsonArray value = getArrayFromProperty(key);
      if (value == null) {
        value = defaultValue;
      }
      return value;
    }
  }

  default void removeFromProperty(String key) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      getPropertyStore().remove(key);
      updatePropertyStore();
    }
  }

  default void setToProperty(String key, String value) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      getPropertyStore().put(key, value);
      updatePropertyStore();
    }
  }

  default void setToProperty(String key, boolean value) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      getPropertyStore().put(key, value);
      updatePropertyStore();
    }
  }

  default void setToProperty(String key, int value) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      getPropertyStore().put(key, value);
      updatePropertyStore();
    }
  }

  default void setToProperty(String key, long value) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      getPropertyStore().put(key, value);
      updatePropertyStore();
    }
  }

  default void setToProperty(String key, double value) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      getPropertyStore().put(key, value);
      updatePropertyStore();
    }
  }

  default void setToProperty(String key, WrappedJson value) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      getPropertyStore().put(key, value);
      updatePropertyStore();
    }
  }

  default void setToProperty(String key, WrappedJsonArray value) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      getPropertyStore().put(key, value);
      updatePropertyStore();
    }
  }

  default void putNewArrayToProperty(String key) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      getPropertyStore().put(key, new ArrayList<>());
      updatePropertyStore();
    }
  }

  default void putToArrayOfProperty(String key, String value) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      WrappedJsonArray array = getPropertyStore().getArray(key, null);
      if (array == null) {
        array = new WrappedJsonArray();
        getPropertyStore().put(key, array);
      }
      if (!array.contains(value)) {
        array.add(value);
      }
      updatePropertyStore();
    }
  }

  default void removeFromArrayOfProperty(String key, Object value) {
    try (LockByKey lock = LockByKey.acquire(getAbsolutePropertyStorePath())) {
      getPropertyStore().getArray(key, new WrappedJsonArray()).remove(value);
      updatePropertyStore();
    }
  }
}
