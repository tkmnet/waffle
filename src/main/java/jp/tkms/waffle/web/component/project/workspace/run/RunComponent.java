package jp.tkms.waffle.web.component.project.workspace.run;

import jp.tkms.waffle.Constants;
import jp.tkms.waffle.Main;
import jp.tkms.waffle.data.project.workspace.HasLocalPath;
import jp.tkms.waffle.data.project.workspace.Workspace;
import jp.tkms.waffle.data.project.workspace.run.*;
import jp.tkms.waffle.data.util.WrappedJson;
import jp.tkms.waffle.exception.RunNotFoundException;
import jp.tkms.waffle.web.component.AbstractAccessControlledComponent;
import jp.tkms.waffle.web.component.ResponseBuilder;
import jp.tkms.waffle.web.component.computer.ComputersComponent;
import jp.tkms.waffle.web.component.project.ProjectComponent;
import jp.tkms.waffle.web.component.project.ProjectsComponent;
import jp.tkms.waffle.web.component.project.executable.ExecutableComponent;
import jp.tkms.waffle.web.component.project.workspace.WorkspaceComponent;
import jp.tkms.waffle.web.component.project.workspace.WorkspacesComponent;
import jp.tkms.waffle.web.template.Html;
import jp.tkms.waffle.web.template.Link;
import jp.tkms.waffle.web.template.Lte;
import jp.tkms.waffle.web.template.ProjectMainTemplate;
import jp.tkms.waffle.data.project.Project;
import jp.tkms.waffle.exception.ProjectNotFoundException;
import spark.Spark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Future;

public class RunComponent extends AbstractAccessControlledComponent {
  public static final String RUNS = "Runs";

  private Mode mode;

  private Project project;
  private Workspace workspace;
  private AbstractRun abstractRun;
  private ArrayList<HasLocalPath> childrenList = null;
  private String directoryName;
  WrappedJson conductorStartupVariables = null;

  public enum Mode {Default, ReCheck, UpdateNote, Root, Abort, Cancel}

  public RunComponent(Mode mode) {
    super();
    this.mode = mode;
  }

  public RunComponent() {
    this(Mode.Default);
  }

  static public void register() {
    Spark.get(getRootUrl(null), new ResponseBuilder(() -> new RunComponent()));
    Spark.get(getUrl(null), new ResponseBuilder(() -> new RunComponent()));
    /*
    Spark.get(getUrl(null, Mode.ReCheck), new ResponseBuilder(() -> new RunComponent(Mode.ReCheck)));
    Spark.get(getUrl(null, Mode.UpdateNote), new ResponseBuilder(() -> new RunComponent(Mode.UpdateNote)));
    Spark.get(getUrl(null, Mode.Abort), new ResponseBuilder(() -> new RunComponent(Mode.Abort)));
    Spark.get(getUrl(null, Mode.Cancel), new ResponseBuilder(() -> new RunComponent(Mode.Cancel)));
     */
  }

  public static String getRootUrl(Workspace workspace) {
    if (workspace != null) {
      return '/' + workspace.getLocalPath().toString();
    } else {
      return WorkspaceComponent.getUrl(null) + "/RUN";
    }
  }

  public static String getUrl(AbstractRun run) {
    if (run != null) {
      return '/' + ((HasLocalPath) run).getLocalPath().toString();
    } else {
      return WorkspaceComponent.getUrl(null) + "/RUN/*";
    }
  }

  public static String getUrl(AbstractRun run, Mode mode) {
    return getUrl(run) + "?mode=" + mode.name();
  }

  public static String getUrlFromLocalPath(HasLocalPath localPath) {
    return "/" + localPath.getLocalPath().toString();
  }

  public static String getUrlFromPath(Path path) {
    return path.toString();
  }

  public Mode getMode() {
    String modeDescription = request.queryParamOrDefault("mode", "").toLowerCase();
    for (Mode mode : Mode.values()) {
      if (modeDescription.equals(mode.name().toLowerCase())) {
        return mode;
      }
    }
    return Mode.Default;
  }

