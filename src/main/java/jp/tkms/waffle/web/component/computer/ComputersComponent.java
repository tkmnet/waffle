package jp.tkms.waffle.web.component.computer;

import com.eclipsesource.json.JsonValue;
import jp.tkms.waffle.Main;
import jp.tkms.waffle.communicator.AbstractSubmitter;
import jp.tkms.waffle.communicator.PodWrappedSubmitter;
import jp.tkms.waffle.communicator.annotation.CommunicatorDescriptionUtil;
import jp.tkms.waffle.data.util.WrappedJson;
import jp.tkms.waffle.data.util.WrappedJsonArray;
import jp.tkms.waffle.inspector.Inspector;
import jp.tkms.waffle.web.component.AbstractAccessControlledComponent;
import jp.tkms.waffle.web.component.ResponseBuilder;
import jp.tkms.waffle.web.component.websocket.PushNotifier;
import jp.tkms.waffle.web.template.Html;
import jp.tkms.waffle.web.template.Lte;
import jp.tkms.waffle.web.template.MainTemplate;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.internal.task.ExecutableRunTask;
import spark.Spark;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ComputersComponent extends AbstractAccessControlledComponent {
  public static final String COMPUTERS = "Computers";
  public static final String COMPUTER = "Computer";

  private static final String KEY_WORKBASE = "work_base_dir";
  private static final String KEY_JVM_ACTIVATION_COMMAND = "jvm_activation_command";
  private static final String KEY_POLLING = "polling_interval";
  private static final String KEY_MAX_THREADS = "maximum_threads";
  private static final String KEY_ALLOCABLE_MEMORY = "allocable_memory";
  private static final String KEY_NUMBER_OF_CALCULATION_NODE = "number_of_calculation_node";
  private static final String KEY_PARAMETERS = "parameters";
  private static final String KEY_ENVIRONMENTS = "environments";
  private static final String KEY_NOTE = "note";

  public enum Mode {Default, New, Update}
  private Mode mode = null;
  private Computer computer = null;
  private static final LinkedHashMap<String, String> submitterMap = new LinkedHashMap<>();

  public ComputersComponent(Mode mode) {
    super();
    this.mode = mode;
  }

  public ComputersComponent() {
    this(Mode.Default);
  }

  static public void register() {
    for (Class<AbstractSubmitter>type : Computer.getSubmitterTypeList()) {
      submitterMap.put(type.getCanonicalName(), CommunicatorDescriptionUtil.getDescription(type));
    }

    Spark.get(getUrl(), new ResponseBuilder(() -> new ComputersComponent()));
    Spark.get(getUrl(Mode.New), new ResponseBuilder(() -> new ComputersComponent(Mode.New)));
    Spark.post(getUrl(Mode.New), new ResponseBuilder(() -> new ComputersComponent(Mode.New)));
    Spark.get(getUrl(null, null), new ResponseBuilder(() -> new ComputersComponent()));
    Spark.post(getUrl(null, Mode.Update), new ResponseBuilder(() -> new ComputersComponent(Mode.Update)));
  }

  public static String getUrl() {
    return "/COMPUTER";
  }

  public static String getUrl(Mode mode) {
    return "/COMPUTER/@" + mode.name();
  }

  public static String getUrl(Computer computer) {
    return "/COMPUTER/" + (computer == null ? ":name" : computer.getName());
  }

  public static String getUrl(Computer computer, Mode mode) {
    return getUrl(computer) + (mode == null ? "" : "/@" + mode.name());
  }

  @Override
  public void controller() {
    if (request.params("name") == null) {
      switch (mode) {
        case New:
          if (isPost()) {
            ArrayList<Lte.FormError> errors = checkCreateProjectFormError();
            if (errors.isEmpty()) {
              addComputer();
            } else {
              renderNewForm(errors);
            }
          } else {
            renderNewForm(new ArrayList<>());
          }
          break;
        default:
          renderComputerList();
      }
    } else {
      computer = Computer.find(request.params("name"));
      switch (mode) {
        case Update:
          updateComputer();
          break;
        default:
          renderComputer();
      }
    }
  }

  private void renderNewForm(ArrayList<Lte.FormError> errors) {
    new MainTemplate() {
      @Override
      protected ArrayList<Map.Entry<String, String>> pageNavigation() {
        return null;
      }

      @Override
      protected String pageTitle() {
        return "@New";
      }

      @Override
      protected String pageSubTitle() {
        return COMPUTERS;
      }

      @Override
      protected ArrayList<String> pageBreadcrumb() {
        return new ArrayList<String>(Arrays.asList(
          Html.a(ComputersComponent.getUrl(), COMPUTERS),
          "@New"));
      }

      @Override
      protected String pageContent() {
        return
          Html.form(getUrl(Mode.New), Html.Method.Post,
            Lte.card("New Computer", null,
              Html.div(null,
                Html.inputHidden("cmd", "new"),
                Lte.formInputGroup("text", "name", null, "Name", null, errors),
                Lte.formSelectGroup("type", "type", submitterMap, errors)
              ),
              Lte.formSubmitButton("success", "Add"),
              "card-warning", null
            )
          );
      }
    }.render(this);
  }

  private ArrayList<Lte.FormError> checkCreateProjectFormError() {
    return new ArrayList<>();
  }

  private void renderComputerList() {
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
        return COMPUTERS;
      }

      @Override
      protected ArrayList<String> pageBreadcrumb() {
        return new ArrayList<String>(Arrays.asList(
          COMPUTERS));
      }

      @Override
      protected String pageTool() {
        return Html.a(getUrl(Mode.New),
            null, null, Lte.badge("primary", null, Html.fasIcon("plus-square") + "NEW")
          );
      }

      @Override
      protected String pageContent() {
        return Lte.card(null, null,
          Lte.table("table-condensed table-nooverflow", new Lte.Table() {
            @Override
            public ArrayList<Lte.TableValue> tableHeaders() {
              ArrayList<Lte.TableValue> list = new ArrayList<>();
              list.add(new Lte.TableValue("", "Name"));
              list.add(new Lte.TableValue("", "Note"));
              list.add(new Lte.TableValue("width:4em;", "Job"));
              list.add(new Lte.TableValue("width:8em;", ""));
              return list;
            }

            @Override
            public ArrayList<Future<Lte.TableRow>> tableRows() {
              ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
              for (Computer computer : Computer.getList()) {
                list.add(Main.interfaceThreadPool.submit(() -> {
                  return new Lte.TableRow(
                    Html.a(ComputersComponent.getUrl(computer), null, null,  computer.getName()),
                    Html.sanitaize(computer.getNote()),
                    String.valueOf(ExecutableRunTask.getList(computer).size()),
                    computer.getState().getStatusBadge()
                  );
                }));
              }
              return list;
            }
          })
          , null, null, "p-0");
      }
    }.render(this);
  }

  private void addComputer() {
    Computer computer = Computer.create(request.queryParams("name"), request.queryParams("type"));
    response.redirect(ComputersComponent.getUrl(computer));
  }

  private void renderComputer() {
    new MainTemplate() {
      @Override
      protected ArrayList<Map.Entry<String, String>> pageNavigation() {
        return null;
      }

      @Override
      protected String pageTitle() {
        return computer.getName();
      }

      @Override
      protected String pageSubTitle() {
        return COMPUTER;
      }

      @Override
      protected ArrayList<String> pageBreadcrumb() {
        return new ArrayList<String>(Arrays.asList(
          Html.a(ComputersComponent.getUrl(), COMPUTERS),
          computer.getName()
        ));
      }

      @Override
      protected String pageContent() {
        String content = "";

        if (computer.isLocked()) {
          return Lte.loading() + Html.javascript("setTimeout(function(){location.reload();}, 10000);");
        }

        ArrayList<Lte.FormError> errors = new ArrayList<>();

        content += ("".equals(computer.getMessage()) ? "" : Lte.errorNoticeTextAreaGroup(computer.getMessage()));
        content += Html.form(getUrl(computer, Mode.Update), Html.Method.Post,
          Lte.card(Html.fasIcon("terminal") + "Properties",
            computer.getState().getStatusBadge(),
            Html.div(null,
              Lte.formTextAreaGroup(KEY_NOTE, "Note", computer.getNote(), null),
              Lte.readonlyTextInput("Submitter Type", computer.getSubmitterType()),
              createForm(computer, errors),
              //Lte.formInputGroup("text", KEY_WORKBASE, "Work base directory on the computer", "", computer.getWorkBaseDirectory(), errors),
              //Lte.formInputGroup("text", KEY_JVM_ACTIVATION_COMMAND, "Java virtual machine activation command", "", computer.getJvmActivationCommand(), errors),
              //Lte.formInputGroup("text", KEY_MAX_THREADS, "Maximum number of threads", "", computer.getMaximumNumberOfThreads().toString(), errors),
              //Lte.formInputGroup("text", KEY_ALLOCABLE_MEMORY, "Allocable memory size (GB)", "", computer.getAllocableMemorySize().toString(), errors),
              //Lte.formInputGroup("text", KEY_NUMBER_OF_CALCULATION_NODE, "Maximum number of jobs", "", computer.getMaximumNumberOfJobs().toString(), errors),
              //Lte.formInputGroup("text", KEY_POLLING, "Polling interval (seconds)", "", computer.getPollingInterval().toString(), errors),
              Lte.formJsonEditorGroup(KEY_ENVIRONMENTS, "Environments", "tree", computer.getEnvironments().toString(), null),
              Lte.formJsonEditorGroup(KEY_PARAMETERS, "Parameters", "tree",  computer.getParametersWithDefaultParametersFiltered().toString(), null)
            )
            , Lte.formSubmitButton("success", "Update")
          )
        );

        return content;
      }
    }.render(this);
  }

  private String createForm(Computer computer, ArrayList<Lte.FormError> errors) {
    String html = "";
    AbstractSubmitter submitter = AbstractSubmitter.getInstance(Inspector.Mode.Normal, computer);
    for (JsonValue value : submitter.getFormSettings().toJsonArray()) {
      WrappedJson entry = new WrappedJson(value.asObject());
      String type = entry.getString(AbstractSubmitter.KEY_TYPE, "text");
      String name = entry.getString(AbstractSubmitter.KEY_NAME, "_" + System.nanoTime());
      Object object = computer.getParameter(name);
      switch (type) {
        case "xsub":
          html += Lte.formSelectGroup(name, entry.getString(AbstractSubmitter.KEY_LABEL, ""),
            Computer.getXsubTypeOptions(), submitter.getXsubType(), errors);
          break;
        default:
          html += Lte.formInputGroup(type, name,
            entry.getString(AbstractSubmitter.KEY_LABEL, ""),
            entry.getString(AbstractSubmitter.KEY_PLACEHOLDER, ""),
            (object == null ? "" : object.toString()), errors);
      }
    }
    return html;
  }

  private void updateComputer() {
    //computer.setWorkBaseDirectory(request.queryParams(KEY_WORKBASE));
    //computer.setJvmActivationCommand(request.queryParams(KEY_JVM_ACTIVATION_COMMAND));
    //computer.setMaximumNumberOfThreads(Double.parseDouble(request.queryParams(KEY_MAX_THREADS)));
    //computer.setAllocableMemorySize(Double.parseDouble(request.queryParams(KEY_ALLOCABLE_MEMORY)));
    //computer.setMaximumNumberOfJobs(Integer.parseInt(request.queryParams(KEY_NUMBER_OF_CALCULATION_NODE)));
    //computer.setPollingInterval(Integer.parseInt(request.queryParams(KEY_POLLING)));
    computer.setEnvironments(new WrappedJson(request.queryParams(KEY_ENVIRONMENTS)));

    /* TODO: refactoring following descriptions */
    WrappedJson parameters = new WrappedJson(request.queryParams(KEY_PARAMETERS));
    AbstractSubmitter submitter = AbstractSubmitter.getInstance(Inspector.Mode.Normal, computer);
    WrappedJsonArray settings = submitter.getFormSettings();
    for (JsonValue value : settings.toJsonArray()) {
      WrappedJson entry = new WrappedJson(value.asObject());
      String name = entry.getString(AbstractSubmitter.KEY_NAME, "_");
      switch (entry.getString(AbstractSubmitter.KEY_CAST, "")) {
        case "Integer":
          parameters.put(name, Integer.parseInt(request.queryParams(name)));
          break;
        case "Double":
          parameters.put(name, Double.parseDouble(request.queryParams(name)));
          break;
        default:
          parameters.put(name, request.queryParams(name));
      }
    }
    parseAndChangeParameterValue(parameters, PodWrappedSubmitter.KEY_SHUTDOWN_PREPARATION_MARGIN);
    parseAndChangeParameterValue(parameters, PodWrappedSubmitter.KEY_FORCE_SHUTDOWN);
    parseAndChangeParameterValue(parameters, PodWrappedSubmitter.KEY_EMPTY_TIMEOUT);
    computer.setParameters(parameters);

    computer.setNote(request.queryParams(KEY_NOTE));
    computer.lock(true);
    String uri = request.uri().replaceFirst("/@.*$", "");
    PushNotifier.sendReloadIfSameUriMessage(uri);
    Main.interfaceThreadPool.submit(() -> {
      computer.update();
      computer.lock(false);
      try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException e) { }
      PushNotifier.sendReloadIfSameUriMessage(uri);
    });
    response.redirect(getUrl(computer));
  }

  private static void parseAndChangeParameterValue(WrappedJson parameters, String key) {
    Object value = parameters.get(key);
    if (value instanceof String) {
      long val = 0;
      int[] coef = {86400, 3600, 60, 1};
      String[] tmp = ((String)value).toLowerCase().split(":");
      if (tmp.length > 1) {
        // style: 0:1:00:00
        for (int d = 1; d <= tmp.length; d += 1) {
          val += Long.valueOf(tmp[tmp.length - d]) * coef[coef.length - d];
        }
      } else {
        // style: 0d1h0m0s
        char[] unit = {'d', 'h', 'm'};
        tmp = tmp[0].split("s");
        for (int i = 0; i < unit.length; i += 1) {
          if (tmp[0].contains(String.valueOf(unit[i]))) {
            tmp = tmp[0].split(String.valueOf(unit[i]));
            val += coef[i] * Long.valueOf(tmp[0]);
            if (tmp.length <= 1) {
              tmp[0] = "0";
              break;
            }
            tmp[0] = tmp[1];
          }
        }
        val += Long.valueOf(tmp[0]);
      }
      parameters.put(key, val);
    }
  }
}
