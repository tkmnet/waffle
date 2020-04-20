package jp.tkms.waffle.component;

import jp.tkms.waffle.data.BrowserMessage;
import spark.Spark;

public class BrowserMessageComponent extends AbstractAccessControlledComponent {
  private static final String KEY_CURRENT_ROWID = "cid";

  public static void register() {
    Spark.get(getUrl(null), new BrowserMessageComponent());
  }

  public static String getUrl(String id) {
    return "/bm" + (id == null ? "/:cid" : "/" + id);
  }

  @Override
  public void controller() {

    String result = "void(0);";
    response.body(result);

    String browserId = request.params(KEY_CURRENT_ROWID);

    for (BrowserMessage message : BrowserMessage.getList(browserId)) {
      result += "cid=" + message.getRowId() + ";" + message.getMessage() + ";";
    }

    response.body(result);
  }
}