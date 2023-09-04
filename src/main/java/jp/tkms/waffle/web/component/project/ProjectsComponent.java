package jp.tkms.waffle.web.component.project;

import jp.tkms.waffle.exception.InvalidInputException;
import jp.tkms.waffle.web.AlertCookie;
import jp.tkms.waffle.web.component.AbstractAccessControlledComponent;
import jp.tkms.waffle.web.component.ResponseBuilder;
import jp.tkms.waffle.web.template.Html;
import jp.tkms.waffle.web.template.Link;
import jp.tkms.waffle.web.template.Lte;
import jp.tkms.waffle.web.template.MainTemplate;
import jp.tkms.waffle.data.project.Project;
import spark.Spark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class ProjectsComponent extends AbstractAccessControlledComponent {
  public static final String PROJECTS = "Projects";
  private Mode mode;

  public ProjectsComponent(Mode mode) {
    super();
    this.mode = mode;
  }

  public ProjectsComponent() {
    this(Mode.Default);
  }

  public static void register() {
    Spark.get(getUrl(), new ResponseBuilder(() -> new ProjectsComponent()));
    Spark.get(getUrl(Mode.New), new ResponseBuilder(() -> new ProjectsComponent(Mode.New)));
    Spark.post(getUrl(Mode.New), new ResponseBuilder(() -> new ProjectsComponent(Mode.New)));

    ProjectComponent.register();
  }

  public static String getUrl() {
    return "/PROJECT";
  }

  public static Link getLink() {
    return Link.entry(getUrl(), PROJECTS);
  }

  public static String getUrl(Mode mode) {
    return getUrl() + "/@" + mode.name();
  }

  @Override
  public void controller() {
    if (mode == Mode.New) {
      if (isPost()) {
        ArrayList<Lte.FormError> errors = checkAddFormError();
        if (errors.isEmpty()) {
          addProject();
        } else {
          renderAddForm(errors);
        }
      } else {
        renderAddForm(new ArrayList<>());
      }
    } else {
      renderProjectList();
    }
  }

  private void renderAddForm(ArrayList<Lte.FormError> errors) {
    new MainTemplate() {
      @Override
      protected ArrayList<Map.Entry<String, String>> pageNavigation() {
        return null;
      }

      @Override
      protected String pageTitle() {
        return "Projects";
      }

      @Override
      protected String pageSubTitle() {
        return "@New";
      }

      @Override
      protected ArrayList<Link> pageBreadcrumb() {
        return new ArrayList<>(Arrays.asList(
          Link.entry(getUrl(), "Projects"),
          Link.entry("Add"))
        );
      }

      @Override
      protected String pageContent() {
        return
          Html.form(getUrl(Mode.New), Html.Method.Post,
            Lte.card("New Project", null,
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

  private ArrayList<Lte.FormError> checkAddFormError() {
    return new ArrayList<>();
  }

  private void renderProjectList() {
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
        return "Projects";
      }

      @Override
      protected String helpLink() {
        return "project";
      }

      @Override
      protected ArrayList<Link> pageBreadcrumb() {
        return new ArrayList<>(Arrays.asList(Link.entry("Projects")));
      }

      @Override
      protected String pageTool() {
        return Html.a(getUrl(Mode.New),
          null, null, Lte.badge("primary", null, Html.fasIcon("plus-square") + "NEW")
        );
      }

      @Override
      protected String pageContent() {
        ArrayList<Project> projectList = Project.getList();
        if (projectList.size() <= 0) {
          return Lte.card(null, null,
            Html.a(getUrl(Mode.New), null, null,
              Html.fasIcon("plus-square") + "Add new project"
            ),
            null
          );
        }

        return Lte.card(null, null,
          Lte.table("table-condensed table-nooverflow",
            (list) -> {
              list.add(new Lte.TableValue("", "Name"));
              list.add(new Lte.TableValue("", "Note"));
              return list;
            },
            (list) -> {
              for (Project project : projectList) {
                list.add(() ->
                  new Lte.TableRow(
                    Html.a(ProjectComponent.getUrl(project), null, null, project.getName()),
                    Html.sanitaize(project.getNote())
                  ));
              }
              return list;
            }),
          null, "card-outline card-secondary", "p-0");
      }
    }.render(this);
  }

  private void addProject() {
    String name = request.queryParams("name");
    Project project = null;
    try {
      project = Project.create(name);
    } catch (InvalidInputException e) {
      AlertCookie.putError(response, "The project's name is invalid.");
      response.redirect(ProjectsComponent.getUrl());
      return;
    }
    response.redirect(ProjectComponent.getUrl(project));
  }

  public enum Mode {Default, New}
}
