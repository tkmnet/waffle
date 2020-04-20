package jp.tkms.waffle.component;

import jp.tkms.waffle.component.template.Html;
import jp.tkms.waffle.component.template.Lte;
import jp.tkms.waffle.component.template.MainTemplate;
import jp.tkms.waffle.data.ConductorRun;
import jp.tkms.waffle.data.Project;
import jp.tkms.waffle.data.SimulatorRun;
import spark.Spark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class TrialsComponent extends AbstractAccessControlledComponent {
  private Mode mode;

  ;
  private Project project;
  private ConductorRun conductorRun;
  public TrialsComponent(Mode mode) {
    super();
    this.mode = mode;
  }

  public TrialsComponent() {
    this(Mode.Default);
  }

  static public void register() {
    Spark.get(getUrl(null), new TrialsComponent());
  }

  public static String getUrl(Project project) {
    return "/trials/" + (project == null ? ":project/:id" : project.getId() + "/ROOT");
  }

  public static String getUrl(Project project, ConductorRun conductorRun) {
    return "/trials/" + (project == null ? ":project/:id" : project.getId() + "/" + conductorRun.getId());
  }

  public static String getUrl(Project project, ConductorRun conductorRun, String mode) {
    return getUrl(project, conductorRun) + "/" + mode;
  }

  @Override
  public void controller() {
    project = Project.getInstance(request.params("project"));
    String requestedId = request.params("id");

    if (requestedId.equals(ConductorRun.ROOT_NAME)){
      conductorRun = ConductorRun.getRootInstance(project);
    } else {
      conductorRun = ConductorRun.getInstance(project, requestedId);
    }

    renderTrialsList();
  }

  private ArrayList<Lte.FormError> checkCreateProjectFormError() {
    return new ArrayList<>();
  }

  private void renderTrialsList() {
    new MainTemplate() {
      @Override
      protected String pageTitle() {
        return "Trials";
      }

      @Override
      protected ArrayList<String> pageBreadcrumb() {
        ArrayList<String> breadcrumb = new ArrayList<String>(Arrays.asList(
          Html.a(ProjectsComponent.getUrl(), "Projects"),
          Html.a(ProjectComponent.getUrl(project), project.getShortId()),
          "Trials"
        ));
        ArrayList<String> conductorRunList = new ArrayList<>();
        ConductorRun parent = conductorRun.getParent();
        while (parent != null) {
          conductorRunList.add(Html.a(TrialsComponent.getUrl(project, parent), parent.getShortId()));
          parent = parent.getParent();
        }
        Collections.reverse(conductorRunList);
        breadcrumb.addAll(conductorRunList);
        breadcrumb.add(conductorRun.getId());
        return breadcrumb;
      }

      @Override
      protected String pageContent() {
        String contents = "";
        contents += Html.javascript(
          "var runUpdated = function(id) {location.reload();};",
          "var runCreated = function(id) {location.reload();};"
        );

        if (! conductorRun.getParameters().isEmpty()) {
          contents += Lte.card(Html.faIcon("poll") + "Parameters",
            Lte.cardToggleButton(true),
            Lte.divRow(
              Lte.divCol(Lte.DivSize.F12,
                Lte.readonlyTextAreaGroup("", null, 10, conductorRun.getParameters().toString(2))
              )
            )
            , null);
        }

        contents += Lte.card(null, null,
          Lte.table("table-condensed table-sm", new Lte.Table() {
            @Override
            public ArrayList<Lte.TableValue> tableHeaders() {
              ArrayList<Lte.TableValue> list = new ArrayList<>();
              list.add(new Lte.TableValue("width:6.5em;", "ID"));
              list.add(new Lte.TableValue("", "Name"));
              list.add(new Lte.TableValue("width:2em;", ""));
              return list;
            }

            @Override
            public ArrayList<Lte.TableRow> tableRows() {
              ArrayList<Lte.TableRow> list = new ArrayList<>();
              for (ConductorRun run : ConductorRun.getList(project, TrialsComponent.this.conductorRun)) {
                list.add(new Lte.TableRow(
                  Html.a(getUrl(project, run), null, null, run.getShortId()),
                  run.getName(),
                  Html.spanWithId(run.getId() + "-badge", getStatusBadge(run))
                  )
                );
              }
              return list;
            }
          })
          , null, null, "p-0");

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
                  run.getHost().getName(),
                  Html.spanWithId(run.getId() + "-badge", JobsComponent.getStatusBadge(run))
                ));
              }
              return list;
            }
          })
          , null, null, "p-0");

        return contents;
      }
    }.render(this);
  }

  public static String getStatusBadge(ConductorRun run) {
    switch (run.getPhase()) {
      case 0:
        return Lte.badge("warning", new Html.Attributes(Html.value("style","width:6em;")), "Running");
      case 1:
        return Lte.badge("success", new Html.Attributes(Html.value("style","width:6em;")), "Finished");
    }
    return null;
  }

  public enum Mode {Default}
}