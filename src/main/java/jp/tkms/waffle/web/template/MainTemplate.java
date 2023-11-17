package jp.tkms.waffle.web.template;

import jp.tkms.waffle.Constants;
import jp.tkms.waffle.Main;
import jp.tkms.waffle.web.AlertCookie;
import jp.tkms.waffle.web.component.*;
import jp.tkms.waffle.web.component.job.JobsComponent;
import jp.tkms.waffle.web.component.log.LogsComponent;
import jp.tkms.waffle.web.component.misc.BrowserMessageComponent;
import jp.tkms.waffle.web.component.misc.SystemComponent;
import jp.tkms.waffle.web.component.project.ProjectsComponent;
import jp.tkms.waffle.web.component.computer.ComputersComponent;
import jp.tkms.waffle.web.updater.AbstractUpdater;
import jp.tkms.waffle.data.web.BrowserMessage;
import jp.tkms.waffle.data.internal.task.ExecutableRunTask;
import jp.tkms.waffle.script.ruby.util.RubyScript;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static jp.tkms.waffle.web.template.Html.*;

abstract public class MainTemplate extends AbstractTemplate {
  @Override
  public void render(AbstractComponent component) {
    component.setBody(
      html(
        head(
          meta(value("charset", "utf-8")),
          meta(value("http-equiv", "X-UA-Compatible"),
            value("content", "IE=edge")),
          element("title", null,
            (pageTitle() == "" ? Constants.APP_NAME : pageTitle()
              + (pageSubTitle() == "" ? "" : "/" + pageSubTitle())
              + " | " + Constants.APP_NAME)),
          link("stylesheet", "/css/adminlte.min.css"),
          link("stylesheet", "/css/fontawesome-free.min.css"),
          link("stylesheet", "/css/ionicons.min.css"),
          link("stylesheet", "/css/tabler-icons.min.css"),
          link("stylesheet", "/css/gfonts.css"),
          link("stylesheet", "/css/select2.min.css"),
          link("stylesheet", "/css/icheck-bootstrap.min.css"),
          link("stylesheet", "/css/toastr.min.css"),
          link("stylesheet", "/css/custom.css"),
          link("stylesheet", "/jsoneditor/jsoneditor.min.css"),
          javascript("var waffle_uri='" + component.request.uri() + "';"),
          element("script", new Attributes(value("src", "/jsoneditor/jsoneditor.min.js"))),
          element("script", new Attributes(value("src", "/js/jquery.min.js"))),
          element("script", new Attributes(value("src", "/js/autoresizetextarea.js"))),
          element("script", new Attributes(value("src", "/js/short.js"))),
          element("script", new Attributes(value("src", "/js/simpleimport.js"))),
          element("script",new Html.Attributes(Html.value("src", "/js/chart.umd.js"), Html.value("type", "text/javascript")), "")
        ),
        body((component.isSidebarCollapsed() ? "sidebar-collapse " : "") + "hold-transition layout-footer-fixed layout-fixed",
          AbstractUpdater.getUpdaterElements(),
          div("wrapper",
            elementWithClass("nav", "main-header navbar navbar-expand navbar-light",
              renderPageNavigation(),
              renderPageRightNavigation()
            ),
            elementWithClass("aside", "main-sidebar sidebar-light-primary elevation-4",
              a(Constants.ROOT_PAGE, "brand-link navbar-light",
                new Attributes(value("title", Constants.APP_NAME_MEANING)),
                attribute("img",
                  value("src", "/img/logo.png"),
                  value("class", "brand-image"),
                  value("style", "opacity:1.0;")
                ),
                span("brand-text text-warning font-weight-bold", null, Constants.APP_NAME)
              ),
              div("sidebar",
                elementWithClass("nav", "mt-2",
                  element("ul",
                    new Attributes(
                      value("class", "nav nav-pills nav-sidebar flex-column"),
                      value("data-widget", "treeview"),
                      value("role", "menu"),
                      value("data-accordion", "false")
                    ),
                    renderPageSidebar()
                  )
                )
              )
            ),
            div("content-wrapper",
              elementWithClass("section", "content-header",
                div("container-fluid",
                  div("row mb-2",
                    div("col-sm-6",
                      h1(null,
                        (enableParentLink() ?
                        element("small", null,
                          a("javascript:gotoParent();", span(null, new Attributes(value("style", "color:#ffc107;")), fasIcon("caret-square-up")))
                        ) : ""),
                        pageTitle(),
                        element("small", null,
                          (pageSubTitle() != "" ? " / " + pageSubTitle() : "")
                        ),
                        ( helpLink() == null ? "" :
                          Html.a("/docs/" + helpLink() + ".html"/*HelpComponent.getUrl(helpLink())*/, "text-secondary",
                              new Html.Attributes().add("style", "font-size:0.5em;").add("target", "_blank"),
                              Html.fasIcon("question-circle"))
                        )
                      )
                    ),
                    div("col-sm-6 text-right",
                      renderPageBreadcrumb(),
                      renderPageWorkingDirectoryButton(component.request.ip())
                    )
                  ),
                  div(null, pageTool())
                )
              ),
              elementWithClass("section", "content",
                pageContent()
              )
            ),
            elementWithClass("footer", "main-footer", div("float-right d-none d-sm-block"),
              Lte.disabledTextInput("info", null, "Screen reloaded (WAFFLE:" + Main.VERSION + ")")
              /*
              element("strong", null, "Copyright &copy; 2019 Waffle Developer Team"),
              a(SystemComponent.getUrl(SystemComponent.Mode.Update), Lte.badge("secondary", null, "update (" + Main.VERSION + ")"))
               */
            )
          ),
          element("script", new Attributes(value("src", "/js/bootstrap.bundle.min.js"))),
          element("script", new Attributes(value("src", "/js/adminlte.min.js"))),
          element("script", new Attributes(value("src", "/js/select2.min.js"))),
          element("script", new Attributes(value("src", "/js/toastr.min.js"))),
          element("script", new Attributes(value("src", "/js/jquery.knob.min.js"))),
          element("script", new Attributes(value("type", "text/javascript")),
              "$(function(){$('.dial').knob();});"
          ),
          element("script", new Attributes(value("src", "/js/custom.js"))),
          element("script", new Attributes(value("type", "text/javascript")),
              "var cid=" + BrowserMessage.getCurrentRowId() + ";" +
              "var loadBrowserMessage = function() {" +
              "var r = ''; if(undefined != bm_subscribed){r = Array.from(bm_subscribed).join(',');}" +
              "simplepost('" + BrowserMessageComponent.getUrl("") + "' + cid, r, function(res) {try{eval(res)}catch(e){console.log(e)}setTimeout(loadBrowserMessage, 2000);})" +
              "}; " +
              "setTimeout(loadBrowserMessage, 5000);" +
              "var updateJobNum = function(n) {" +
              "if (n > 0) {" +
              "document.getElementById('jobnum').style.display = 'inline-block';" +
              "document.getElementById('jobnum').innerHTML = n;" +
              "} else {" +
              "document.getElementById('jobnum').style.display = 'none';" +
              "}" +
              "};updateJobNum(" + ExecutableRunTask.getNum() + ");"
          ),
          element("script", new Html.Attributes(Html.value("src", "/js/pushnotifier.js"), Html.value("type", "text/javascript")), ""),
          element("script", new Html.Attributes(Html.value("src", "/ace/ace.js"), Html.value("type", "text/javascript")), ""),
          element("script", new Html.Attributes(Html.value("src", "/ace/ext-language_tools.js"), Html.value("type", "text/javascript")), ""),
          element("script",new Html.Attributes(Html.value("src", "/js/ace-apply.js"), Html.value("type", "text/javascript")), ""),
          element("script",new Html.Attributes(Html.value("src", "/js/jsoneditor-apply.js"), Html.value("type", "text/javascript")), ""),
          AlertCookie.getAlertScript()
        )
      )
    );
  }

