package jp.tkms.waffle.web.component.project.workspace;

  import jp.tkms.waffle.Main;
  import jp.tkms.waffle.data.project.workspace.HasLocalPath;
  import jp.tkms.waffle.data.project.workspace.Workspace;
  import jp.tkms.waffle.data.project.workspace.conductor.StagedConductor;
  import jp.tkms.waffle.data.project.workspace.executable.StagedExecutable;
  import jp.tkms.waffle.data.project.workspace.run.AbstractRun;
  import jp.tkms.waffle.data.project.workspace.run.ConductorRun;
  import jp.tkms.waffle.data.util.WrappedJson;
  import jp.tkms.waffle.web.component.AbstractAccessControlledComponent;
  import jp.tkms.waffle.web.component.ResponseBuilder;
  import jp.tkms.waffle.web.component.project.ProjectComponent;
  import jp.tkms.waffle.web.component.project.ProjectsComponent;
  import jp.tkms.waffle.web.component.project.conductor.ConductorComponent;
  import jp.tkms.waffle.web.component.project.executable.ExecutablesComponent;
  import jp.tkms.waffle.web.component.project.workspace.conductor.StagedConductorComponent;
  import jp.tkms.waffle.web.component.project.workspace.executable.StagedExecutableComponent;
  import jp.tkms.waffle.web.component.project.workspace.run.RunComponent;
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

public class WorkspaceComponent extends AbstractAccessControlledComponent {
  public static final String WORKSPACES = "Workspaces";
  public static final String WORKSPACE = "Workspace";
  public static final String KEY_WORKSPACE = "workspace";
  public static final String KEY_NOTE = "note";

  public enum Mode {Default, RedirectToWorkspace, UpdateNote, Abort, GetRecord}
  private Mode mode;

  private Project project;
  private Workspace workspace;

  public WorkspaceComponent(Mode mode) {
    this.mode = mode;
  }

  public WorkspaceComponent() {
    this(Mode.Default);
  }

  static public void register() {
    Spark.get(getUrl(null), new ResponseBuilder(() -> new WorkspaceComponent()));
    Spark.post(getUrl(null, Mode.UpdateNote), new ResponseBuilder(() -> new WorkspaceComponent(Mode.UpdateNote)));
    Spark.get(getUrl(null, Mode.Abort), new ResponseBuilder(() -> new WorkspaceComponent(Mode.Abort)));
    Spark.get(getUrl(null, Mode.GetRecord), new ResponseBuilder(() -> new WorkspaceComponent(Mode.GetRecord)));

    RunComponent.register();
    StagedExecutableComponent.register();
    StagedConductorComponent.register();
  }

  public static Link getLink(Workspace workspace) {
    return Link.entry(getUrl(workspace), Html.fasIcon("table") + workspace.getName());
  }

  public static String getUrl(Workspace workspace) {
    if (workspace == null) {
      return ProjectComponent.getUrl(null) + "/" + Workspace.WORKSPACE + "/" + ':' + KEY_WORKSPACE;
    } else {
      return ProjectComponent.getUrl(workspace.getProject()) + "/" + Workspace.WORKSPACE + "/" + workspace.getName();
    }
  }

  public static String getUrl(Workspace workspace, Mode mode) {
    if (workspace == null) {
      return ProjectComponent.getUrl(null) + "/" + Workspace.WORKSPACE + "/" + ':' + KEY_WORKSPACE + "/@" + mode.name();
    } else {
      return ProjectComponent.getUrl(workspace.getProject()) + "/" + Workspace.WORKSPACE + "/" + workspace.getName() + "/@" + mode.name();
    }
  }

  @Override
  public void controller() throws ProjectNotFoundException {
    project = Project.getInstance(request.params(ProjectComponent.KEY_PROJECT));
    workspace = Workspace.getInstance(project, request.params(KEY_WORKSPACE));

    switch (mode) {
      case UpdateNote:
        workspace.setNote(request.queryParams(KEY_NOTE));
        response.redirect(getUrl(workspace));
        break;
      case Abort:
        workspace.abort();
        response.redirect(getUrl(workspace));
        break;
      case RedirectToWorkspace:
        response.redirect(getUrl(workspace));
        break;
      case GetRecord:
        response.body(workspace.getChildStateRecord(Integer.valueOf(request.queryParams("pointer"))));
        break;
      default:
        renderWorkspace();
    }
  }

