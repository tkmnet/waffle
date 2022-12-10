package jp.tkms.waffle.script.wnj;

import jp.tkms.waffle.Constants;
import jp.tkms.waffle.communicator.AbstractSubmitter;
import jp.tkms.waffle.data.log.message.WarnLogMessage;
import jp.tkms.waffle.data.project.workspace.HasLocalPath;
import jp.tkms.waffle.data.project.workspace.convertor.WorkspaceConvertorRun;
import jp.tkms.waffle.data.project.workspace.run.ExecutableRun;
import jp.tkms.waffle.data.project.workspace.run.ProcedureRun;
import jp.tkms.waffle.data.util.Remote;
import jp.tkms.waffle.data.util.StringKeyHashMap;
import jp.tkms.waffle.script.ProcessorDescription;
import jp.tkms.waffle.script.ScriptProcessor;
import jp.tkms.waffle.script.ruby.util.RubyScript;
import org.jruby.Ruby;
import org.jruby.embed.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;

@ProcessorDescription("Visual Programming")
public class WaffleNodeJsonScriptProcessor extends ScriptProcessor {
  public static final String EXTENSION = Constants.EXT_WNJ;

  @Override
  public String checkSyntax(Path scriptPath) {
    String error = "";
    return error;
  }

  @Override
  public void processProcedure(ProcedureRun run, StringKeyHashMap<HasLocalPath> referable, String script, ArrayList<Object> arguments) {
    RubyScript.process((container) -> {
      try {
        container.runScriptlet(procedureTemplate());
        container.runScriptlet(script);
        container.callMethod(Ruby.newInstance().getCurrentContext(), "exec_procedure", run, referable);
      } catch (EvalFailedException e) {
        WarnLogMessage.issue(e);
      }
    });
  }

  @Override
  public String procedureTemplate() {
    return "{\"graph\":{\"id\":\"" + UUID.randomUUID() + "\"," +
      "\"nodes\":[{\"type\":\"Begin\",\"id\":\"" + UUID.randomUUID() + "\",\"title\":\"Begin\"," +
      "\"inputs\":{},\"outputs\":{\"next\":{\"id\":\"" + UUID.randomUUID() + "\",\"value\":0}," +
      "\"references\":{\"id\":\"" + UUID.randomUUID() + "\",\"value\":0}}," +
      "\"position\":{\"x\":100,\"y\":70},\"width\":200,\"twoColumn\":false}]," +
      "\"connections\":[],\"inputs\":[],\"outputs\":[]},\"graphTemplates\":[]}";
  }

  @Override
  public void processExtractor(AbstractSubmitter submitter, ExecutableRun run, String extractorName) {
    RubyScript.process((container) -> {
      try {
        container.runScriptlet(extractorTemplate());
        container.runScriptlet(run.getExecutable().getExtractorScript(extractorName));
        container.callMethod(Ruby.newInstance().getCurrentContext(), "exec_parameter_extract", run);
      } catch (EvalFailedException e) {
        WarnLogMessage.issue(e);
      }
    });
  }

  @Override
  public String extractorTemplate() {
    return "def parameter_extract(me)\n" +
      "end\n";
  }

  @Override
  public void processCollector(AbstractSubmitter submitter, ExecutableRun run, String collectorName) {

    RubyScript.process((container) -> {
      try {
        container.runScriptlet(collectorTemplate());
        container.runScriptlet(run.getExecutable().getCollectorScript(collectorName));
        Remote remote = new Remote(run, submitter);
        container.callMethod(Ruby.newInstance().getCurrentContext(), "exec_result_collect", run, remote);
      } catch (EvalFailedException e) {
        WarnLogMessage.issue(e);
      }
    });
  }

  @Override
  public String collectorTemplate() {
    return "def result_collect(me, remote)\n" +
      "end\n";
  }

  @Override
  public void processConvertor(WorkspaceConvertorRun run) {
    RubyScript.process((container) -> {
      try {
        container.runScriptlet(collectorTemplate());
        container.runScriptlet(run.getConvertor().getScript());
        container.callMethod(Ruby.newInstance().getCurrentContext(), "exec_convertor", run);
      } catch (EvalFailedException e) {
        WarnLogMessage.issue(e);
      }
    });
  }

  @Override
  public String convertorTemplate() {
    return "def convertor(me) \n" +
      "end\n";
  }
}
