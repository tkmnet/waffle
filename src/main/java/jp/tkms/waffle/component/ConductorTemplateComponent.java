package jp.tkms.waffle.component;

import jp.tkms.waffle.component.template.Html;
import jp.tkms.waffle.component.template.Lte;
import jp.tkms.waffle.component.template.MainTemplate;
import jp.tkms.waffle.data.*;
import spark.Spark;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class ConductorTemplateComponent extends AbstractAccessControlledComponent {
  private static final String KEY_MAIN_SCRIPT = "main_script";
  private static final String KEY_LISTENER_SCRIPT = "listener_script";
  private static final String KEY_ARGUMENTS = "arguments";
  private static final String KEY_LISTENER = "listener";
  private static final String KEY_EXT_RUBY = ".rb";
  private static final String KEY_LISTENER_FILENAME = "listener_filename";
  private static final String KEY_NAME = "Name";

  private Mode mode;

  private ConductorTemplate module;
  public ConductorTemplateComponent(Mode mode) {
    super();
    this.mode = mode;
  }

  public ConductorTemplateComponent() {
    this(Mode.Default);
  }

  static public void register() {
    Spark.get(getUrl(null), new ConductorTemplateComponent());
    Spark.post(getUrl(null, "update-arguments"), new ConductorTemplateComponent(Mode.UpdateArguments));
    Spark.post(getUrl(null, "update-main-script"), new ConductorTemplateComponent(Mode.UpdateMainScript));
    Spark.post(getUrl(null, "update-listener-script"), new ConductorTemplateComponent(Mode.UpdateListenerScript));
    Spark.post(getUrl(null, "new-listener"), new ConductorTemplateComponent(Mode.NewListener));

    SimulatorsComponent.register();
    TrialsComponent.register();
  }

  public static String getUrl(ConductorTemplate module) {
    return "/conductor-template/"
      + (module == null ? ":id" : module.getId());
  }

  public static String getUrl(ConductorTemplate module, String mode) {
    return getUrl(module) + '/' + mode;
  }

  @Override
  public void controller() {

    module = ConductorTemplate.getInstance(request.params("id"));

    if (mode == Mode.UpdateArguments) {
      if (request.queryMap().hasKey(KEY_ARGUMENTS)) {
        module.setArguments(request.queryParams(KEY_ARGUMENTS));
      }
      response.redirect(getUrl(module));
    } else if (mode == Mode.UpdateMainScript) {
      if (request.queryMap().hasKey(KEY_MAIN_SCRIPT)) {
        module.updateMainScriptContents(request.queryParams(KEY_MAIN_SCRIPT));
      }
      response.redirect(getUrl(module));
    } else if (mode == Mode.NewListener) {
      if (request.queryMap().hasKey(KEY_NAME)) {
        module.createNewListener(request.queryParams(KEY_NAME));
      }
      response.redirect(getUrl(module));
    } else if (mode == Mode.UpdateListenerScript) {
      if (request.queryMap().hasKey(KEY_LISTENER_FILENAME) || request.queryMap().hasKey(KEY_LISTENER_SCRIPT)) {
        module.updateFileContents(request.queryParams(KEY_LISTENER_FILENAME), request.queryParams(KEY_LISTENER_SCRIPT));
      }
      response.redirect(getUrl(module));
    } else {
      renderConductor();
    }
  }

  private void renderConductor() {
    new MainTemplate() {
      @Override
      protected String pageTitle() {
        return module.getName();
      }

      @Override
      protected ArrayList<String> pageBreadcrumb() {
        return new ArrayList<String>(Arrays.asList(
          Html.a(ConductorTemplatesComponent.getUrl(), ConductorTemplatesComponent.TITLE),
          module.getId()
        ));
      }

      @Override
      protected String pageContent() {
        String content = "";

        ArrayList<Lte.FormError> errors = new ArrayList<>();

        String argumentsText = "";
        for (String s : module.getArguments()) {
          argumentsText += s + "\n";
        }

        content += Lte.card(Html.faIcon("terminal") + "Basic",
            Lte.cardToggleButton(true) ,
          Html.div(null,
            Lte.readonlyTextInput("Conductor Directory", module.getLocation().toAbsolutePath().toString()),
            Lte.readonlyTextInput("Base Script", module.getScriptFileName())
          )
          , null, "collapsed-card", null);

        content +=
          Html.form(getUrl(module, "update-arguments"), Html.Method.Post,
            Lte.card(Html.faIcon("terminal") + "Arguments",
              Lte.cardToggleButton(false),
              Lte.divRow(
                Lte.divCol(Lte.DivSize.F12,
                  Lte.formTextAreaGroup(KEY_ARGUMENTS, null, argumentsText.split("\n").length, argumentsText, null),
                  Lte.formSubmitButton("success", "Update")
                )
              )
              , null, "collapsed-card.stop", null)
          );

        content +=
          Html.form(getUrl(module, "update-main-script"), Html.Method.Post,
            Lte.card(Html.faIcon("terminal") + "Main Script",
              Lte.cardToggleButton(false),
              Lte.divRow(
                Lte.divCol(Lte.DivSize.F12,
                  Lte.formDataEditorGroup(KEY_MAIN_SCRIPT, null, "ruby", module.getMainScriptContents(), errors),
                  Lte.formSubmitButton("success", "Update")
                )
              )
              , null, "collapsed-card.stop", null)
          );

        content +=
          Html.form(getUrl(module, "new-listener"), Html.Method.Post,
            Lte.card(Html.faIcon("terminal") + "New Listener",
              Lte.cardToggleButton(true),
              Lte.divRow(
                Lte.divCol(Lte.DivSize.F12,
                  Lte.formInputGroup("text", KEY_NAME, KEY_NAME, "", "", errors),
                  Lte.formSubmitButton("primary", "Create")
                )
              )
              , null, "collapsed-card", null)
          );

        for (File child : module.getLocation().toFile().listFiles()) {
          String fileName = child.getName();
          if (child.isFile() && fileName.startsWith(KEY_LISTENER + "-") && fileName.endsWith(KEY_EXT_RUBY)) {
            content +=
              Html.form(getUrl(module, "update-listener-script"), Html.Method.Post,
                Lte.card(Html.faIcon("terminal") + fileName.substring(9, fileName.length() -3) + " (Event Listener)",
                  Lte.cardToggleButton(false),
                  Lte.divRow(
                    Lte.divCol(Lte.DivSize.F12,
                      Html.inputHidden(KEY_LISTENER_FILENAME, fileName),
                      Lte.formDataEditorGroup(KEY_LISTENER_SCRIPT, null, "ruby", module.getFileContents(fileName), errors),
                      Lte.formSubmitButton("success", "Update")
                    )
                  )
                  , null, "collapsed-card.stop", null)
              );
          }
        }

        content += Lte.card(Html.faIcon("file") + "Files",
          Lte.cardToggleButton(true),
          Lte.table("table-sm", new Lte.Table() {
            @Override
            public ArrayList<Lte.TableValue> tableHeaders() {
              return null;
            }

            @Override
            public ArrayList<Lte.TableRow> tableRows() {
              ArrayList<Lte.TableRow> list = new ArrayList<>();
              for (File child : module.getLocation().toFile().listFiles()) {
                list.add(new Lte.TableRow(
                  child.getName())
                );
              }
              return list;
            }
          })
          , null, "collapsed-card", "p-0");

        return content;
      }
    }.render(this);
  }

  private ArrayList<Lte.FormError> checkCreateProjectFormError() {
    return new ArrayList<>();
  }

  public enum Mode {Default, UpdateArguments, UpdateMainScript, UpdateListenerScript, NewListener}
}