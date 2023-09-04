package jp.tkms.waffle.web.component.job;

import jp.tkms.waffle.Main;
import jp.tkms.waffle.data.internal.task.ExecutableRunTask;
import jp.tkms.waffle.data.project.workspace.run.ExecutableRun;
import jp.tkms.waffle.web.component.AbstractAccessControlledComponent;
import jp.tkms.waffle.web.component.ResponseBuilder;
import jp.tkms.waffle.web.component.computer.ComputersComponent;
import jp.tkms.waffle.web.component.project.workspace.WorkspaceComponent;
import jp.tkms.waffle.web.component.project.workspace.run.RunComponent;
import jp.tkms.waffle.web.component.project.ProjectComponent;
import jp.tkms.waffle.web.component.project.executable.ExecutableComponent;
import jp.tkms.waffle.web.template.Html;
import jp.tkms.waffle.web.template.Link;
import jp.tkms.waffle.web.template.Lte;
import jp.tkms.waffle.web.template.MainTemplate;
import jp.tkms.waffle.exception.RunNotFoundException;
import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.log.message.WarnLogMessage;
import spark.Spark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Future;

public class JobsComponent extends AbstractAccessControlledComponent {
  private Mode mode;

  public JobsComponent(Mode mode) {
    super();
    this.mode = mode;
  }

  public JobsComponent() {
    this(Mode.Default);
  }

  static public void register() {
    Spark.get(getUrl(), new ResponseBuilder(() -> new JobsComponent()));
    Spark.get(getUrl(Mode.Abort, null), new ResponseBuilder(() -> new JobsComponent(Mode.Abort)));
    Spark.get(getUrl(Mode.Cancel, null), new ResponseBuilder(() -> new JobsComponent(Mode.Cancel)));
  }

  public static String getUrl() {
    return "/jobs";
  }

  public static String getUrl(Mode mode, ExecutableRunTask job) {
    return "/jobs/" + mode.name() + "/" + (job == null ? ":id" : job.getId().getReversedBase36Code());
  }

  @Override
  public void controller() {
    if (mode == Mode.Abort) {
      ExecutableRunTask job = ExecutableRunTask.getInstance(request.params("id"));
      if (job != null) {
        try {
          job.abort();
        } catch (RunNotFoundException e) {
          ErrorLogMessage.issue(e);
        }
        response.redirect(getUrl());
      }
    } else if (mode == Mode.Cancel) {
        ExecutableRunTask job = ExecutableRunTask.getInstance(request.params("id"));
        if (job != null) {
          try {
            job.cancel();
          } catch (RunNotFoundException e) {
            ErrorLogMessage.issue(e);
          }
          response.redirect(getUrl());
        }
    } else {
     renderJobList();
    }
  }

  private void renderJobList() {
    new MainTemplate() {

      @Override
      protected boolean enableParentLink() {
        return false;
      }

      @Override
      protected ArrayList<Map.Entry<String, String>> pageNavigation() {
        return null;
      }

      @Override
      protected String pageTitle() {
        return "Jobs";
      }

      @Override
      protected ArrayList<Link> pageBreadcrumb() {
        return new ArrayList<>(Arrays.asList(
          Link.entry("Jobs")));
      }

      @Override
      protected String pageContent() {
        return
          Lte.card(null, null,
          Lte.table("table-condensed table-sm", new Lte.Table("jobs_table") {
            @Override
            public ArrayList<Lte.TableValue> tableHeaders() {
              ArrayList<Lte.TableValue> list = new ArrayList<>();
              list.add(new Lte.TableValue("width:6.5em;", "ID"));
              list.add(new Lte.TableValue("", "Project"));
              list.add(new Lte.TableValue("", "Workspace"));
              list.add(new Lte.TableValue("", "Executable"));
              list.add(new Lte.TableValue("", "Computer"));
              list.add(new Lte.TableValue("width:5em;", "JobID"));
              list.add(new Lte.TableValue("width:3em;", ""));
              list.add(new Lte.TableValue("width:1em;", ""));
              return list;
            }

            @Override
            public ArrayList<Future<Lte.TableRow>> tableRows() {
              ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
              for (ExecutableRunTask job : ExecutableRunTask.getList()) {
                list.add(Main.interfaceThreadPool.submit(() -> {
                  ExecutableRun run = job.getRun();
                  try {
                    return new Lte.TableRow(
                      Html.a(RunComponent.getUrl(job.getRun()), job.getHexCode()),
                      Html.a(
                        ProjectComponent.getUrl(run.getProject()),
                        run.getProject().getName()
                      ),
                      Html.a(
                        WorkspaceComponent.getUrl(run.getWorkspace()),
                        run.getWorkspace().getName()
                      ),
                      Html.a(
                        ExecutableComponent.getUrl(run.getExecutable()),
                        run.getExecutable().getName()
                      ),
                      Html.a(
                        ComputersComponent.getUrl(job.getComputer()),
                        job.getComputer().getName()
                      ),
                      Html.spanWithId(job.getPath().toString() + "-jobid", job.getJobId()),
                      Html.spanWithId(job.getPath().toString() + "-badge", job.getState().getStatusBadge()),
                      Html.a(getUrl(Mode.Abort, job), Html.fasIcon("times-circle"))
                    ).setAttributes(new Html.Attributes(Html.value("id", job.getPath().toString() + "-jobrow")));
                  } catch (Exception e) {
                    WarnLogMessage.issue(e);
                    return new Lte.TableRow("");
                  }
                }));
              }
              return list;
            }
          })
          , null, null, "p-0");
      }
    }.render(this);
  }

  public enum Mode {Default, Abort, Cancel}
}
