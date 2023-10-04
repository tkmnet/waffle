package jp.tkms.waffle.web.component.project.conductor;

import jp.tkms.waffle.Main;
import jp.tkms.waffle.data.project.Project;
import jp.tkms.waffle.data.project.conductor.Conductor;
import jp.tkms.waffle.exception.InvalidInputException;
import jp.tkms.waffle.exception.ProjectNotFoundException;
import jp.tkms.waffle.script.ScriptProcessor;
import jp.tkms.waffle.web.Key;
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
import java.util.LinkedHashMap;
import java.util.concurrent.Future;

public class ConductorsComponent extends AbstractAccessControlledComponent {
  public static final String CONDUCTORS = "Conductors";

  public enum Mode {Default, AddConductor}

  public static LinkedHashMap<String, String> procedureTypes = new LinkedHashMap<>() {
    {
      ScriptProcessor.CLASS_NAME_MAP.forEach((k, v) -> put(k, ScriptProcessor.getDescription(v)));
    }
  };

  private Mode mode;
  protected Project project;
  //protected Conductor conductor;
  //private ActorRun parent;
  //private SimulatorRun baseRun;
  public ConductorsComponent(Mode mode) {
    super();
    this.mode = mode;
  }

  public ConductorsComponent() {
    this(Mode.Default);
  }

  static public void register() {
    Spark.get(getUrl(null), new ResponseBuilder(() -> new ConductorsComponent()));
    Spark.get(getUrl(null, Mode.AddConductor), new ResponseBuilder(() -> new ConductorsComponent(Mode.AddConductor)));
    Spark.post(getUrl(null, Mode.AddConductor), new ResponseBuilder(() -> new ConductorsComponent(Mode.AddConductor)));

    ConductorComponent.register();
  }

  public static String getUrl(Project project) {
    return ProjectComponent.getUrl(project) + "/" + Conductor.CONDUCTOR;
  }

  public static Link getLink(Project project) {
    return Link.entry(getUrl(project), CONDUCTORS);
  }

  public static String getUrl(Project project, Mode mode) {
    return getUrl(project) + "/@" + mode.name();
  }

  @Override
  public void controller() throws ProjectNotFoundException {
    project = Project.getInstance(request.params("project"));

    switch (mode){
      case AddConductor:
        if (request.requestMethod().toLowerCase().equals("post")) {
          ArrayList<Lte.FormError> errors = checkCreateProjectFormError();
          if (errors.isEmpty()) {
            addConductor();
          } else {
            renderConductorAddForm(errors);
          }
        } else {
          renderConductorAddForm(new ArrayList<>());
        }
        break;
      default:
        renderConductors();
    }
  }

  private void renderConductors() throws ProjectNotFoundException {
    new ProjectMainTemplate(project) {
      @Override
      protected String pageTitle() {
        return CONDUCTORS;
      }

      @Override
      protected ArrayList<Link> pageBreadcrumb() {
        return new ArrayList<>(Arrays.asList(
          ProjectsComponent.getLink(),
          ProjectComponent.getLink(project),
          ConductorsComponent.getLink(project)
        ));
      }

      @Override
      protected String pageTool() {
        return Html.a(getUrl(project, Mode.AddConductor),
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
          getConductorsTable(project, headers), null, "card-warning card-outline", "p-0");
        return content;
      }
    }.render(this);
  }

  public static String getConductorsTable(Project project, ArrayList<Lte.TableValue> headers) {
    return Lte.table("table-nooverflow", new Lte.Table() {
      @Override
      public ArrayList<Lte.TableValue> tableHeaders() {
        return headers;
      }

      @Override
      public ArrayList<Future<Lte.TableRow>> tableRows() {
        ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
        for (Conductor conductor : Conductor.getList(project)) {
          list.add(Main.interfaceThreadPool.submit(() -> {
              Lte.TableRow row = new Lte.TableRow(
                ConductorComponent.getLink(conductor).toHtml(),
                Html.sanitaize(conductor.getNote())
              );
              row.add(new Lte.TableValue("text-align:right;",
                Html.a(ConductorComponent.getUrl(conductor, ConductorComponent.Mode.Prepare),
                  Html.span("right badge badge-secondary", null, "RUN")
                )
              ));
              return row;
            }
          ));
        }
        return list;
      }
    });
  }

  private void addConductor() {
    String name = request.queryParams(Key.NAME);
    String defaultProcedureType = request.queryParams(Key.PROCEDURE_TYPE);
    Conductor conductor = null;
    try {
      conductor = Conductor.create(project, name, defaultProcedureType);
    } catch (InvalidInputException e) {
      response.redirect(getUrl(project));
      return;
    }
    response.redirect(ConductorComponent.getUrl(conductor));
  }

  private void renderConductorAddForm(ArrayList<Lte.FormError> errors) throws ProjectNotFoundException {
    new ProjectMainTemplate(project) {
      @Override
      protected String pageTitle() {
        return "Conductor";
      }

      @Override
      protected String pageSubTitle() {
        return "(new)";
      }

      @Override
      protected ArrayList<Link> pageBreadcrumb() {
        return new ArrayList<>(Arrays.asList(
          ProjectsComponent.getLink(),
          ProjectComponent.getLink(project),
          ConductorsComponent.getLink(project),
          Link.entry("(new)")));
      }

      @Override
      protected String pageContent() {
        return
          Html.form(getUrl(project, Mode.AddConductor), Html.Method.Post,
            Lte.card("New Conductor", null,
              Html.div(null,
                Html.inputHidden("cmd", "add"),
                Lte.formInputGroup("text", Key.NAME, "Name", "Name", null, errors),
                Lte.formSelectGroup(Key.PROCEDURE_TYPE, "Default Procedure Type", procedureTypes, errors)
              ),
              Lte.formSubmitButton("success", "Add"),
              "card-warning", null
            )
          );
      }
    }.render(this);
  }

  private ArrayList<Lte.FormError> checkCreateProjectFormError() {
    return new ArrayList<>();
  }
}
