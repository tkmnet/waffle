package jp.tkms.waffle.web.component.project;

import jp.tkms.waffle.data.project.conductor.Conductor;
import jp.tkms.waffle.web.component.*;
import jp.tkms.waffle.web.component.project.conductor.ConductorComponent;
import jp.tkms.waffle.web.component.project.conductor.ConductorsComponent;
import jp.tkms.waffle.web.component.project.convertor.WorkspaceConvertorComponent;
import jp.tkms.waffle.web.component.project.convertor.WorkspaceConvertorsComponent;
import jp.tkms.waffle.web.component.project.executable.ExecutablesComponent;
import jp.tkms.waffle.web.component.project.workspace.WorkspaceComponent;
import jp.tkms.waffle.web.component.project.workspace.WorkspacesComponent;
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

public class ProjectComponent extends AbstractAccessControlledComponent {
  public static final String TITLE = "Project";
  public static final String PROJECTS = "Projects";
  public static final String KEY_PROJECT = "project";
  public static final String KEY_NOTE = "note";

  public enum Mode {Default, NotFound, UpdateNote}
  Mode mode;

  private String requestedId;
  private Project project;
  public ProjectComponent(Mode mode) {
    super();
    this.mode = mode;
  }

  public ProjectComponent() {
    this(Mode.Default);
  }

  static public void register() {
    Spark.get(getUrl(null), new ResponseBuilder(() -> new ProjectComponent()));
    Spark.post(getUrl(null, Mode.UpdateNote), new ResponseBuilder(() -> new ProjectComponent(Mode.UpdateNote)));

    ExecutablesComponent.register();
    ConductorsComponent.register();
    WorkspacesComponent.register();
    WorkspaceConvertorsComponent.register();
  }

  public static String getUrl(Project project) {
    return "/" + Project.PROJECT + "/" + (project == null ? ':' + KEY_PROJECT : project.getName());
  }

  public static Link getLink(Project project) {
    return Link.entry(getUrl(project), Html.fasIcon("folder-open") + project.getName());
  }

  public static String getUrl(Project project, Mode mode) {
    return getUrl(project) + "/@" + mode.name();
  }

  @Override
  public void controller() throws ProjectNotFoundException {
    Mode mode = this.mode;

    requestedId = request.params(KEY_PROJECT);
    project = Project.getInstance(requestedId);

    if (project == null) {
      mode = Mode.NotFound;
    }

    switch (mode) {
      case Default:
        renderProject();
        break;
      case UpdateNote:
        project.setNote(request.queryParams(KEY_NOTE));
        response.redirect(getUrl(project));
        break;
      case NotFound:
        renderProjectNotFound();
        break;
    }
  }

  private void renderProjectNotFound() throws ProjectNotFoundException {
    new ProjectMainTemplate(project) {
      @Override
      protected String pageTitle() {
        return "[" + requestedId + "]";
      }

      @Override
      protected ArrayList<Link> pageBreadcrumb() {
        return new ArrayList<>(Arrays.asList(
          Link.entry(ProjectsComponent.getUrl(), "Projects"),
          Link.entry("NotFound")));
      }

      @Override
      protected String pageContent() {
        ArrayList<Project> projectList = Project.getList();
        return Lte.card(null, null,
          Html.h1("text-center", Html.fasIcon("question")),
          null
        );
      }
    }.render(this);
  }

