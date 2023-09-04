package jp.tkms.waffle.web.component.misc;

import jp.tkms.waffle.Constants;
import jp.tkms.waffle.Main;
import jp.tkms.waffle.data.SystemDataAgent;
import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.project.workspace.run.ConductorRun;
import jp.tkms.waffle.data.project.workspace.run.ExecutableRun;
import jp.tkms.waffle.data.project.workspace.run.ProcedureRun;
import jp.tkms.waffle.data.util.InstanceCache;
import jp.tkms.waffle.data.util.ResourceFile;
import jp.tkms.waffle.script.ruby.util.RubyScript;
import jp.tkms.waffle.web.component.AbstractAccessControlledComponent;
import jp.tkms.waffle.web.component.ResponseBuilder;
import jp.tkms.waffle.web.template.Html;
import jp.tkms.waffle.web.template.Link;
import jp.tkms.waffle.web.template.Lte;
import jp.tkms.waffle.web.template.MainTemplate;
import jp.tkms.waffle.web.updater.GeneralUpdater;
import spark.Spark;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static jp.tkms.waffle.web.template.Html.*;

public class HelpComponent extends AbstractAccessControlledComponent {
  private Mode mode;

  public enum Mode {Default, Hibernate, Restart, Update, DebugReport, ReduceRubyContainerCache, Kill, Gc}

  public HelpComponent(Mode mode) {
    this.mode = mode;
  }

  static public void register() {
    Spark.get(getUrl(), new ResponseBuilder(() -> new HelpComponent(Mode.Default)));
    Spark.get(getUrl(null), new ResponseBuilder(() -> new HelpComponent(Mode.Default)));
  }

  public static String getUrl() {
    return "/@help";
  }

  public static String getUrl(String name) {
    if (name == null) {
      return getUrl() + "/*";
    } else {
      return getUrl() + "/" + name;
    }
  }

  @Override
  public void controller() {
    logger.info(response.status() + ": " + request.url());

    switch (mode) {
      case Hibernate:
        redirectToReferer();
        Main.hibernate();
        break;
      case Restart:
        redirectToReferer();
        Main.restart();
        break;
      case Update:
        redirectToReferer();
        Main.update();
      break;
      case DebugReport:
        break;
      case Kill:
        try {
          Runtime.getRuntime().exec("kill -9 " + Main.PID);
        } catch (IOException e) {
          ErrorLogMessage.issue(e);
        }
        break;
      case Gc:
        redirectToReferer();
        InstanceCache.gc();
        break;
      default:
        renderSystem();
    }
  }

  void redirectToReferer() {
    String referer = request.headers("Referer");
    if (referer == null || "".equals(referer)) {
      referer = "/";
    }
    response.redirect(referer);
  }

  void renderSystem() {
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
        return "H";
      }

      @Override
      protected ArrayList<Link> pageBreadcrumb() {
        return new ArrayList<Link>(Arrays.asList(Link.entry("System")));
      }

      @Override
      protected String pageContent() {
        return
          Lte.card(fasIcon("digital-tachograph") + "Status", null,
            div(null,
              Lte.readonlyTextInputWithCopyButton("PID", String.valueOf(Main.PID)),
              Lte.readonlyTextInputWithCopyButton("Version", Main.VERSION),
              Lte.readonlyTextInputWithCopyButton("Working directory", Constants.WORK_DIR.toString()),
              p(),
              Html.div("d-flex justify-content-around",
                GeneralUpdater.generateHtml("system.storage", (n,v)->Lte.disabledKnob(n, "#87ceeb",0,SystemDataAgent.getTotalStorage(), 1, false, Double.parseDouble(v), Html.span("font-weight-bold", null, "Available Storage (GB)"))),
                GeneralUpdater.generateHtml("system.cpu", (n,v)->Lte.disabledKnob(n, "#f08080",0,100, 1, true, Double.parseDouble(v), Html.span("font-weight-bold", null, "CPU Usage (%)"))),
                GeneralUpdater.generateHtml("system.memory", (n,v)->Lte.disabledKnob(n, "#deb887",0, SystemDataAgent.getTotalMemory(), 0.01, true, Double.parseDouble(v), Html.span("font-weight-bold", null,"Memory Usage (GB)")))
                )
            )
            ,null, "card-success", null
          ) +
        Lte.card(
          "LICENSE : " + element("strong", null, "Copyright &copy; 2019 Waffle Developer Team"),
           null,
          element("pre", null, ResourceFile.getContents("/LICENSE.md"))
          ,null, "card-secondary", null
        );
      }
    }.render(this);
  }

  String getStorageAvailableSpaceMessage() {
    File workDir = Constants.WORK_DIR.toFile();
    int gb = 1024 * 1024 * 1024;
    return String.format("%,d", workDir.getUsableSpace() / gb) + "GB (" +
      ((int)(100.0 * workDir.getUsableSpace() / workDir.getTotalSpace())) + "% in " +
      String.format("%,d", workDir.getTotalSpace() / gb) + "GB)";
  }
}