  @Override
  public void controller() throws ProjectNotFoundException {
    mode = getMode();

    project = Project.getInstance(request.params(ProjectComponent.KEY_PROJECT));
    workspace = Workspace.getInstance(project, request.params(WorkspaceComponent.KEY_WORKSPACE));

    if (Mode.Root.equals(mode)) {
      conductorRunController();
    } else {
      String localPathString = request.uri().substring(1);
      if (localPathString.endsWith("/")) {
        localPathString = localPathString.substring(0, localPathString.length() -1);
      }

      directoryName = localPathString.substring(localPathString.lastIndexOf('/') +1);

      try {
        abstractRun = AbstractRun.getInstance(workspace, localPathString);
      } catch (RunNotFoundException e) {
        return;
      }

      if (abstractRun == null) {
        runDirectoryController();
      } else if (abstractRun instanceof ExecutableRun) {
        executableRunController();
      } else {
        conductorRunController();
      }
    }
  }

  private void runDirectoryController() throws ProjectNotFoundException {
    String localPathString = request.uri().substring(1);
    childrenList = AbstractRun.getDirectoryList(new RunDirectory(localPathString));
    renderRuns();
  }

  private void conductorRunController() throws ProjectNotFoundException {
    switch (mode) {
      case UpdateNote:
        //run.update();
        response.redirect(RunComponent.getUrl(abstractRun));
        return;
    }

    /*
    if (Paths.get(request.uri()).relativize(Paths.get(request.headers("Referer").replaceFirst("^.*?/PROJECT/", "/PROJECT/"))).toString().contains("..") && runList.size() == 1) {
      response.redirect(RunComponent.getUrl(runList.get(0)));
    }
     */
    if (abstractRun == null) {
      childrenList = AbstractRun.getDirectoryList(workspace);
      conductorStartupVariables = ConductorRun.getInstance(workspace, RunComponent.getUrlFromPath(AbstractRun.getBaseDirectoryPath(workspace))).getStartupVariables();
    } else {
      childrenList = AbstractRun.getDirectoryList((ConductorRun)abstractRun);
      conductorStartupVariables = ((ConductorRun) abstractRun).getStartupVariables();
    }

    renderRuns();
  }

  private void executableRunController() throws ProjectNotFoundException {
    switch (mode) {
      case ReCheck:
        //run.recheck();
        response.redirect(RunComponent.getUrl(abstractRun));
        return;
      case Abort:
        ((ExecutableRun) abstractRun).abort();
        response.redirect(RunComponent.getUrl(abstractRun));
        return;
      case Cancel:
        ((ExecutableRun) abstractRun).cancel();
        response.redirect(RunComponent.getUrl(abstractRun));
        return;
    }

    renderRun();
  }