  private void renderProject() throws ProjectNotFoundException {
    new ProjectMainTemplate(project) {
      @Override
      protected String pageTitle() {
        return TITLE;
      }

      @Override
      protected ArrayList<Link> pageBreadcrumb() {
        return new ArrayList<>(Arrays.asList(
          null,
          ProjectsComponent.getLink(),
          ProjectComponent.getLink(project)
          ));
      }

      @Override
      protected Path pageWorkingDirectory() {
        return project.getPath();
      }

      @Override
      protected String pageContent() {
        String contents = Html.javascript("sessionStorage.setItem('latest-project-id','" + project.getName() + "');sessionStorage.setItem('latest-project-name','" + project.getName() + "');");

        contents += Lte.card(Html.fasIcon("folder-open") + project.getName(), null,
          Html.div(null,
            Lte.readonlyTextInputWithCopyButton("Project Directory", project.getPath().toAbsolutePath().toString())
          )
          , Lte.divRow(
            //Lte.infoBox(Lte.DivSize.F12Md12Sm6, "project-diagram", "bg-danger",
            //  Html.a(RunsComponent.getUrl(project), "Runs"), ""),
            Lte.divCol(Lte.DivSize.F12Md12Sm6,
              Lte.button(WorkspacesComponent.getUrl(project), Lte.Color.Outline_Danger, true,
                Html.fasIcon("table") + WorkspaceComponent.WORKSPACES)
            ),
            Html.div("d-inline d-md-none small", "&nbsp;"),
            Lte.divCol(Lte.DivSize.F12Md12Sm6,
              Lte.button(ExecutablesComponent.getUrl(project), Lte.Color.Outline_Info, true,
                Html.fasIcon("layer-group") + ExecutablesComponent.EXECUTABLES)
            ),
            Html.br()
          )
          , "card-secondary", null);

        ArrayList<Conductor> conductorList = Conductor.getList(project);
        if (conductorList.size() <= 0) {
          contents += Lte.card(Html.fasIcon("user-tie") + "Conductors",
            null,
            Html.a(ConductorsComponent.getUrl(project, ConductorsComponent.Mode.AddConductor), null, null,
              Html.fasIcon("plus-square") + "Add Conductors"
            ),
            null, "card-warning card-outline", null
          );
        } else {
          //ArrayList<ActorRun> notFinishedList = new ArrayList<>();
          /*
          for (Actor notFinished : Actor.getNotFinishedList(project)) {
            if (!notFinished.isRoot()) {
              if (notFinished.getParentActor() != null && notFinished.getParentActor().isRoot()) {
                notFinishedList.add(notFinished);
              }
            }
          }

          content += Html.element("script", new Attributes(value("type", "text/javascript")),
              "var updateConductorJobNum = function(c,n) {" +
              "if (n > 0) {" +
              "document.getElementById('conductor-jobnum-' + c).style.display = 'inline-block';" +
              "document.getElementById('conductor-jobnum-' + c).innerHTML = n;" +
              "} else {" +
              "document.getElementById('conductor-jobnum-' + c).style.display = 'none';" +
              "}" +
              "};"
          );
           */

          contents += Lte.card(Html.fasIcon("user-tie") + ConductorComponent.CONDUCTORS,
            Html.a(ConductorsComponent.getUrl(project, ConductorsComponent.Mode.AddConductor),
              null, null, Lte.badge("primary", null, Html.fasIcon("plus-square") + "NEW")
            ),
            ConductorsComponent.getConductorsTable(project, null),
            null, "card-warning card-outline", "p-0");
        }

        contents += Lte.card(Html.fasIcon("broom") + WorkspaceConvertorComponent.WORKSPACE_CONVERTORS,
          Html.a(WorkspaceConvertorsComponent.getUrl(project, WorkspaceConvertorsComponent.Mode.AddWorkspaceConvertor),
            null, null, Lte.badge("primary", null, Html.fasIcon("plus-square") + "NEW")
          ),
          WorkspaceConvertorsComponent.getWorkspaceConvertorsTable(project, null),
          null, "card-outline", "p-0");

        contents +=
          Html.form(getUrl( project, Mode.UpdateNote), Html.Method.Post,
            Lte.card(Html.fasIcon("sticky-note") + "Note",null,
              Lte.divRow(
                Lte.divCol(Lte.DivSize.F12,
                  Lte.formTextAreaGroup(KEY_NOTE, null, project.getNote(), null)
                )
              )
              ,Lte.formSubmitButton("success", "Update")
              , null, null)
          );

        return contents;
      }
    }.render(this);
  }
}
