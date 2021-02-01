package jp.tkms.waffle.web.component.project.workspace;

  import jp.tkms.waffle.Main;
  import jp.tkms.waffle.data.project.conductor.Conductor;
  import jp.tkms.waffle.data.project.executable.Executable;
  import jp.tkms.waffle.data.project.workspace.Workspace;
  import jp.tkms.waffle.data.project.workspace.executable.StagedExecutable;
  import jp.tkms.waffle.web.component.AbstractAccessControlledComponent;
  import jp.tkms.waffle.web.component.project.ProjectComponent;
  import jp.tkms.waffle.web.component.project.ProjectsComponent;
  import jp.tkms.waffle.web.component.project.conductor.ConductorComponent;
  import jp.tkms.waffle.web.component.project.executable.ExecutableComponent;
  import jp.tkms.waffle.web.component.project.executable.ExecutablesComponent;
  import jp.tkms.waffle.web.component.project.workspace.executable.StagedExecutableComponent;
  import jp.tkms.waffle.web.component.project.workspace.run.RunComponent;
  import jp.tkms.waffle.web.component.project.workspace.run.RunsComponent;
  import jp.tkms.waffle.web.template.Html;
  import jp.tkms.waffle.web.template.Lte;
  import jp.tkms.waffle.web.template.ProjectMainTemplate;
  import jp.tkms.waffle.data.project.Project;
  import jp.tkms.waffle.exception.ProjectNotFoundException;
  import spark.Spark;

  import java.util.ArrayList;
  import java.util.Arrays;
  import java.util.concurrent.Future;

public class WorkspaceComponent extends AbstractAccessControlledComponent {
  public static final String WORKSPACES = "Workspaces";
  public static final String WORKSPACE = "Workspaces";
  public static final String KEY_WORKSPACE = "workspace";

  private Project project;
  private Workspace workspace;

  public WorkspaceComponent() {
  }

  static public void register() {
    Spark.get(getUrl(null), new WorkspaceComponent());
    Spark.get(getUrl(null, null), new WorkspaceComponent());

    StagedExecutableComponent.register();
    RunsComponent.register();
    RunComponent.register();
  }

  public static String getUrl(Project project) {
    return ProjectComponent.getUrl(project) + "/" + Workspace.WORKSPACE;
  }

  public static String getUrl(Project project, Workspace workspace) {
    return ProjectComponent.getUrl(project) + "/" + Workspace.WORKSPACE + "/" + (workspace == null ? ':' + KEY_WORKSPACE : workspace.getName());
  }

  @Override
  public void controller() throws ProjectNotFoundException {
    project = Project.getInstance(request.params(ProjectComponent.KEY_PROJECT));
    String workspaceName = request.params(KEY_WORKSPACE);
    if (workspaceName == null) {
      renderWorkspaceList();
    } else {
      workspace = Workspace.getInstance(project, request.params(KEY_WORKSPACE));
      renderWorkspace();
    }
  }

  private void renderWorkspaceList() throws ProjectNotFoundException {
    new ProjectMainTemplate(project) {
      @Override
      protected String pageTitle() {
        return WORKSPACES;
      }

      @Override
      protected ArrayList<String> pageBreadcrumb() {
        return new ArrayList<String>(Arrays.asList(
          Html.a(ProjectsComponent.getUrl(), "Projects"),
          Html.a(ProjectComponent.getUrl(project), project.getName())));
      }

      @Override
      protected String pageContent() {
        ArrayList<Workspace> workspaceList = Workspace.getList(project);
        /*
        if (executableList.size() <= 0) {
          return Lte.card(null, null,
            Html.a(getUrl(project, jp.tkms.waffle.web.component.project.executable.ExecutablesComponent.Mode.New), null, null,
              Html.fasIcon("plus-square") + "Add Executable"
            ),
            null
          );
        }
         */
        return Lte.card(null, null,
          Lte.table("table-condensed", new Lte.Table() {
            @Override
            public ArrayList<Lte.TableValue> tableHeaders() {
              ArrayList<Lte.TableValue> list = new ArrayList<>();
              list.add(new Lte.TableValue("", "Name"));
              return list;
            }

            @Override
            public ArrayList<Future<Lte.TableRow>> tableRows() {
              ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
              for (Workspace workspace : workspaceList) {
                list.add(Main.interfaceThreadPool.submit(() -> {
                    return new Lte.TableRow(
                      Html.a(WorkspaceComponent.getUrl(project, workspace), null, null, workspace.getName()));
                  }
                ));
              }
              return list;
            }
          })
          , null, "card-danger card-outline", "p-0");
      }
    }.render(this);
  }