  private void renderRuns() throws ProjectNotFoundException {
    new ProjectMainTemplate(project) {
      @Override
      protected String pageTitle() {
        if (abstractRun != null) {
          return abstractRun.getName();
        }
        return directoryName;
      }

      @Override
      protected String pageSubTitle() {
        if (abstractRun != null) {
          if (abstractRun instanceof ProcedureRun) {
            return "ProcedureRun";
          }
          return "ConductorRun";
        }
        return super.pageSubTitle();
      }

      @Override
      protected Workspace pageWorkspace() {
        return workspace;
      }

      @Override
      protected Path pageWorkingDirectory() {
        if (abstractRun != null) {
          return abstractRun.getPath();
        }
        return null;
      }

      @Override
      protected ArrayList<Link> pageBreadcrumb() {
        ArrayList<Link> breadcrumb = new ArrayList<>(Arrays.asList(
          Link.entry(ProjectsComponent.getUrl(), "Projects"),
          Link.entry(ProjectComponent.getUrl(project), project.getName())
        ));
        ArrayList<Link> runNodeList = new ArrayList<>();
        /*
        RunNode parent = runNode.getParent();
        if (parent == null) {
          breadcrumb.add("Runs");
        } else {
          breadcrumb.add(Html.a(RunsComponent.getUrl(project), "Runs"));
          while (parent != null) {
            runNodeList.add(Html.a(RunsComponent.getUrl(project, parent), parent.getSimpleName()));
            parent = parent.getParent();
          }
          runNodeList.remove(runNodeList.size() - 1);
          Collections.reverse(runNodeList);
          breadcrumb.addAll(runNodeList);
          breadcrumb.add(runNode.getSimpleName());
        }

         */

        return breadcrumb;
      }

      @Override
      protected String pageContent() {
        String contents = "";
        contents += Html.javascript(
          "var runUpdated = function(id) {location.reload();};",
          "var runCreated = function(id) {location.reload();};"
        );

        /*
        String errorNote = conductorRun.getErrorNote();
        if (! "".equals(errorNote)) {
          contents += Lte.card(Html.faIcon("exclamation-triangle") + "Error",
            Lte.cardToggleButton(true),
            Lte.divRow(
              Lte.divCol(Lte.DivSize.F12,
                Lte.errorNoticeTextAreaGroup(errorNote)
              )
            )
            , null, "card-danger", null);
        }

        if (! conductorRun.getVariables().isEmpty()) {
          contents += Lte.card(Html.faIcon("poll") + "Variables",
            Lte.cardToggleButton(false),
            Lte.divRow(
              Lte.divCol(Lte.DivSize.F12,
                Lte.readonlyTextAreaGroup("", null, conductorRun.getVariables().toString(2))
              )
            )
            , null);
        }
         */

        /*
        String note = runNode.getNote();
        contents +=
          Html.form(getUrl( project, runNode, Mode.UpdateNote), Html.Method.Post,
            Lte.card(Html.fasIcon("sticky-note") + "Note",
              Lte.cardToggleButton("".equals(note)),
              Lte.divRow(
                Lte.divCol(Lte.DivSize.F12,
                  Lte.formTextAreaGroup(KEY_NOTE, null, note, null)
                )
              )
              ,Lte.formSubmitButton("success", "Update")
              , ("".equals(note) ? "collapsed-card" : null), null)
          );

         */

        contents += Lte.card(null, null,
          Lte.table("table-condensed table-sm", new Lte.Table() {
            @Override
            public ArrayList<Lte.TableValue> tableHeaders() {
              ArrayList<Lte.TableValue> list = new ArrayList<>();
              //list.add(new Lte.TableValue("width:0;", ""));
              list.add(new Lte.TableValue("", "Name"));
              list.add(new Lte.TableValue("width:50%;", "Note"));
              //list.add(new Lte.TableValue("width:0;", ""));
              return list;
            }

            @Override
            public ArrayList<Future<Lte.TableRow>> tableRows() {
              ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();

              for (HasLocalPath child : childrenList) {
                list.add(Main.interfaceThreadPool.submit(() -> {
                    String icon = Html.farIcon("circle");
                    /*
                    if (run instanceof ProcedureRun) {

                    } else if (run instanceof ConductorRun) {

                    }
                     */

                    return new Lte.TableRow(
                      //new Lte.TableValue(null, (child instanceof ParallelRunNode ? Html.fasIcon("plus-circle") : Html.farIcon("circle"))),
                      //new Lte.TableValue(null, icon),
                      new Lte.TableValue(null, Html.a(getUrlFromLocalPath(child), null, null, child.getPath().getFileName().toString())),
                      new Lte.TableValue("max-width:0;", Html.div("hide-overflow", /*run.getNote()*/ "note"))//,
                      //new Lte.TableValue(null, Html.spanWithId(/*run.getLocalDirectoryPath().toString() +*/ "-badge", run.getState().getStatusBadge()))
                    );
                  })
                );
              }

              /*
              for (RunNode child : runNode.getList()) {
                if (child instanceof SimulatorRunNode) {
                  list.add(Main.interfaceThreadPool.submit(() ->{
                      return new Lte.TableRow(
                        new Lte.TableValue(null, Html.fasIcon("circle")),
                        new Lte.TableValue(null, Html.a(RunComponent.getUrlFromPath(child.getLocalDirectoryPath()), null, null, child.getSimpleName())),
                        new Lte.TableValue("max-width:0;", Html.div("hide-overflow", child.getNote())),
                        new Lte.TableValue(null, Html.spanWithId(child.getId() + "-badge", child.getState().getStatusBadge()))
                      );
                    })
                  );
                } else {
                  list.add(Main.interfaceThreadPool.submit(() -> {
                      return new Lte.TableRow(
                        new Lte.TableValue(null, (child instanceof ParallelRunNode ? Html.fasIcon("plus-circle") : Html.farIcon("circle"))),
                        new Lte.TableValue(null, Html.a(getUrl(project, child), null, null, child.getSimpleName())),
                        new Lte.TableValue("max-width:0;", Html.div("hide-overflow", child.getNote())),
                        new Lte.TableValue(null, Html.spanWithId(child.getId() + "-badge", child.getState().getStatusBadge()))
                      );
                    })
                  );
                }
              }

               */
              return list;
            }
          })
          , null, null, "p-0");

        /*
        contents += Lte.card(null, null,
          Lte.table("table-condensed table-sm", new Lte.Table() {
            @Override
            public ArrayList<Lte.TableValue> tableHeaders() {
              ArrayList<Lte.TableValue> list = new ArrayList<>();
              list.add(new Lte.TableValue("width:6.5em;", "ID"));
              list.add(new Lte.TableValue("", "Name"));
              list.add(new Lte.TableValue("", "Simulator"));
              list.add(new Lte.TableValue("", "Host"));
              list.add(new Lte.TableValue("width:2em;", ""));
              return list;
            }

            @Override
            public ArrayList<Lte.TableRow> tableRows() {
              ArrayList<Lte.TableRow> list = new ArrayList<>();
              for (SimulatorRun run : SimulatorRun.getList(project, conductorRun)) {
                list.add(new Lte.TableRow(
                  Html.a(RunComponent.getUrl(project, run), run.getShortId()),
                  run.getName(),
                  run.getSimulator().getName(),
                  (run.getHost() == null ? "NotFound" : Html.a(HostComponent.getUrl(run.getHost()), run.getHost().getName())),
                  Html.spanWithId(run.getId() + "-badge", run.getState().getStatusBadge())
                ));
              }
              return list;
            }
          })
          , null, null, "p-0");
         */
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

        return contents;
      }
    }.render(this);
  }

