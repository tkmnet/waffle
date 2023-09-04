package jp.tkms.waffle.web.component.project.conductor;

import jp.tkms.waffle.Main;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.project.Project;
import jp.tkms.waffle.data.project.conductor.Conductor;
import jp.tkms.waffle.data.project.executable.Executable;
import jp.tkms.waffle.data.project.workspace.Workspace;
import jp.tkms.waffle.data.project.workspace.run.ConductorRun;
import jp.tkms.waffle.data.util.FileName;
import jp.tkms.waffle.exception.ChildProcedureNotFoundException;
import jp.tkms.waffle.exception.InvalidInputException;
import jp.tkms.waffle.script.ScriptProcessor;
import jp.tkms.waffle.script.wnj.WaffleNodeJsonScriptProcessor;
import jp.tkms.waffle.web.Key;
import jp.tkms.waffle.web.component.AbstractAccessControlledComponent;
import jp.tkms.waffle.web.component.ResponseBuilder;
import jp.tkms.waffle.web.component.log.LogsComponent;
import jp.tkms.waffle.web.component.project.ProjectComponent;
import jp.tkms.waffle.web.component.project.ProjectsComponent;
import jp.tkms.waffle.web.component.project.workspace.WorkspaceComponent;
import jp.tkms.waffle.web.template.Html;
import jp.tkms.waffle.web.template.Link;
import jp.tkms.waffle.web.template.Lte;
import jp.tkms.waffle.web.template.ProjectMainTemplate;
import jp.tkms.waffle.exception.ProjectNotFoundException;
import spark.Spark;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

import static jp.tkms.waffle.web.template.Html.value;

public class ConductorComponent extends AbstractAccessControlledComponent {
  public static final String TITLE = "Conductor";
  public static final String CONDUCTORS = "Conductors";
  private static final String KEY_MAIN_SCRIPT = "main_script";
  private static final String KEY_CHILD_SCRIPT = "listener_script";
  private static final String KEY_DEFAULT_VARIABLES = "default_variables";
  private static final String KEY_PROCEDURE_NAME = "procedure_name";
  public static final String KEY_CONDUCTOR = "conductor";
  public static final String KEY_PROCEDURE = "procedure";
  public static final String KEY_NOTE = "note";
  private static final String NEW_WORKSPACE = "[Create new workspace]";

  public enum Mode {Default, Prepare, Run, UpdateArguments, UpdateMainScript, UpdateChildScript, NewChildProcedure, RemoveConductor, RemoveProcedure, UpdateNote}

  private Mode mode;
  protected Project project;
  protected Conductor conductor;
  //private ActorRun parent;
  //private SimulatorRun baseRun;
  public ConductorComponent(Mode mode) {
    super();
    this.mode = mode;
  }

  public ConductorComponent() {
    this(Mode.Default);
  }

  static public void register() {
    Spark.get(getUrl(null), new ResponseBuilder(() -> new ConductorComponent()));
    Spark.get(getUrl(null, Mode.Prepare), new ResponseBuilder(() -> new ConductorComponent(Mode.Prepare)));
    Spark.post(getUrl(null, Mode.Run), new ResponseBuilder(() -> new ConductorComponent(Mode.Run)));
    Spark.post(getUrl(null, Mode.UpdateArguments), new ResponseBuilder(() -> new ConductorComponent(Mode.UpdateArguments)));
    Spark.post(getUrl(null, Mode.UpdateMainScript), new ResponseBuilder(() -> new ConductorComponent(Mode.UpdateMainScript)));
    Spark.post(getUrl(null, Mode.UpdateChildScript), new ResponseBuilder(() -> new ConductorComponent(Mode.UpdateChildScript)));
    Spark.post(getUrl(null, Mode.NewChildProcedure), new ResponseBuilder(() -> new ConductorComponent(Mode.NewChildProcedure)));
    Spark.get(getUrl(null, Mode.RemoveConductor), new ResponseBuilder(() -> new ConductorComponent(Mode.RemoveConductor)));
    Spark.post(getUrl(null, Mode.UpdateNote), new ResponseBuilder(() -> new ConductorComponent(Mode.UpdateNote)));
  }