  private void renderWorkspace() throws ProjectNotFoundException {
    new ProjectMainTemplate(project) {
      @Override
      protected String pageTitle() {
        return workspace.getName();
      }

      @Override
      protected String pageSubTitle() {
        return WORKSPACE;
      }

      @Override
      protected ArrayList<String> pageBreadcrumb() {
        return new ArrayList<String>(Arrays.asList(
          Html.a(ProjectsComponent.getUrl(), "Projects"),
          Html.a(ProjectComponent.getUrl(project), project.getName()),
          Html.a(WorkspaceComponent.getUrl(project), WORKSPACES)
        ));
      }

      @Override
      protected String pageContent() {
        ArrayList<Workspace> workspaceList = Workspace.getList(project);
        /*
        if (executableList.size() <= 0) {
          return Lte.card(null, null,
            Html.a(getUrl(project, jp.tkms.waffle.web.component.project.executable.ExecutablesComponent.Mode.New), null, null,
              Html.fasIcon("plus-square") + "Add Executable"
            ),
            null
          );
        }
         */
        return
          Lte.divContainerFluid(Lte.divRow(
            Html.section("col-lg-6",
            Lte.card(Html.fasIcon("user-tie") + "Staged" + ConductorComponent.CONDUCTORS, null,
              Lte.table("table-condensed", new Lte.Table() {
                @Override
                public ArrayList<Lte.TableValue> tableHeaders() {
                  ArrayList<Lte.TableValue> list = new ArrayList<>();
                  list.add(new Lte.TableValue("", "Name1"));
                  return list;
                }

                @Override
                public ArrayList<Future<Lte.TableRow>> tableRows() {
                  ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
                  for (Workspace workspace : workspaceList) {
                    list.add(Main.interfaceThreadPool.submit(() -> {
                        return new Lte.TableRow(
                          Html.a(WorkspaceComponent.getUrl(project, workspace), null, null, workspace.getName()));
                      }
                    ));
                  }
                  return list;
                }
              })
              , null, "card-warning card-outline", "p-0")
              , Lte.card(Html.fasIcon("layer-group") + "Staged" + ExecutablesComponent.EXECUTABLES, null,
                Lte.table("table-condensed", new Lte.Table() {
                  ArrayList<StagedExecutable> executableList = StagedExecutable.getList(workspace);

                  @Override
                  public ArrayList<Lte.TableValue> tableHeaders() {
                    return null;
                  }

                  @Override
                  public ArrayList<Future<Lte.TableRow>> tableRows() {
                    ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
                    for (StagedExecutable executable : executableList) {
                      list.add(Main.interfaceThreadPool.submit(() -> {
                          return new Lte.TableRow(
                            Html.a(StagedExecutableComponent.getUrl(executable), null, null, executable.getName()));
                        }
                      ));
                    }
                    return list;
                  }
                })
                , null, "card-info card-outline", "p-0")
            ),
            Html.section("col-lg-6",
              Lte.card(Html.fasIcon("project-diagram") + RunsComponent.TITLE, null,
                Lte.table("table-condensed", new Lte.Table() {
                  @Override
                  public ArrayList<Lte.TableValue> tableHeaders() {
                    ArrayList<Lte.TableValue> list = new ArrayList<>();
                    list.add(new Lte.TableValue("", "Name3"));
                    return list;
                  }

                  @Override
                  public ArrayList<Future<Lte.TableRow>> tableRows() {
                    ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
                    for (Workspace workspace : workspaceList) {
                      list.add(Main.interfaceThreadPool.submit(() -> {
                          return new Lte.TableRow(
                            Html.a(WorkspaceComponent.getUrl(project, workspace), null, null, workspace.getName()));
                        }
                      ));
                    }
                  for (Workspace workspace : workspaceList) {
                    list.add(Main.interfaceThreadPool.submit(() -> {
                        return new Lte.TableRow(
                          Html.a(WorkspaceComponent.getUrl(project, workspace), null, null, workspace.getName()));
                      }
                    ));
                  }
                  for (Workspace workspace : workspaceList) {
                    list.add(Main.interfaceThreadPool.submit(() -> {
                        return new Lte.TableRow(
                          Html.a(WorkspaceComponent.getUrl(project, workspace), null, null, workspace.getName()));
                      }
                    ));
                  }
                  return list;
                }
              })
              , null, "card-danger card-outline", "p-0")
            )
          ));
      }
    }.render(this);
  }

  public enum Mode {Default}
}
