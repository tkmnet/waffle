package jp.tkms.waffle.data.log.message;

import jp.tkms.waffle.data.ComputerTask;
import jp.tkms.waffle.data.computer.Computer;

public class InfoLogMessage extends LogMessage {
  public InfoLogMessage(String message) {
    super(message);
  }

  public static void issue(String message) {
    new InfoLogMessage(message).printMessage();
  }

  public static void issue(Throwable e) {
    new InfoLogMessage(getStackTrace(e)).printMessage();
  }

  public static void issue(ComputerTask run, String message) {
    new InfoLogMessage("Run(" + run.getLocalPath().toString() + ") " + message).printMessage();
  }

  public static void issue(Computer computer, String message) {
    new InfoLogMessage("Computer(" + computer.getName() + ") " + message).printMessage();
  }
}