  public static String getUrl(Conductor conductor) {
    return ProjectComponent.getUrl((conductor == null ? null : conductor.getProject())) + "/" + Conductor.CONDUCTOR + "/"
      + (conductor == null ? ":" + KEY_CONDUCTOR : conductor.getName());
  }

  public static Link getLink(Conductor conductor) {
    return Link.entry(getUrl(conductor), Html.fasIcon("user-tie") + conductor.getName());
  }

  public static String getUrl(Conductor conductor, Mode mode) {
    return getUrl(conductor) + "/@" + mode.name();
  }

  protected Conductor getConductorEntity() {
    return Conductor.getInstance(project, request.params(KEY_CONDUCTOR));
  }

  @Override
  public void controller() throws ProjectNotFoundException {
    project = Project.getInstance(request.params("project"));
    conductor = getConductorEntity();

    switch (mode) {
      case Prepare:
        if (conductor.checkSyntax()) {
          renderPrepareForm();
        } else {
          response.redirect(getUrl(conductor));
        }
        break;
      case Run:
        String workspaceName = "" + request.queryParams(WorkspaceComponent.WORKSPACE);
        String newRunName = "" + request.queryParams(KEY_CONDUCTOR);
        Workspace workspace = null;
        if (NEW_WORKSPACE.equals(workspaceName)) {
          workspace = Workspace.create(project, newRunName);
        } else {
          workspace = Workspace.getInstance(project, workspaceName);
        }

        if (workspace == null) {
          ErrorLogMessage.issue("Workspace(" + workspaceName + ") is not found.");
          response.redirect(LogsComponent.getUrl());
        } else {
          if (workspace.getNote().length() <= 0) {
            workspace.setNote(request.queryParams(KEY_NOTE));
          } else {
            workspace.setNote(workspace.getNote() + "\n" + request.queryParams(KEY_NOTE));
          }

          ConductorRun conductorRun = ConductorRun.create(workspace, conductor);
          conductorRun.putVariablesByJson(request.queryParams(KEY_DEFAULT_VARIABLES));
          conductorRun.start(true);

          response.redirect(WorkspaceComponent.getUrl(workspace));
        }
        break;
      case UpdateArguments:
        if (request.queryMap().hasKey(KEY_DEFAULT_VARIABLES)) {
          conductor.setDefaultVariables(request.queryParams(KEY_DEFAULT_VARIABLES));
        }
        response.redirect(getUrl(conductor));
        break;
      case UpdateMainScript:
        if (request.queryMap().hasKey(KEY_MAIN_SCRIPT)) {
          conductor.updateMainProcedureScript(request.queryParams(KEY_MAIN_SCRIPT));
        }
        response.redirect(getUrl(conductor));
        break;
      case NewChildProcedure:
        if (request.queryMap().hasKey(KEY_PROCEDURE) && request.queryMap().hasKey(Key.PROCEDURE_TYPE)) {
          try {
            conductor.createNewChildProcedure(request.queryParams(KEY_PROCEDURE), request.queryParams(Key.PROCEDURE_TYPE));
          } catch (InvalidInputException e) {
            // NOP // response.redirect(getUrl(conductor)); break;
          }
        }
        response.redirect(getUrl(conductor));
        break;
      case UpdateChildScript:
        if (request.queryMap().hasKey(KEY_PROCEDURE_NAME) && request.queryMap().hasKey(KEY_CHILD_SCRIPT)) {
          conductor.updateChildProcedureScript(request.queryParams(KEY_PROCEDURE_NAME), request.queryParams(KEY_CHILD_SCRIPT));
        }
        response.redirect(getUrl(conductor));
        break;
      case RemoveConductor:
        if (conductor.getName().equals(request.queryParams(KEY_CONDUCTOR))) {
          conductor.deleteDirectory();
          response.redirect(ProjectComponent.getUrl(project));
        } else {
          response.redirect(getUrl(conductor));
        }
        break;
      case UpdateNote:
        conductor.setNote(request.queryParams(KEY_NOTE));
        response.redirect(getUrl(conductor));
        break;
      default:
        renderConductor();
    }
  }