  private String renderPageWorkingDirectoryButton(String clientIP) {
    if (pageWorkingDirectory() != null) {
      Path directoryPath = pageWorkingDirectory().toAbsolutePath().normalize();
      if (Files.exists(directoryPath)) {
        if ("127.0.0.1".equals(clientIP) && System.getenv().containsKey(Constants.WAFFLE_OPEN_COMMAND)) {
          return Html.div("d-inline", " " + Lte.openButton(Html.fasIcon("folder"), directoryPath.toString()));
        } else {
          return Html.div("d-inline", " " + Lte.clipboardButton(Html.fasIcon("folder"), directoryPath.toString()));
        }
      }
    }
    return null;
  }

  protected String renderPageSidebar() {
    return
      elementWithClass("li", "nav-item",
        a(ProjectsComponent.getUrl(), "nav-link", null,
          fasIcon("folder-open", "nav-icon"),
          p("Projects")
        )
      ) +
      element("li",
        new Attributes(
          value("class", "nav-item"),
          value("style", "padding-left:1rem;"),
          value("id", "recently-accessed-project")
        ),
        a("", "nav-link", null,
          p("null")
        )
      ) +
        /*
      elementWithClass("li", "nav-item",
        a(TemplatesComponent.getUrl(), "nav-link", null,
          fasIcon("layer-group", "nav-icon"),
          p("Templates")
        )
      ) +
         */
      elementWithClass("li", "nav-item",
        a(JobsComponent.getUrl(), "nav-link", null,
          fasIcon("running", "nav-icon"),
          p("Jobs", span("right badge badge-warning", new Attributes(value("id", "jobnum"))))
        )
      ) +
      elementWithClass("li", "nav-item",
        a(ComputersComponent.getUrl(), "nav-link", null,
          fasIcon("server", "nav-icon"),
          p("Computers")
        )
      ) +
      elementWithClass("li", "nav-item",
        a(LogsComponent.getUrl(), "nav-link", null,
          fasIcon("quote-left", "nav-icon"),
          p("Logs")
        )
      ) +
      elementWithClass("li", "nav-item",
        a(SystemComponent.getUrl(), "nav-link", null,
          fasIcon("cog", "nav-icon"),
          p("System")
        )
      );
        /*+
      elementWithClass("li", "nav-header", "Status") +
      elementWithClass("li", "nav-item",
        Lte.disabledTextInput("info", null, "Screen reloaded")
      ) +
      elementWithClass("li", "nav-item",
        a(SystemComponent.getUrl(SystemComponent.Mode.Hibernate), "nav-link", new Attributes(value("title", String.valueOf(Main.PID))),
          fasIcon("power-off", "nav-icon"),
          p("Hibernate")
        )
      );
         */
  }

