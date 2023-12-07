package jp.tkms.waffle.sub.servant;

public class Constants {
  public static final String RESPONSE_SUFFIX = ".res";
  public static final String BASE = "BASE";
  public static final String ALIVE = ".ALIVE" + DirectoryHash.IGNORE_FLAG;
  public static final String XSUB_LOG_FILE = "xsub.log";
  public static final String EXEC_KEY = ".EXEC_KEY";
  public static final String NOTIFIER = ".NOTIFIER";
  public static final String JOBID_FILE = "JOBID.log";
  public static final String STDOUT_FILE = "STDOUT.txt";
  public static final String STDERR_FILE = "STDERR.txt";
  public static final String EVENT_FILE = "EVENT.bin";
  public static final String EVENT_DIR = "EVENT";
  public static final String REMAINS_FILE = "remains.txt";
  public static final String EVENT_SEPARATOR = new String(new byte[]{0x1e});
  public static final String EVENT_VALUE_SEPARATOR = new String(new byte[]{0x1f});
  public static final String EXIT_STATUS_FILE = "EXIT_STATUS.log";
  public static final String LOCAL_SHARED = "LOCAL_SHARED";
  public static final String PROJECT = "PROJECT";
  public static final String WORKSPACE = "WORKSPACE";
  public static final int DIRECTORY_SYNCHRONIZATION_TIMEOUT = 300;
  public static final String WAFFLE_SLOT_INDEX = "WAFFLE_SLOT_INDEX";
  public static final String WAFFLE_BASE = "WAFFLE_BASE";
  public static final String WAFFLE_TASK_JSONFILE = "WAFFLE_TASK_JASONFILE";
  public static final String WAFFLE_BATCH_WORKING_DIR = "WAFFLE_BATCH_WORKING_DIR";
  public static final int SHUTDOWN_TIMEOUT = 3;
  public static final int WATCHDOG_INTERVAL = 2;
  public static final int MAX_STREAM = 20;
}