  protected String renderPageTitle() {
    return TITLE;
  }

  protected ArrayList<Link> renderPageBreadcrumb() {
    return new ArrayList<>(Arrays.asList(
      ProjectsComponent.getLink(),
      ProjectComponent.getLink(project),
      ConductorsComponent.getLink(project),
      getLink(conductor)
    ));
  }

  protected String renderTool() {
    return Html.a(ConductorComponent.getUrl(conductor, ConductorComponent.Mode.Prepare),
      Html.span("right badge badge-secondary", null, "RUN")
    );
  }

  protected Workspace pageWorkspace() {
    return null;
  }

  private void renderConductor() throws ProjectNotFoundException {
    new ProjectMainTemplate(project) {
      @Override
      protected String pageTitle() {
        return renderPageTitle();
      }

      @Override
      protected ArrayList<Link> pageBreadcrumb() {
        return renderPageBreadcrumb();
      }

      @Override
      protected Workspace pageWorkspace() {
        return ConductorComponent.this.pageWorkspace();
      }

      @Override
      protected String pageContent() {
        String contents = "";
        ArrayList<Lte.FormError> errors = new ArrayList<>();

        contents += Html.link("stylesheet", "/css/baklavajs.css");

        //Actor lastConductorRun = Actor.getLastInstance(project, actorGroup);

        // TODO: do refactoring
        /*
        ArrayList<Actor> notFinishedList = new ArrayList<>();
        for (Actor notFinished : Actor.getNotFinishedList(project)) {
          if (!notFinished.isRoot()) {
            if (notFinished.getParentActor() != null && notFinished.getParentActor().isRoot()) {
              notFinishedList.add(notFinished);
            }
          }
        }
        int runningCount = 0;
        for (Actor notFinished : notFinishedList) {
          if (notFinished.getActorGroup() != null && notFinished.getActorGroup().getId().equals(actorGroup.getId())) {
            runningCount += 1;
          }
        }
         */
        int runningCount = 0;


        /*
        if (lastConductorRun != null && ! lastConductorRun.getErrorNote().equals("")) {
          content += Lte.card(Html.fasIcon("exclamation-triangle") + "Error of last run",
            Lte.cardToggleButton(false),
            Lte.divRow(
              Lte.divCol(Lte.DivSize.F12,
                Lte.errorNoticeTextAreaGroup(lastConductorRun.getErrorNote())
              )
            )
            , null, "card-danger", null);
        }

         */

        String snippetScript = "";

        snippetScript += "s('procedure:Main Procedure','#');";
        snippetScript += "s('procedure:#','#');";
        for (Executable executable : Executable.getList(project)) {
          snippetScript += "s('executable:" + executable.getName() + "','" + executable.getName() + "');";
        }
        for (Computer computer : Computer.getViableList()) {
          snippetScript += "s('computer:" + computer.getName() + "','" + computer.getName() + "');";
        }

        /*
        contents += Html.element("script", new Html.Attributes(value("type", "text/javascript")),
          "var updateConductorJobNum = function(c,n) {" +
            "if (n > 0) {" +
            "document.getElementById('actorGroup-jobnum-' + c).style.display = 'inline-block';" +
            "document.getElementById('actorGroup-jobnum-' + c).innerHTML = n;" +
            "} else {" +
            "document.getElementById('actorGroup-jobnum-' + c).style.display = 'none';" +
            "}" +
            "};"
        );
         */

        contents += Html.form(getUrl(conductor, Mode.UpdateNote), Html.Method.Post,
          Lte.card(Html.fasIcon("user-tie") + conductor.getName(),
            Html.span(null, null,
              //Html.span("right badge badge-warning", new Html.Attributes(value("id", "actorGroup-jobnum-" + conductor.getName()))),
              renderTool()
              //, Html.javascript("updateConductorJobNum('" + conductor.getName() + "'," + runningCount + ")")
            ),
            Html.div(null,
              Lte.readonlyTextInputWithCopyButton("Conductor Directory", conductor.getPath().toAbsolutePath().toString()),
              Lte.formTextAreaGroup(KEY_NOTE, "Note", conductor.getNote(), null)
            ),
            Lte.formSubmitButton("success", "Update"), "card-warning", null));

        contents +=
          Html.form(getUrl(conductor, Mode.UpdateArguments), Html.Method.Post,
            Lte.card(Html.fasIcon("terminal") + "Default Variables",
              Lte.cardToggleButton(false),
              Lte.divRow(
                Lte.divCol(Lte.DivSize.F12,
                  Lte.formJsonEditorGroup(KEY_DEFAULT_VARIABLES, null, "tree", conductor.getDefaultVariables().toString(), null)
                )
              ),
              Lte.formSubmitButton("success", "Update"),
              "collapsed-card.stop", null)
          );

        contents += getProcedureContents(conductor.getMainProcedureScriptPath(), snippetScript, errors);
        /*
        String mainScriptSyntaxError = ScriptProcessor.getProcessor(conductor.getMainProcedureScriptPath()).checkSyntax(conductor.getMainProcedureScriptPath());
        contents +=
          Html.form(getUrl(conductor, Mode.UpdateMainScript), Html.Method.Post,
            Lte.card(Html.fasIcon("terminal") + Conductor.MAIN_PROCEDURE_SHORT_ALIAS + " (Main Procedure)",
              Lte.cardToggleButton(false),
              Lte.divRow(
                Lte.divCol(Lte.DivSize.F12,
                  ("".equals(mainScriptSyntaxError) ? null : Lte.errorNoticeTextAreaGroup(mainScriptSyntaxError)),
                  Lte.formDataEditorGroup(KEY_MAIN_SCRIPT, null, "ruby", conductor.getMainProcedureScript(), snippetScript, errors),
                  getGuideHtml()
                )
              ),
              Lte.formSubmitButton("success", "Update"),
              "collapsed-card.stop", null)
          );
         */

        for (String listenerName : conductor.getChildProcedureNameList()) {
          Path path = conductor.getChildProcedureScriptPath(listenerName);
          contents += getProcedureContents(path, snippetScript, errors);
          /*
          String scriptSyntaxError = ScriptProcessor.getProcessor(path).checkSyntax(path);
          try {
            contents +=
              Html.form(getUrl(conductor, Mode.UpdateListenerScript), Html.Method.Post,
                Lte.card(Html.fasIcon("terminal") + listenerName + " (Child Procedure)",
                  Lte.cardToggleButton(false),
                  Lte.divRow(
                    Lte.divCol(Lte.DivSize.F12,
                      Html.inputHidden(KEY_LISTENER_NAME, listenerName),
                      ("".equals(scriptSyntaxError) ? null : Lte.errorNoticeTextAreaGroup(scriptSyntaxError)),
                      Lte.formDataEditorGroup(KEY_LISTENER_SCRIPT, null, "ruby", conductor.getChildProcedureScript(listenerName), snippetScript, errors)
                    )
                  ),
                  Lte.formSubmitButton("success", "Update"),
                  "collapsed-card.stop", null)
              );
          } catch (ChildProcedureNotFoundException e) {
            //NOOP
          }
           */
        }

        LinkedHashMap<String, String> procedureTypes = new LinkedHashMap<>();
        String mainScriptExtension = ScriptProcessor.getExtension(conductor.getMainProcedureScriptPath());
        procedureTypes.put(mainScriptExtension, ScriptProcessor.getDescription(ScriptProcessor.CLASS_NAME_MAP.get(mainScriptExtension)));
        ScriptProcessor.CLASS_NAME_MAP.forEach((k, v) -> {
          if (!mainScriptExtension.equals(k)) {
            procedureTypes.put(k, ScriptProcessor.getDescription(v));
          }
        });

        contents +=
          Html.form(getUrl(conductor, Mode.NewChildProcedure), Html.Method.Post,
            Lte.card(Html.fasIcon("plus-square") + "New Procedure",
              Lte.cardToggleButton(true),
              Lte.divRow(
                Lte.divCol(Lte.DivSize.F12,
                  Lte.formInputGroup("text", KEY_PROCEDURE, "Name", "", "", errors),
                  Lte.formSelectGroup(Key.PROCEDURE_TYPE, "Default Procedure Type", procedureTypes, errors)
                )
              )
              , Lte.formSubmitButton("primary", "Create")
              , "collapsed-card card-secondary card-outline", null)
          );

        contents += Html.form(getUrl(conductor, Mode.RemoveConductor), Html.Method.Get,
          Lte.card(Html.fasIcon("trash-alt") + "Remove",
            Lte.cardToggleButton(true),
            Lte.formInputGroup("text", KEY_CONDUCTOR, "Type the name of Conductor.", conductor.getName(), null, null),
            Lte.formSubmitButton("danger", "Remove")
            , "collapsed-card card-danger card-outline", null
          )
        );

        contents += Html.element("script", new Html.Attributes(value("src", "/js/baklavajs.js")));
        contents += Html.element("script", new Html.Attributes(value("src", "/js/baklavajs-apply.js")));

        return contents;
      }
    }.render(this);
  }