  private void renderWorkspace() throws ProjectNotFoundException {
    new ProjectMainTemplate(project) {
      @Override
      protected String pageTitle() {
        return WORKSPACE;
      }

      @Override
      protected ArrayList<Link> pageBreadcrumb() {
        return new ArrayList<>(Arrays.asList(
          ProjectsComponent.getLink(),
          ProjectComponent.getLink(project),
          WorkspacesComponent.getLink(project),
          WorkspaceComponent.getLink(workspace)
        ));
      }

      @Override
      protected Path pageWorkingDirectory() {
        return workspace.getPath();
      }

      @Override
      protected Workspace pageWorkspace() {
        return workspace;
      }

      @Override
      protected String pageContent() {
        String contents = Html.form(getUrl(workspace, Mode.UpdateNote), Html.Method.Post,
          Lte.card(Html.fasIcon("table") + workspace.getName(), null,
            Html.div(null,
              Lte.readonlyTextInputWithCopyButton("Workspace Directory", workspace.getPath().toAbsolutePath().toString()),
              Lte.formTextAreaGroup(KEY_NOTE, "Note", workspace.getNote(), null)
            ),
            Lte.formSubmitButton("success", "Update"), "card-danger", null));

        if (workspace.hasChildStateRecord()) {
          contents += Lte.card("Status", null,
            Html.div("workspace-status-record", Html.canvas("workspace-status-chart")), null);
          contents += Html.javascript("const ctx = document.getElementById('workspace-status-chart');" +
            "workspaceStatusChart = new Chart(ctx, {" +
            "type: 'scatter', data: {labels: []," +
            "      datasets: [" +
            "{\n" + "        label: 'Created',\n" + "borderColor: '#282c34A0'," + "pointStyle: false, showLine: true, data: [],}," +
            "{\n" + "        label: 'Submitted',\n" + "borderColor: '#ffc107A0'," + "pointStyle: false, showLine: true, data: [],}," +
            "{\n" + "        label: 'Finished',\n" + "borderColor: '#28a745A0'," + "pointStyle: false, showLine: true, data: [],}," +
            "{\n" + "        label: 'Excepted'," + "borderColor: '#e31a1cA0'," + "pointStyle: false, showLine: true, data: [],}," +
            "]\n" +
            "    },\n" +
            "options: {\n" +
            "    maintainAspectRatio: false,\n" +
            "    scales:{ x: {type:'linear', min:0, max:1} },\n" +
            "    plugins: {\n" + "      legend: {\n" + "        position: 'top',\n" + "      }\n" + "    }\n" +
            "  }," +
            "  });" +
            "workspaceStatusRecordPointer = 0;" +
            "addWorkspaceStatusRecord = function(lines) {" +
            "data = workspaceStatusChart.data;" +
            "lines.trim().split('\\n').forEach(line=>{" +
            "if (line == '') {return;}" +
            "elements = line.split(',');" +
            "time = Number(elements[0]);" +
            "data.labels.push(time);" +
            "workspaceStatusChart.options.scales.x.max = time;" +
            "for(i=0; i<4; i+=1){data.datasets[i].data.push(elements[i + 1]);}" +
            "workspaceStatusRecordPointer += 1;" +
            "});" +
            "workspaceStatusChart.update();" +
            "};" +
            "workspaceStatusRecordUpdate = function(){simpleget('" + getUrl(workspace, Mode.GetRecord) + "?pointer=' + workspaceStatusRecordPointer, v=>{addWorkspaceStatusRecord(v);});};" +
            "setInterval(workspaceStatusRecordUpdate, 5000);" +
            "workspaceStatusRecordUpdate();" +
            "");
        }

        contents += Lte.divRow(
          Lte.divCol(Lte.DivSize.F12Md12Sm6, Lte.card(Html.fasIcon("user-tie") + "Staged" + ConductorComponent.CONDUCTORS, null,
            Lte.table("table-condensed", new Lte.Table() {
              @Override
              public ArrayList<Lte.TableValue> tableHeaders() {
                return null;
              }

              @Override
              public ArrayList<Future<Lte.TableRow>> tableRows() {
                ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
                for (StagedConductor conductor : StagedConductor.getList(workspace)) {
                  list.add(Main.interfaceThreadPool.submit(() -> {
                      return new Lte.TableRow(
                        Html.a(StagedConductorComponent.getUrl(conductor), null, null, conductor.getName()));
                    }
                  ));
                }
                if (list.isEmpty()) {
                  list.add(Main.interfaceThreadPool.submit(() -> {
                      return new Lte.TableRow(new Lte.TableValue("text-align:center;color:silver;", Html.fasIcon("receipt") + "Empty"));
                    }
                  ));
                }
                return list;
              }
            })
            , null, "card-warning card-outline", "p-0")),
          Lte.divCol(Lte.DivSize.F12Md12Sm6, Lte.card(Html.fasIcon("layer-group") + "Staged" + ExecutablesComponent.EXECUTABLES, null,
            Lte.table("table-condensed", new Lte.Table() {
              @Override
              public ArrayList<Lte.TableValue> tableHeaders() {
                return null;
              }

              @Override
              public ArrayList<Future<Lte.TableRow>> tableRows() {
                ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
                for (StagedExecutable executable : StagedExecutable.getList(workspace)) {
                  list.add(Main.interfaceThreadPool.submit(() -> {
                      return new Lte.TableRow(
                        Html.a(StagedExecutableComponent.getUrl(executable), null, null, executable.getName()));
                    }
                  ));
                }
                if (list.isEmpty()) {
                  list.add(Main.interfaceThreadPool.submit(() -> {
                      return new Lte.TableRow(new Lte.TableValue("text-align:center;color:silver;", Html.fasIcon("receipt") + "Empty"));
                    }
                  ));
                }
                return list;
              }
            }),
            null, "card-info card-outline", "p-0"))
        );

        contents += Lte.card(Html.fasIcon("project-diagram") + RunComponent.RUNS, null,
          Lte.table("table-condensed table-sm", new Lte.Table() {
            @Override
            public ArrayList<Lte.TableValue> tableHeaders() {
              return null;
            }

            @Override
            public ArrayList<Future<Lte.TableRow>> tableRows() {
              ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
              for (HasLocalPath localPath : AbstractRun.getDirectoryList(workspace)) {
                list.add(Main.interfaceThreadPool.submit(() -> {
                    return new Lte.TableRow(
                      Html.a(RunComponent.getUrlFromLocalPath(localPath), null, null, localPath.getPath().getFileName().toString()));
                        /*
                          if (abstractRun instanceof ExecutableRun) {
                            return new Lte.TableRow(
                              Html.a(RunComponent.getUrl(abstractRun), null, null, abstractRun.getName()));
                          } else {
                            return new Lte.TableRow(
                              Html.a(RunComponent.getUrl(abstractRun), null, null, abstractRun.getName()));
                          }
                         */
                  }
                ));
              }
              if (list.isEmpty()) {
                list.add(Main.interfaceThreadPool.submit(() -> {
                    return new Lte.TableRow(new Lte.TableValue("text-align:center;color:silver;", Html.fasIcon("receipt") + "Empty"));
                  }
                ));
              }
              return list;
            }
          })
          , null, "card-outline", "p-0");

        WrappedJson conductorStartupVariables = ConductorRun.getInstance(workspace, RunComponent.getUrlFromPath(AbstractRun.getBaseDirectoryPath(workspace))).getStartupVariables();
        if (conductorStartupVariables != null) {
          contents += Lte.card(Html.fasIcon("list-ol") + "Startup Variables",
            Lte.cardToggleButton(false),
            Lte.divRow(
              Lte.divCol(Lte.DivSize.F12,
                Lte.formJsonEditorGroup(ConductorRun.KEY_STARTUP_VARIABLES, null, "tree", conductorStartupVariables.toString(), null)
              )
            ), null,
            "collapsed-card.stop card-secondary card-outline", null);
        }

        String scriptLog = workspace.getScriptOutput();
        contents += Lte.divRow(
              Html.section("col-lg-12",
                Lte.card(Html.fasIcon("sticky-note") + "Script Output", Lte.cardToggleButton(false),
                  (scriptLog.equals("") ?
                    Html.element("div",
                      new Html.Attributes(Html.value("style", "text-align:center;color:silver;")), Html.fasIcon("receipt") + "Empty")
                    : Lte.readonlyTextAreaGroup("log", null, scriptLog) )
                  , null, "card-secondary card-outline", null)
              )
            );

        return contents;
      }
    }.render(this);
  }
}
