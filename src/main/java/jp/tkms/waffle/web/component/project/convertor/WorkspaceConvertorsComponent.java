package jp.tkms.waffle.web.component.project.convertor;

import jp.tkms.waffle.Main;
import jp.tkms.waffle.data.project.Project;
import jp.tkms.waffle.data.project.convertor.WorkspaceConvertor;
import jp.tkms.waffle.exception.InvalidInputException;
import jp.tkms.waffle.exception.ProjectNotFoundException;
import jp.tkms.waffle.web.component.AbstractAccessControlledComponent;
import jp.tkms.waffle.web.component.ResponseBuilder;
import jp.tkms.waffle.web.component.project.ProjectComponent;
import jp.tkms.waffle.web.component.project.ProjectsComponent;
import jp.tkms.waffle.web.template.Html;
import jp.tkms.waffle.web.template.Link;
import jp.tkms.waffle.web.template.Lte;
import jp.tkms.waffle.web.template.ProjectMainTemplate;
import spark.Spark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Future;

public class WorkspaceConvertorsComponent extends AbstractAccessControlledComponent {
  public static final String WORKSPACE_CONVERTOR = "WorkspaceConvertor";
  public static final String WORKSPACE_CONVERTORS = "WorkspaceConvertors";
  public static final String KEY_CONVERTOR = "convertor";
  public static final String KEY_SCRIPT = "script";
  public static final String KEY_NOTE = "note";

  public enum Mode {Default, AddWorkspaceConvertor}

  private Mode mode;
  private Project project;

  public WorkspaceConvertorsComponent(Mode mode) {
    super();
    this.mode = mode;
  }

  public WorkspaceConvertorsComponent() {
    this(Mode.Default);
  }

  static public void register() {
    Spark.get(getUrl(null), new ResponseBuilder(() -> new WorkspaceConvertorsComponent()));
    Spark.get(getUrl(null, Mode.AddWorkspaceConvertor), new ResponseBuilder(() -> new WorkspaceConvertorsComponent(Mode.AddWorkspaceConvertor)));
    Spark.post(getUrl(null, Mode.AddWorkspaceConvertor), new ResponseBuilder(() -> new WorkspaceConvertorsComponent(Mode.AddWorkspaceConvertor)));

    WorkspaceConvertorComponent.register();
  }

  public static String getUrl(Project project) {
    return ProjectComponent.getUrl(project) + "/" + WorkspaceConvertor.WORKSPACE_CONVERTOR;
  }

  public static String getAnchorLink(Project project) {
    return Html.a(getUrl(project), WORKSPACE_CONVERTORS);
  }

  public static String getUrl(Project project, Mode mode) {
    return getUrl(project) + "/@" + mode.name();
  }

  @Override
  public void controller() throws ProjectNotFoundException {
    project = Project.getInstance(request.params("project"));

    switch (mode) {
      case AddWorkspaceConvertor:
        if (request.requestMethod().toLowerCase().equals("post")) {
          ArrayList<Lte.FormError> errors = checkCreateProjectFormError();
          if (errors.isEmpty()) {
            addWorkspaceConvertor();
          } else {
            renderWorkspaceConvertorAddForm(errors);
          }
        } else {
          renderWorkspaceConvertorAddForm(new ArrayList<>());
        }
        break;
      default:
        renderConvertors();
    }
  }

  private void renderConvertors() throws ProjectNotFoundException {
    new ProjectMainTemplate(project) {
      @Override
      protected String pageTitle() {
        return WORKSPACE_CONVERTORS;
      }

      @Override
      protected ArrayList<Link> pageBreadcrumb() {
        return new ArrayList<>(Arrays.asList(
          ProjectsComponent.getLink(),
          ProjectComponent.getLink(project),
          Link.entry(WORKSPACE_CONVERTORS)
        ));
      }

      @Override
      protected String pageTool() {
        return Html.a(getUrl(project, Mode.AddWorkspaceConvertor),
          null, null, Lte.badge("primary", null, Html.fasIcon("plus-square") + "NEW")
        );
      }

      @Override
      protected String pageContent() {
        ArrayList<Lte.TableValue> headers = new ArrayList<>();
        headers.add(new Lte.TableValue("", "Name"));
        headers.add(new Lte.TableValue("", "Note"));
        headers.add(new Lte.TableValue("", ""));

        String content = Lte.card(null, null,
          getWorkspaceConvertorsTable(project, headers), null, null, "p-0");
        return content;
      }
    }.render(this);
  }

  public static String getWorkspaceConvertorsTable(Project project, ArrayList<Lte.TableValue> headers) {
    return Lte.table("table-nooverflow", new Lte.Table() {
      @Override
      public ArrayList<Lte.TableValue> tableHeaders() {
        return headers;
      }

      @Override
      public ArrayList<Future<Lte.TableRow>> tableRows() {
        ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
        for (WorkspaceConvertor convertor : WorkspaceConvertor.getList(project)) {
          list.add(Main.interfaceThreadPool.submit(() -> {
              Lte.TableRow row = new Lte.TableRow(
                WorkspaceConvertorComponent.getLink(convertor).toHtml(),
                Html.sanitaize(convertor.getNote())
              );
              row.add(new Lte.TableValue("text-align:right;",
                  Html.a(WorkspaceConvertorComponent.getUrl(convertor, WorkspaceConvertorComponent.Mode.Prepare),
                    Html.span("right badge badge-secondary", null, "RUN")
                  )
                )
              );
              return row;
            }
          ));
        }
        return list;
      }
    });
  }

  private void renderWorkspaceConvertorAddForm(ArrayList<Lte.FormError> errors) throws ProjectNotFoundException {
    new ProjectMainTemplate(project) {
      @Override
      protected String pageTitle() {
        return "WorkspaceConvertor";
      }

      @Override
      protected String pageSubTitle() {
        return "(new)";
      }

      @Override
      protected ArrayList<Link> pageBreadcrumb() {
        return new ArrayList<>(Arrays.asList(
          ProjectsComponent.getLink(),
          Link.entry(ProjectComponent.getUrl(project), project.getName()),
          Link.entry(WorkspaceConvertorComponent.WORKSPACE_CONVERTORS)));
      }

      @Override
      protected String pageContent() {
        return
          Html.form(WorkspaceConvertorsComponent.getUrl(project, WorkspaceConvertorsComponent.Mode.AddWorkspaceConvertor), Html.Method.Post,
            Lte.card("New WorkspaceConvertor", null,
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

  private void addWorkspaceConvertor() {
    String name = request.queryParams("name");
    //AbstractConductor abstractConductor = AbstractConductor.getInstance(type);
    WorkspaceConvertor convertor = null;
    try {
      convertor = WorkspaceConvertor.create(project, name);
    } catch (InvalidInputException e) {
      response.redirect(getUrl(project));
      return;
    }
    response.redirect(WorkspaceConvertorComponent.getUrl(convertor));
  }

  private ArrayList<Lte.FormError> checkCreateProjectFormError() {
    return new ArrayList<>();
  }
}
