package jp.tkms.waffle;

import jp.tkms.waffle.web.component.project.ProjectsComponent;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Constants {
  static final public String JAR_URL = "https://desk.tkms.jp/resource/WjjMKKgC2MdI4GpepkzGOy4yQ3dWEOxM/waffle-all.jar";
  static final public String APP_NAME = "WAFFLE";
  static final public String APP_NAME_MEANING = "Workflow Administration Framework to Facilitate Lucid Exploration";
  static final public String MAIN_DB_NAME = ".main.db";
  static final public String ROOT_PAGE = ProjectsComponent.getUrl();
  static final public Path WORK_DIR = Paths.get("." + File.separator + APP_NAME).toAbsolutePath().normalize();
  static final public String PROJECT = "PROJECT";
  static final public String COMPUTER = "COMPUTER";
  static final public String LOG = "LOG";
  public static final String EXT_JSON = ".json";
  public static final String EXT_RUBY = ".rb";
  public static final String EXT_WNJ = ".wnj";
  public static final String STDOUT_FILE = "STDOUT.txt";
  public static final String STDERR_FILE = "STDERR.txt";
  public static final String DOT_INTERNAL = ".INTERNAL";
  public static final String DOT_FAVORITE = ".FAVORITE";
  public static final String DOT_TRASH = ".TRASH";
  static final public String DOT_LOCK = ".LOCK";
  public static final String PASSWORD = ".PASSWORD";
  public static final Path PID_FILE = WORK_DIR.resolve(DOT_INTERNAL).resolve("PID");
  public static final Path UUID_FILE = WORK_DIR.resolve(DOT_INTERNAL).resolve("UUID");
  public static final long HUGE_FILE_SIZE = 1048576;
  public static final int COMMUNICATION_TIMEOUT = 15000;
  public static final int TIME_LIMIT_OF_STREAM_MODE_SERVANT = 300000;
  public static final String WAFFLE_OPEN_COMMAND = "WAFFLE_OPEN_COMMAND";
  public static final Path AUTO_START_FILE = Constants.WORK_DIR.resolve(Constants.DOT_INTERNAL).resolve("AUTO_START");
  public static final Path REMOVE_JAR_FILE = Constants.WORK_DIR.resolve(Constants.DOT_INTERNAL).resolve("REMOVE_JAR");
  public static final Path SESSION_CACHE_FILE = WORK_DIR.resolve(DOT_INTERNAL).resolve("SESSION_CACHE");
}
