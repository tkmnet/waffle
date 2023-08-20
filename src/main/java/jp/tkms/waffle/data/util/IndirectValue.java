package jp.tkms.waffle.data.util;

import jp.tkms.waffle.data.log.message.WarnLogMessage;
import jp.tkms.waffle.data.project.workspace.Workspace;
import jp.tkms.waffle.data.project.workspace.run.ConductorRun;
import jp.tkms.waffle.data.project.workspace.run.ExecutableRun;
import jp.tkms.waffle.exception.RunNotFoundException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IndirectValue {
  /**
   * Syntax:
   * [*WAFFLE-<TYPE>::<GLOBAL KEY>::<LOCAL KEY>]
   *   TYPE:
   *      Variable: V
   *      Parameter: P
   *      Result: R
   *   GLOBAL KEY:
   *     Local path
   *   LOCAL KEY:
   *     Json path
   */

  public static final String PREFIX = "[*WAFFLE-";
  public static final String SUFFIX = "]";
  public static final String DELIMITER = "::";
  public static final Pattern PATTERN = Pattern.compile((PREFIX + "(.)" + DELIMITER + "(.+)" + DELIMITER + "(.+)" + SUFFIX).replaceAll("([\\[\\]\\*\\-])","\\\\$1"));

  private String type;
  private String runKey;
  private String valueKey;

  public IndirectValue(String type, String runKey, String valueKey) {
    this.type = type;
    this.runKey = runKey;
    this.valueKey = valueKey;
  }

  public String getKey() {
    return PREFIX + type + DELIMITER + runKey + DELIMITER + valueKey + SUFFIX;
  }

  public String getRunKey() {
    return runKey;
  }

  public String getValueKey() {
    return valueKey;
  }

  public Workspace getWorkspace() {
    return Workspace.resolveFromLocalPathString(runKey);
  }

  public String getString(String defaults) {
    try {
      switch (type) {
        case "V":
          return ConductorRun.getInstance(getWorkspace(), runKey).getVariables().getString(valueKey, defaults);
        case "P":
          return ExecutableRun.getInstance(runKey).getParameters().getString(valueKey, defaults);
        case "R":
          return ExecutableRun.getInstance(runKey).getResults().getString(valueKey, defaults);
      }
    } catch (RunNotFoundException | NullPointerException e) {
      return defaults;
    }
    return defaults;
  }

  public String getString() {
    return getString(null);
  }

  @Override
  public String toString() {
    return getKey();
  }

  public static IndirectValue convert(String key) throws WarnLogMessage {
    Matcher matcher = PATTERN.matcher(key);
    if (matcher.find() && matcher.groupCount() == 3) {
      String type = matcher.group(1).toUpperCase();
      switch (type) {
        case "V":
        case "P":
        case "R":
          return new IndirectValue(type, matcher.group(2), matcher.group(3));
      }
    }
    throw new WarnLogMessage("Invalid indirect value key: " + key);
  }

  public static boolean isMatchKeyPattern(String key) {
    Matcher matcher = PATTERN.matcher(key);
    if (matcher.find() && matcher.groupCount() == 3) {
      return true;
    }
    return false;
  }

  public static IndirectValue getVariableIndirectValue(ConductorRun run, String key) {
    return new IndirectValue("V", run.getLocalPath().toString(), key);
  }

  public static IndirectValue getParameterIndirectValue(ExecutableRun run, String key) {
    return new IndirectValue("P", run.getLocalPath().toString(), key);
  }

  public static IndirectValue getResultIndirectValue(ExecutableRun run, String key) {
    return new IndirectValue("R", run.getLocalPath().toString(), key);
  }
}
