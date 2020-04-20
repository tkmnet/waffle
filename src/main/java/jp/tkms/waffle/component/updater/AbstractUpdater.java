package jp.tkms.waffle.component.updater;

import jp.tkms.waffle.component.template.Html;
import jp.tkms.waffle.data.BrowserMessage;

import java.util.ArrayList;
import java.util.Arrays;

public abstract class AbstractUpdater {
  private static ArrayList<AbstractUpdater> updaterList = new ArrayList<>(Arrays.asList(
    new RunStatusUpdater(), new SystemUpdater()
  ));

  abstract public String templateBody();
  abstract public String scriptArguments();
  abstract public String scriptBody();

  public static String getUpdaterElements() {
    String template = "";
    String script = "";
    for (AbstractUpdater updater : updaterList) {
      template += updater.templateBody();
      script += "var " + updater.getClass().getSimpleName() + " = function("
        + updater.scriptArguments() + ") {" + updater.scriptBody() + "};";
    }
    return Html.element("div",new Html.Attributes(Html.value("style", "display:none;")), template) + Html.javascript(script);
  }

  public AbstractUpdater(String... values) {
    BrowserMessage.addMessage(this.getClass().getSimpleName() + "(" + listByComma(values) + ")");
  }

  static String listByComma(String... values) {
    String result = "";
    for (int i = 0; i < values.length; i++) {
      String value = values[i];
      if (value != null) {
        result += (result != "" ? ',' : "") + value;
      }
    }
    return result;
  }
}