  private String getProcedureContents(Path procedurePath, String snippetScript, ArrayList<Lte.FormError> errors) {
    String contents = "";
    String procedureName = procedurePath.getFileName().toString();
    String scriptSyntaxError = ScriptProcessor.getProcessor(procedurePath).checkSyntax(procedurePath);
    boolean isMain = conductor.getMainProcedureScriptPath().equals(procedurePath);
    String scriptTitle = (isMain ? Conductor.MAIN_PROCEDURE_SHORT_ALIAS + " (Main Procedure)" : procedureName + " (Child Procedure)");

    try {
      String scriptBody = (isMain ? conductor.getMainProcedureScript() : conductor.getChildProcedureScript(procedureName));
      Mode mode = (isMain ? Mode.UpdateMainScript : Mode.UpdateChildScript);
      String valueKey = (isMain ? KEY_MAIN_SCRIPT : KEY_CHILD_SCRIPT);

      if (procedureName.endsWith(WaffleNodeJsonScriptProcessor.EXTENSION)) {
        contents +=
          Html.form(getUrl(conductor, mode), Html.Method.Post,
            Lte.card(Html.fasIcon("terminal") + scriptTitle,
              Lte.cardToggleButton(false),
              Lte.divRow(
                Lte.divCol(Lte.DivSize.F12,
                  Html.inputHidden(KEY_PROCEDURE_NAME, procedureName),
                  ("".equals(scriptSyntaxError) ? null : Lte.errorNoticeTextAreaGroup(scriptSyntaxError)),
                  Html.element("div", new Html.Attributes(value("id", "nodeeditor"),
                    value("class", "node-editor"))),
                  Html.textareaHidden(valueKey, scriptBody)
                )
              ),
              Lte.formSubmitButton("success", "Update"),
              "collapsed-card.stop", null)
          );
      } else {
        contents +=
          Html.form(getUrl(conductor, mode), Html.Method.Post,
            Lte.card(Html.fasIcon("terminal") + scriptTitle,
              Lte.cardToggleButton(false),
              Lte.divRow(
                Lte.divCol(Lte.DivSize.F12,
                  Html.inputHidden(KEY_PROCEDURE_NAME, procedureName),
                  ("".equals(scriptSyntaxError) ? null : Lte.errorNoticeTextAreaGroup(scriptSyntaxError)),
                  Lte.formDataEditorGroup(valueKey, null, "ruby", scriptBody, snippetScript, errors)
                )
              ),
              Lte.formSubmitButton("success", "Update"),
              "collapsed-card.stop", null)
          );
      }
    } catch (ChildProcedureNotFoundException e) {
      //NOOP
    }
    return contents;
  }

