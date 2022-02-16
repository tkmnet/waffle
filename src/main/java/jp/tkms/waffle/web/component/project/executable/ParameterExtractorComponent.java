package jp.tkms.waffle.web.component.project.executable;

import jp.tkms.waffle.data.project.executable.Executable;
import jp.tkms.waffle.web.component.AbstractAccessControlledComponent;
import jp.tkms.waffle.web.component.ResponseBuilder;
import jp.tkms.waffle.web.component.project.ProjectComponent;
import jp.tkms.waffle.web.component.project.ProjectsComponent;
import jp.tkms.waffle.web.template.Html;
import jp.tkms.waffle.web.template.Lte;
import jp.tkms.waffle.web.template.ProjectMainTemplate;
import jp.tkms.waffle.data.project.Project;
import jp.tkms.waffle.exception.ProjectNotFoundException;
import spark.Spark;

import java.util.ArrayList;
import java.util.Arrays;

import static jp.tkms.waffle.data.project.executable.Executable.KEY_EXTRACTOR;

public class ParameterExtractorComponent extends AbstractAccessControlledComponent {
  private Mode mode;

  private Project project;
  private Executable executable;
  private String extractorName;

  public ParameterExtractorComponent(Mode mode) {
    super();
    this.mode = mode;
  }

  public ParameterExtractorComponent() {
    this(Mode.Default);
  }

  static public void register() {
    Spark.get(getStaticUrl(null, Mode.Add), new ResponseBuilder(ParameterExtractorComponent.class, Mode.Add));
    Spark.post(getStaticUrl(null, Mode.Add), new ResponseBuilder(ParameterExtractorComponent.class, Mode.Add));
    Spark.get(getUrl(null, null), new ResponseBuilder(ParameterExtractorComponent.class));
    Spark.post(getUrl(null, null, Mode.Update), new ResponseBuilder(ParameterExtractorComponent.class, Mode.Update));
    Spark.get(getUrl(null, null, Mode.Remove), new ResponseBuilder(ParameterExtractorComponent.class, Mode.Remove));
  }

  public static String getStaticUrl(Executable executable, Mode mode) {
    return ExecutableComponent.getUrl(executable) + "/" + KEY_EXTRACTOR + "/@" + (mode == null ? ":mode" : mode.name());
  }

  public static String getUrl(Executable executable, String name) {
    return ExecutableComponent.getUrl(executable) + "/" + KEY_EXTRACTOR + "/" + (name == null ? ":name" : name + ".rb");
  }

  public static String getUrl(Executable executable, String name, Mode mode) {
    return getUrl(executable, name) + "/@" + mode.name();
  }

  @Override
  public void controller() throws ProjectNotFoundException {
    project = Project.getInstance(request.params("project"));
    executable = Executable.getInstance(project, request.params(ExecutableComponent.KEY_EXECUTABLE));

    switch (mode) {
      case Add:
        if (isPost()) {
          System.out.println("OK");
          addParameterExtractor();
        } else {
          renderAddParameterExtractorForm();
        }
        break;
      case Update:
        extractorName = request.params("name").replaceFirst("\\.rb$", "");
        updateParameterExtractor();
        break;
      case Remove:
        extractorName = request.params("name").replaceFirst("\\.rb$", "");
        removeParameterExtractor();
        break;
      default:
        extractorName = request.params("name").replaceFirst("\\.rb$", "");
        renderParameterExtractor();
    }
  }

  private void renderParameterExtractor() throws ProjectNotFoundException {
    new ProjectMainTemplate(project) {
      @Override
      protected String pageTitle() {
        return extractorName;
      }

      @Override
      protected ArrayList<String> pageBreadcrumb() {
        ArrayList<String> breadcrumb = new ArrayList<String>(Arrays.asList(
          Html.a(ProjectsComponent.getUrl(), "Projects"),
          Html.a(ProjectComponent.getUrl(project), project.getName()),
          Html.a(ExecutablesComponent.getUrl(project), "Simulators"),
          Html.a(ExecutableComponent.getUrl(executable), executable.getName()),
          "Parameter Extractor",
          extractorName
        ));
        return breadcrumb;
      }

      @Override
      protected String pageContent() {
        String content = "";

        ArrayList<Lte.FormError> errors = new ArrayList<>();

        content += Html.form(getUrl(executable, extractorName, Mode.Update), Html.Method.Post,
          Lte.card(Html.fasIcon("tasks") + "Properties", null,
            Html.div(null,
              Lte.formInputGroup("text", "name", "Name", "Name", extractorName, errors),
              Lte.formDataEditorGroup("extract_script", "Script", "ruby", executable.getExtractorScript(extractorName), errors)
            )
            , Lte.formSubmitButton("primary", "Update")
          )
        );

        content += Html.form(getUrl(executable, extractorName, Mode.Remove), Html.Method.Get,
          Lte.card(Html.fasIcon("trash-alt") + "Remove",
            Lte.cardToggleButton(true),
            Html.div(null,
              Lte.formSubmitButton("danger", "Remove")
            )
            , null, "collapsed-card", null
          )
        );

        return content;
      }
    }.render(this);
  }

  private void renderAddParameterExtractorForm() throws ProjectNotFoundException {
    new ProjectMainTemplate(project) {
      @Override
      protected String pageTitle() {
        return "Parameter Extractor";
      }

      @Override
      protected String pageSubTitle() {
        return "Add";
      }

      @Override
      protected ArrayList<String> pageBreadcrumb() {
        ArrayList<String> breadcrumb = new ArrayList<String>(Arrays.asList(
          Html.a(ProjectsComponent.getUrl(), "Projects"),
          Html.a(ProjectComponent.getUrl(project), project.getName()),
          Html.a(ExecutablesComponent.getUrl(project), "Simulators"),
          Html.a(ExecutableComponent.getUrl(executable), executable.getName()),
          "Parameter Extractor",
          "Add"
        ));
        return breadcrumb;
      }

      @Override
      protected String pageContent() {
        String content = "";

        ArrayList<Lte.FormError> errors = new ArrayList<>();

        content += Html.form(getStaticUrl(executable,Mode.Add), Html.Method.Post,
          Lte.card(Html.fasIcon("tasks") + "Properties",
            null,
            Html.div(null,
              Lte.formInputGroup("text", "name", "Name", "Name", "", errors)
            )
            , Lte.formSubmitButton("success", "Add")
          )
        );


        return content;
      }
    }.render(this);
  }

  public void addParameterExtractor() {
    String name = request.queryParams("name").replaceFirst("\\.rb$", "");
    executable.createExtractor(name);
    response.redirect(getUrl(executable, name));
  }

  public void updateParameterExtractor() {
    String name = request.queryParams("name").replaceFirst("\\.rb$", "");
    String script = request.queryParams("extract_script");
    executable.updateExtractorScript(name, script);
    response.redirect(getUrl(executable, extractorName));
  }

  public void removeParameterExtractor() {
    executable.removeExtractor(extractorName);
    response.redirect(ExecutableComponent.getUrl(executable));
  }

  public enum Mode {Default, Add, Update, Remove}
}
