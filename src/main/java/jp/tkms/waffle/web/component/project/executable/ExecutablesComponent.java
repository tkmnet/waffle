package jp.tkms.waffle.web.component.project.executable;

import jp.tkms.waffle.Main;
import jp.tkms.waffle.data.project.executable.Executable;
import jp.tkms.waffle.web.component.AbstractAccessControlledComponent;
import jp.tkms.waffle.web.component.ResponseBuilder;
import jp.tkms.waffle.web.component.project.ProjectComponent;
import jp.tkms.waffle.web.component.project.ProjectsComponent;
import jp.tkms.waffle.web.template.Html;
import jp.tkms.waffle.web.template.Link;
import jp.tkms.waffle.web.template.Lte;
import jp.tkms.waffle.web.template.ProjectMainTemplate;
import jp.tkms.waffle.data.project.Project;
import jp.tkms.waffle.exception.ProjectNotFoundException;
import spark.Spark;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Future;

public class ExecutablesComponent extends AbstractAccessControlledComponent {
  public static final String EXECUTABLES = "Executables";

  private Mode mode;

  private String requestedId;
  private Project project;
  public ExecutablesComponent(Mode mode) {
    super();
    this.mode = mode;
  }

  public ExecutablesComponent() {
    this(Mode.Default);
  }

  static public void register() {
    Spark.get(getUrl(null), new ResponseBuilder(() -> new ExecutablesComponent()));
    Spark.get(getUrl(null, Mode.New), new ResponseBuilder(() -> new ExecutablesComponent(Mode.New)));
    Spark.post(getUrl(null, Mode.New), new ResponseBuilder(() -> new ExecutablesComponent(Mode.New)));

    ExecutableComponent.register();
  }

  public static String getUrl(Project project) {
    return ProjectComponent.getUrl(project) + "/" + Executable.EXECUTABLE;
  }

  public static String getUrl(Project project, Mode mode) {
    return getUrl(project) + "/@" + mode.name();
  }

  public static Link getLink(Project project) {
    return Link.entry(getUrl(project), EXECUTABLES);
  }

  @Override
  public void controller() throws ProjectNotFoundException {
    requestedId = request.params("project");
    project = Project.getInstance(requestedId);

    if (mode == Mode.New) {
      if (request.requestMethod().toLowerCase().equals("post")) {
        ArrayList<Lte.FormError> errors = checkCreateProjectFormError();
        if (errors.isEmpty()) {
          addSimulator();
        } else {
          renderAddForm(errors);
        }
      } else {
        renderAddForm(new ArrayList<>());
      }
    } else {
      renderExecutableList();
    }
  }

  private void renderAddForm(ArrayList<Lte.FormError> errors) throws ProjectNotFoundException {
    new ProjectMainTemplate(project) {
      @Override
      protected String pageTitle() {
        return "Executables";
      }

      @Override
      protected String pageSubTitle() {
        return "@New";
      }

      @Override
      protected ArrayList<Link> pageBreadcrumb() {
        return new ArrayList<>(Arrays.asList(
          Link.entry(ProjectsComponent.getUrl(), "Projects"),
          Link.entry(ProjectComponent.getUrl(project), project.getName()),
          Link.entry(ExecutablesComponent.getUrl(project), EXECUTABLES)));
      }

      @Override
      protected String pageContent() {
        return
          Html.form(getUrl(project, Mode.New), Html.Method.Post,
            Lte.card("New Executable", null,
              Html.div(null,
                Html.inputHidden("cmd", "add"),
                Lte.formInputGroup("text", "name", null, "Name", null, errors)
              ),
              Lte.formSubmitButton("success", "Add"),
              "card-warning", null
            )
          );
      }
    }.render(this);
  }

  private void addSimulator() {
    Executable executable = Executable.create(project, request.queryParams("name"));
    executable.setCommand("");
    response.redirect(ExecutableComponent.getUrl(executable));
  }

  private ArrayList<Lte.FormError> checkCreateProjectFormError() {
    return new ArrayList<>();
  }

  private void renderExecutableList() throws ProjectNotFoundException {
    new ProjectMainTemplate(project) {
      @Override
      protected String pageTitle() {
        return "Executables";
      }

      @Override
      protected Path pageWorkingDirectory() {
        return Executable.getBaseDirectoryPath(project);
      }

      @Override
      protected ArrayList<Link> pageBreadcrumb() {
        return new ArrayList<>(Arrays.asList(
          ProjectsComponent.getLink(),
          ProjectComponent.getLink(project),
          ExecutablesComponent.getLink(project)));
      }

      @Override
      protected String pageTool() {
        return Html.a(getUrl(project, Mode.New),
          null, null, Lte.badge("primary", null, Html.fasIcon("plus-square") + "NEW")
        );
      }

      @Override
      protected String pageContent() {
        ArrayList<Executable> executableList = Executable.getList(project);
        if (executableList.size() <= 0) {
          return Lte.card(null, null,
            Html.a(getUrl(project, Mode.New), null, null,
              Html.fasIcon("plus-square") + "Add Executable"
            ),
            null
          );
        }
        return Lte.card(null, null,
          Lte.table("table-condensed table-nooverflow", new Lte.Table() {
            @Override
            public ArrayList<Lte.TableValue> tableHeaders() {
              ArrayList<Lte.TableValue> headers = new ArrayList<>();
              headers.add(new Lte.TableValue("", "Name"));
              headers.add(new Lte.TableValue("", "Note"));
              headers.add(new Lte.TableValue("", ""));
              return headers;
            }

            @Override
            public ArrayList<Future<Lte.TableRow>> tableRows() {
              ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
              for (Executable executable : executableList) {
                list.add(Main.interfaceThreadPool.submit(() -> {
                    Lte.TableRow row =  new Lte.TableRow(
                      Html.a(ExecutableComponent.getUrl(executable), null, null, executable.getName()),
                      Html.sanitaize(executable.getNote())
                    );
                    row.add(new Lte.TableValue("text-align:right;",
                      Html.a(ExecutableComponent.getUrl(executable, ExecutableComponent.Mode.TestRun),
                        Html.span("right badge badge-secondary", null, "TEST RUN")
                      )
                    ));
                    return row;
                  }
                ));
              }
              return list;
            }
          })
          , null, "card-info card-outline", "p-0");
      }
    }.render(this);
  }

  public enum Mode {Default, New}
}