  String renderPageNavigation() {
    String innerContent = elementWithClass("li", "nav-item",
      a("#", "nav-link", new Attributes(value("data-widget", "pushmenu")),
        fasIcon("bars")
      )
    );

    if (pageNavigation() != null) {
      for (Map.Entry<String, String> entry : pageNavigation()) {
        innerContent += elementWithClass("li", "nav-item",
          a(entry.getValue(), "nav-link", null, entry.getKey())
        );
      }
    }

    return elementWithClass("ul", "navbar-nav", innerContent);
  }

  String renderPageRightNavigation() {
    String innerContent = "";

    if (pageRightNavigation() != null) {
      for (String value : pageRightNavigation()) {
        innerContent += elementWithClass("li", "nav-item", value);
      }
    }

    return elementWithClass("ul", "navbar-nav ml-auto", innerContent);
  }

  String renderPageBreadcrumb() {
    String innerContent = "";

    ArrayList<Link> breadcrumb = pageBreadcrumb();
    Collections.reverse(breadcrumb);
    breadcrumb.add(Link.entry(Constants.ROOT_PAGE, fasIcon("home")));
    Link top = breadcrumb.remove(0);

    for (int i = 0; i < breadcrumb.size(); i++) {
      Link link = breadcrumb.get(i);
      if (link == null) {
        innerContent += elementWithClass("li", null, attribute("hr", value("class", "dropdown-divider")));
      } else {
        innerContent += elementWithClass("li", null, a(link.getTarget(), "dropdown-item", null, link.getKey()));
      }
    }

    return Html.div("d-inline dropdown",
      element("button", new Attributes(value("class", "btn btn-outline-secondary btn-sm dropdown-toggle"),
        value("id", "pageBreadcrumb"), value("type", "button"), value("data-toggle", "dropdown"), value("aria-haspopup","true"), value("aria-expanded","false")), top.getKey()),
      element("ul", new Attributes(value("class", "dropdown-menu dropdown-menu-right"), value("aria-labelledby", "pageBreadcrumb")), innerContent));
  }

  protected ArrayList<String> pageRightNavigation() {
    ArrayList<String> nav = new ArrayList<String>();
    nav.add(Html.spanWithId("ruby-running-status", Lte.formSubmitButton("danger",Html.fasIcon("spinner", "fa-pulse") + "Script Running"),
      Html.javascript("var rubyRunningStatus=function(status){if(status){document.getElementById('ruby-running-status').style.display='inline';}else{document.getElementById('ruby-running-status').style.display='none';}};rubyRunningStatus(" + (RubyScript.hasRunning()?"true":"false") + ");")));
    return nav;
  }

  protected boolean enableParentLink() {
    return true;
  }

  protected abstract ArrayList<Map.Entry<String, String>> pageNavigation();

  protected abstract String pageTitle();

  protected String helpLink() {
    return null;
  }

  protected String pageSubTitle() {
    return "";
  }

  protected Path pageWorkingDirectory() {
    return null;
  }

  protected String pageTool() { return ""; };

  protected abstract ArrayList<Link> pageBreadcrumb();

  protected abstract String pageContent();
}
