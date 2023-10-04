package jp.tkms.waffle.web.component.project.workspace.conductor;

import jp.tkms.waffle.data.project.conductor.Conductor;
import jp.tkms.waffle.data.project.workspace.Workspace;
import jp.tkms.waffle.data.project.workspace.conductor.StagedConductor;
import jp.tkms.waffle.web.component.ResponseBuilder;
import jp.tkms.waffle.web.component.project.ProjectComponent;
import jp.tkms.waffle.web.component.project.ProjectsComponent;
import jp.tkms.waffle.web.component.project.conductor.ConductorComponent;
import jp.tkms.waffle.web.component.project.workspace.WorkspaceComponent;
import jp.tkms.waffle.web.component.project.workspace.WorkspacesComponent;
import jp.tkms.waffle.web.template.Html;
import jp.tkms.waffle.web.template.Link;
import spark.Spark;

import java.util.ArrayList;
import java.util.Arrays;

public class StagedConductorComponent extends ConductorComponent {
  public static final String TITLE = "StagedConductor";
  private Workspace workspace;

  public StagedConductorComponent(Mode mode) {
    super(mode);
  }

  public StagedConductorComponent() {
    this(Mode.Default);
  }

  public static void register() {
    Spark.get(getUrl(), new ResponseBuilder(() -> new WorkspaceComponent(WorkspaceComponent.Mode.RedirectToWorkspace)));
    Spark.get(getUrl(null), new ResponseBuilder(() -> new StagedConductorComponent()));
    Spark.get(getUrl(null, Mode.Prepare), new ResponseBuilder(() -> new StagedConductorComponent(Mode.Prepare)));
    Spark.post(getUrl(null, Mode.Run), new ResponseBuilder(() -> new StagedConductorComponent(Mode.Run)));
    Spark.post(getUrl(null, Mode.UpdateArguments), new ResponseBuilder(() -> new StagedConductorComponent(Mode.UpdateArguments)));
    Spark.post(getUrl(null, Mode.UpdateMainScript), new ResponseBuilder(() -> new StagedConductorComponent(Mode.UpdateMainScript)));
    Spark.post(getUrl(null, Mode.UpdateChildScript), new ResponseBuilder(() -> new StagedConductorComponent(Mode.UpdateChildScript)));
    Spark.post(getUrl(null, Mode.NewChildProcedure), new ResponseBuilder(() -> new StagedConductorComponent(Mode.NewChildProcedure)));
  }

  protected static String getUrl() {
    return WorkspaceComponent.getUrl( null) + "/" + Conductor.CONDUCTOR;
  }

  public static String getUrl(StagedConductor conductor) {
    if (conductor == null) {
      return WorkspaceComponent.getUrl( null) + "/" + Conductor.CONDUCTOR + "/:" + KEY_CONDUCTOR;
    } else {
      return WorkspaceComponent.getUrl(conductor.getWorkspace()) + "/" + Conductor.CONDUCTOR + "/" + conductor.getName();
    }
  }

  public static String getUrl(StagedConductor conductor, Mode mode) {
    return getUrl(conductor) + "/@" + mode.name();
  }


  @Override
  protected Conductor getConductorEntity() {
    workspace = Workspace.getInstance(project, request.params(WorkspaceComponent.KEY_WORKSPACE));
    return StagedConductor.getInstance(workspace, request.params(KEY_CONDUCTOR));
  }

  @Override
  protected String renderPageTitle() {
    return TITLE;
  }

  @Override
  protected String renderTool() {
    String value = "";
      /*
    Html.a(getUrl((StagedConductor) conductor, Mode.TestRun),
      Html.span("right badge badge-secondary", null, "TEST RUN")
    );
       */
    Conductor parent = Conductor.getInstance(project, conductor.getName());
    if (parent != null) {
      value += "&nbsp;" + Html.a(ConductorComponent.getUrl(parent),
        Html.span("right badge badge-info", null, Html.fasIcon("layer-group") + "Base Conductor")
      );
    }
    return value;
  }

  @Override
  protected Workspace pageWorkspace() {
    return workspace;
  }

  @Override
  protected ArrayList<Link> renderPageBreadcrumb() {
    return new ArrayList<>(Arrays.asList(
      Link.entry(ProjectsComponent.getUrl(), ProjectComponent.PROJECTS),
      Link.entry(ProjectComponent.getUrl(project), project.getName()),
      Link.entry(WorkspacesComponent.getUrl(project), WorkspaceComponent.WORKSPACES),
      Link.entry(WorkspaceComponent.getUrl(workspace), workspace.getName())
    ));
  }
}