  private void renderRun() throws ProjectNotFoundException {
    new ProjectMainTemplate(project) {
      @Override
      protected String pageTitle() {
        return directoryName;
      }

      @Override
      protected String pageSubTitle() {
        return "ExecutableRun";
      }

      @Override
      protected Workspace pageWorkspace() {
        return workspace;
      }

      @Override
      protected Path pageWorkingDirectory() {
        return abstractRun.getPath();
      }

      @Override
      protected ArrayList<Link> pageBreadcrumb() {
        ArrayList<Link> breadcrumb = new ArrayList<>(Arrays.asList(
          Link.entry(ProjectsComponent.getUrl(), ProjectComponent.PROJECTS),
          Link.entry(ProjectComponent.getUrl(project), project.getName()),
          Link.entry(WorkspacesComponent.getUrl(project), WorkspaceComponent.WORKSPACES),
          Link.entry(WorkspaceComponent.getUrl(workspace), workspace.getName())
        ));
        ArrayList<Link> runNodeList = new ArrayList<>();
        /*
        RunNode parent = run.getRunNode().getParent();
        while (parent != null) {
          runNodeList.add(Html.a(RunsComponent.getUrl(project, parent), parent.getSimpleName()));
          parent = parent.getParent();
        }
        runNodeList.remove(runNodeList.size() -1);

         */
        Collections.reverse(runNodeList);
        breadcrumb.addAll(runNodeList);
        //breadcrumb.add(run.getName());
        return breadcrumb;
      }

      @Override
      protected String pageContent() {
        ExecutableRun executableRun = (ExecutableRun) abstractRun;

        String content = "";

        content += Html.javascript("var run_id = '" + executableRun.getLocalPath().toString() + "';");

        content += Lte.card(Html.fasIcon("info-circle") + "Properties", null,
          Lte.table("table-condensed table-sm", new Lte.Table() {
              @Override
              public ArrayList<Lte.TableValue> tableHeaders() {
                return null;
              }

              @Override
              public ArrayList<Future<Lte.TableRow>> tableRows() {
                ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
                list.add(Main.interfaceThreadPool.submit(() -> { return new Lte.TableRow("Status",
                  executableRun.getState().getStatusBadge()
                  + (executableRun.isRunning() ? " | "
                    + Html.a(getUrl(executableRun, Mode.Abort), Lte.badge("secondary",  new Html.Attributes(Html.value("style","width:5em;")), Html.fasIcon("times-circle") + "Abort"))
                    + Html.a(getUrl(executableRun, Mode.Cancel), Lte.badge("secondary", new Html.Attributes(Html.value("style","width:5em;")), Html.fasIcon("cut") + "Cancel")) : "")
                );}));
                /*
                if (run.getActorGroup() != null) {
                  list.add(Main.interfaceThreadPool.submit(() -> { return new Lte.TableRow("Conductor", Html.a(ConductorComponent.getUrl(run.getActorGroup()), run.getActorGroup().getName()));}));
                } else {
                  list.add(Main.interfaceThreadPool.submit(() -> { return new Lte.TableRow("Conductor", "No Conductor");}));
                }

                 */
                list.add(Main.interfaceThreadPool.submit(() -> { return new Lte.TableRow("Executable", Html.a(ExecutableComponent.getUrl(executableRun.getExecutable()), executableRun.getExecutable().getName()));}));
                if (executableRun.getComputer() == executableRun.getActualComputer()) {
                  list.add(Main.interfaceThreadPool.submit(() -> { return new Lte.TableRow("Computer",
                    (executableRun.getComputer() == null ? "NotFound" : Html.a(ComputersComponent.getUrl(executableRun.getComputer()), executableRun.getComputer().getName())));}));
                } else {
                  list.add(Main.interfaceThreadPool.submit(() -> { return new Lte.TableRow("Computer",
                    (executableRun.getActualComputer() == null ? "NotFound" : Html.a(ComputersComponent.getUrl(executableRun.getActualComputer()), executableRun.getActualComputer().getName())) +
                    (executableRun.getComputer() == null ? "NotFound" : Html.a(ComputersComponent.getUrl(executableRun.getComputer()), "(" + executableRun.getComputer().getName() + ")"))
                  );}));
                }
                list.add(Main.interfaceThreadPool.submit(() -> { return new Lte.TableRow("Exit status", "" + executableRun.getExitStatus()
                  + (executableRun.getExitStatus() == -2
                  ? Html.a(RunComponent.getUrl(executableRun, Mode.ReCheck),
                  Lte.badge("secondary", null, "ReCheck")):""));}));
                list.add(Main.interfaceThreadPool.submit(() -> { return new Lte.TableRow("Created at", executableRun.getCreatedDateTime().toString());}));
                list.add(Main.interfaceThreadPool.submit(() -> { return new Lte.TableRow("Submitted at", executableRun.getSubmittedDateTime().toString());}));
                list.add(Main.interfaceThreadPool.submit(() -> { return new Lte.TableRow("Finished at", executableRun.getFinishedDateTime().toString());}));
                list.add(Main.interfaceThreadPool.submit(() -> { return new Lte.TableRow("Remote Directory", Lte.readonlyTextInputWithCopyButton(null, executableRun.getRemoteWorkingDirectoryLog(), true));}));
                return list;
              }
            })
          , null, null, "p-0");

        /*
        if (run.getVariablesStoreSize() <= Constants.HUGE_FILE_SIZE) {
          content += Lte.card(Html.fasIcon("list-alt") + "Variables",
            Lte.cardToggleButton(true),
            Lte.divRow(
              Lte.divCol(Lte.DivSize.F12,
                Lte.formJsonEditorGroup("", null, "view", run.getVariables().toString(), null)
              )
            )
            , (run.getActorGroup() == null ? "" : Html.a(ConductorComponent.getUrl(run.getActorGroup(), "prepare", ActorRun.getRootInstance(project), run),
              Html.span("right badge badge-secondary", null, "run by this variables")
            )), "collapsed-card", null);
        } else {
          content += Lte.card(Html.fasIcon("list-alt") + "Variables",
            Lte.cardToggleButton(true),
            Lte.divRow(
              Lte.divCol(Lte.DivSize.F12,
                "The variables is too large"
              )
            )
            , (run.getActorGroup() == null ? "" : Html.a(ConductorComponent.getUrl(run.getActorGroup(), "prepare", ActorRun.getRootInstance(project), run),
              Html.span("right badge badge-secondary", null, "run by this variables")
            )), "collapsed-card", null);
        }

         */

        if (executableRun.getPriorRunSize() > 0) {
          content += Lte.card(Html.fasIcon("trash") + "Failed Runs", null,
          Lte.table("table-condensed table-sm", new Lte.Table() {
            @Override
            public ArrayList<Future<Lte.TableRow>> tableRows() {
              ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
              for (ExecutableRun child : executableRun.getPriorRun()) {
                list.add(Main.interfaceThreadPool.submit(() -> {
                    return new Lte.TableRow(
                      new Lte.TableValue(null, Html.a(getUrlFromLocalPath(child), null, null, child.getPath().getFileName().toString())),
                      new Lte.TableValue(null, child.getCreatedDateTime().toString())
                    );
                  })
                );
              }
              return list;
            }
          })
          , null, null, "p-0");
        }

        String parametersAndResults = "";

        if (executableRun.getParametersStoreSize() <= Constants.HUGE_FILE_SIZE) {
          parametersAndResults = Lte.divCol(Lte.DivSize.F12,
            Lte.formJsonEditorGroup("", "Parameters", "view", executableRun.getParameters().toString(), null)
          );
        } else {
          parametersAndResults = Lte.divCol(Lte.DivSize.F12,
            Lte.readonlyTextAreaGroup("", "Parameters", "The parameters is too large.")
          );
        }

        if (executableRun.getResultsStoreSize() <= Constants.HUGE_FILE_SIZE) {
          parametersAndResults += Lte.divCol(Lte.DivSize.F12,
            Lte.formJsonEditorGroup("", "Results", "view", executableRun.getResults().toString(), null)
          );
        } else {
          parametersAndResults += Lte.divCol(Lte.DivSize.F12,
            Lte.readonlyTextAreaGroup("", "Results", "The results is too large.")
          );
        }

        content += Lte.collapsedCard(Html.fasIcon("list-alt") + "Parameters & Results", null,
          Lte.divRow(parametersAndResults),
          null, null, null,
          project .getName() + "#" + executableRun.getExecutable().getName() + "#PARAMRES",
          false, request);

        String stdout = executableRun.getStdout();
        if (stdout != null) {
          content += Lte.collapsedCard(Html.fasIcon("file") + "Standard Output", null,
            Lte.divRow(
              Lte.divCol(Lte.DivSize.F12,
                Lte.readonlyTextAreaGroup("", null, stdout)
              )
            ),
            null, null, null,
            project .getName() + "#" + executableRun.getExecutable().getName() + "#STDOUT",
            false, request);
        }

        String stderr = executableRun.getStderr();
        if (stderr != null) {
          content += Lte.collapsedCard(Html.fasIcon("file") + "Standard Error", null,
            Lte.divRow(
              Lte.divCol(Lte.DivSize.F12,
                Lte.readonlyTextAreaGroup("", null, stderr)
              )
            ),
            null, null, null,
            project .getName() + "#" + executableRun.getExecutable().getName() + "#STDERR",
            false, request);
        }

        return content;
      }
    }.render(this);
  }
}