  private String getGuideHtml() {
    return Html.div(null,
      Html.div(null, "<b style='color:green;'>以下，主要コマンド仮表示</b>"),
      Html.element("div", new Html.Attributes(Html.value("style", "padding-left:1em;font-size:0.8em;")),
        //Html.div(null, "hub.invokeListener(\"&lt;LISTENER_NAME&gt;\")"),
        //Html.div(null, "instance.loadConductorTemplate(\"&lt;NAME&gt;\")"),
        //Html.div(null, "instance.loadListenerTemplate(\"&lt;NAME&gt;\")"),
        Html.div(null, "this.v[:&lt;KEY&gt;] = &lt;VALUE&gt;"),
        Html.div(null, "<b>[RUN(Executable)]</b> = this.createExecutableRun(\"&lt;EXECUTABLE_NAME&gt;\", \"&lt;HOST&gt;\")"),
        Html.div(null, "<b>[RUN(Conductor)]</b> = this.createConductorRun(\"&lt;CONDUCTOR_NAME&gt;\")"),
        Html.div(null, "<b>[RUN]</b>.addFinalizer(\"&lt;LISTENER_NAME&gt;\")"),
        Html.div(null, "this.addFinalizer(\"&lt;LISTENER_NAME&gt;\")"),
        Html.div(null, "<b>[RUN(Executable)]</b>.p[:&lt;KEY&gt;] = &lt;VALUE&gt"),
        Html.div(null, "<b>[RUN(Executable)]</b>.makeLocalShared(\"&lt;KEY&gt\", \"&lt;REMOTE_FILE&gt\")"),
        Html.div(null, "&lt;VALUE&gt = <b>[RUN(Executable)]</b>.getResult(\"&lt;KEY&gt\")")
      )
    );
  }

