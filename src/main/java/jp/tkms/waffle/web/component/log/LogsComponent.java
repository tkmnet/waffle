package jp.tkms.waffle.web.component.log;

import jp.tkms.waffle.Main;
import jp.tkms.waffle.web.Key;
import jp.tkms.waffle.web.component.AbstractAccessControlledComponent;
import jp.tkms.waffle.web.component.ResponseBuilder;
import jp.tkms.waffle.web.template.Html;
import jp.tkms.waffle.web.template.Link;
import jp.tkms.waffle.web.template.Lte;
import jp.tkms.waffle.web.template.MainTemplate;
import jp.tkms.waffle.data.log.Log;
import spark.Spark;

import java.util.*;
import java.util.concurrent.Future;

public class LogsComponent extends AbstractAccessControlledComponent {
  public static final String KEY_LOGNAME = "logname";
  private final int logBundleSize = 50;
  private Mode mode;

  private final String[][] quickLinkRegExps = {
    {"^Computer\\((.*)\\)", "<a href=\"/COMPUTER/$1\">Computer($1)</a>"},
    {"^Run\\((PROJECT/)(.*?)(/WORKSPACE/)(.*?)(/RUN/)(.*)\\)", "<a href=\"/$1$2$3$4$5$6\">Run($2, $4, $6)</a>"}
  };

  public LogsComponent(Mode mode) {
    super();
    this.mode = mode;
  }

  public LogsComponent() {
    this(Mode.Default);
  }

  static public void register() {
    Spark.get(getUrl(Mode.GetOld), new ResponseBuilder(() -> new LogsComponent(Mode.GetOld)));
    Spark.get(getUrl(true), new ResponseBuilder(() -> new LogsComponent()));
  }

  public static String getUrl(Mode mode) {
    return "/LOG/@" + mode.name();
  }

  public static String getUrl(boolean isTemplate) {
    if (isTemplate) {
      return "/LOG/:" + KEY_LOGNAME;
    } else {
      return "/LOG/" + Log.getDatabasePath().getFileName().toString();
    }
  }

  public static String getUrl() {
    return getUrl(false);
  }

  @Override
  public void controller() {
    if (mode.equals(Mode.GetOld)) {
      int from = Integer.valueOf(request.queryParamOrDefault("from", "0"));
      renderOldLog(from);
    } else {
      if (!Log.getDatabasePath().getFileName().toString().equals(request.params(KEY_LOGNAME))) {
        response.redirect(getUrl());
        return;
      }
      renderList();
    }
  }

  private void renderOldLog(int from) {
    String body = "";

    for (Log log : Log.getDescList(from, logBundleSize)) {
      body += Html.element("tr", null,
        Html.element("td", null, log.getLevel().name() ),
        Html.element("td", null, log.getTimestamp() ),
        Html.element("td", null, convertMessage(log.getMessage()) )
      );
    }

    response.body(body);
  }

  private void renderList() {
    new MainTemplate() {

      @Override
      protected boolean enableParentLink() {
        return false;
      }

      @Override
      protected ArrayList<Map.Entry<String, String>> pageNavigation() {
        return null;
      }

      @Override
      protected String pageTitle() {
        return "Logs";
      }

      @Override
      protected ArrayList<Link> pageBreadcrumb() {
        return new ArrayList<Link>(Arrays.asList(Link.entry("Logs")));
      }

      @Override
      protected String pageContent() {
        final long[] lastRowId = {0};
        Lte.Table table;
        return
          Lte.card(null, null,
          Lte.table("table-condensed table-sm", table = new Lte.Table() {
            @Override
            public ArrayList<Lte.TableValue> tableHeaders() {
              ArrayList<Lte.TableValue> list = new ArrayList<>();
              list.add(new Lte.TableValue("width:1em;", "Level"));
              list.add(new Lte.TableValue("width:10em;", "Timestamp"));
              list.add(new Lte.TableValue("", "Message"));
              return list;
            }

            @Override
            public ArrayList<Future<Lte.TableRow>> tableRows() {
              ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
              for (Log log : Log.getDescList(-1, logBundleSize)) {
                list.add(Main.interfaceThreadPool.submit(() -> {
                  return new Lte.TableRow(
                    log.getLevel().name(),
                    log.getTimestamp(),
                    convertMessage(log.getMessage().replace("<", "&lt;").replace(">", "&gt;"))
                  );
                }));
                lastRowId[0] = log.getId();
              }
              return list;
            }
          }) + Html.javascript(
            "var insert_new_log = function(l, t, m) { document.getElementById('" + table.id.toString() + "').insertAdjacentHTML('afterbegin', '" +
              Html.element("tr", null,
                Html.element("td", null, "' + l + '"),
                Html.element("td", null, "' + t + '"),
                Html.element("td", null, "' + m" + convertMessageJavascript() + " + '")
                ).replaceAll("\n", "")
              + "'); };"
          )
          , null, null, "p-0")
            + Html.javascript("var last_row_id = " + lastRowId[0] + ";var loading_old_log_flag=false;"
            + "var loadOldLog = function(){if(loading_old_log_flag || last_row_id <= 0){return;}loading_old_log_flag=true;simpleget('" + getUrl(Mode.GetOld)
            + "?from=' + last_row_id,function(res,xhr){if(xhr.status===200){document.getElementById('"
            + table.id.toString() + "').insertAdjacentHTML('beforeend',res);last_row_id-=" + logBundleSize
            + ";}if(last_row_id <= 0){document.getElementById('oldLogButton').style.display='none';}loading_old_log_flag=false;});};"
            + "$(window).on(\"scroll\", function() {if($(window).scrollTop() + window.innerHeight > $(\"#oldLogButton\").offset().top){ loadOldLog(); }});"
          )
            + Html.div("text-center",
            Html.a("javascript:loadOldLog()", null, new Html.Attributes(Html.value("id", "oldLogButton")),
              Html.fasIcon("plus-circle")));
      }
    }.render(this);
  }

  private String convertMessage(String message) {
    for (String[] regExp : quickLinkRegExps) {
      message = message.replaceFirst(regExp[0], regExp[1]);
    }
    return message;
  }

  private String convertMessageJavascript() {
    String javascript = "";
    for (String[] regExp : quickLinkRegExps) {
      javascript += ".replace(/" + regExp[0].replace("/", "\\/") + "/, \"" + regExp[1].replace("\"", "\\\"") + "\")";
    }
    return javascript;
  }

  public enum Mode {Default, GetOld}
}