  private void renderPrepareForm() throws ProjectNotFoundException {
    new ProjectMainTemplate(project) {
      @Override
      protected String pageTitle() {
        return renderPageTitle();
      }

      @Override
      protected ArrayList<Link> pageBreadcrumb() {
        return renderPageBreadcrumb();
      }

      @Override
      protected String pageContent() {
        String content = "";

        String name = conductor.getName() + '_' + Main.DATE_FORMAT.format(System.currentTimeMillis());
        String variables = conductor.getDefaultVariables().toString();

        content +=
          Html.form(getUrl(conductor, Mode.Run), Html.Method.Post,
            Lte.card(Html.fasIcon("feather-alt") + "Prepare",
              null,
              Lte.divRow(
                Lte.divCol(Lte.DivSize.F12,
                  Lte.formSelectGroup(WorkspaceComponent.WORKSPACE, WorkspaceComponent.WORKSPACE +
                      Html.span("text-danger", null, "(If you select an existing Workspace, the StagedConductor will be replaced even though it is running.)")
                    ,
                    new ArrayList<>() {
                      {
                        add(NEW_WORKSPACE);
                        addAll(Workspace.getList(project).stream().map(workspace -> workspace.getName()).collect(Collectors.toList()));
                      }
                    }
                    , null)
                ),
                Lte.divCol(Lte.DivSize.F12,
                  Lte.formInputGroup("text", KEY_CONDUCTOR, "Name", "name", FileName.removeRestrictedCharacters(name), null)
                ),
                Lte.divCol(Lte.DivSize.F12,
                  Lte.formTextAreaGroup(KEY_NOTE, "Note", 2, "", null)
                ),
                Lte.divCol(Lte.DivSize.F12,
                  Lte.formJsonEditorGroup(KEY_DEFAULT_VARIABLES, "Variables", "form", variables, null)
                )
              )
              ,Lte.formSubmitButton("primary", "Run"), "card-info", null
            )
          );

        return content;
      }
    }.render(this);
  }

  private ArrayList<Lte.FormError> checkCreateProjectFormError() {
    return new ArrayList<>();
  }
}
