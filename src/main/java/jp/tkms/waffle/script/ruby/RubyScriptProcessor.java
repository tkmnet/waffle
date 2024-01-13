package jp.tkms.waffle.script.ruby;

import jp.tkms.waffle.Constants;
import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.log.message.WarnLogMessage;
import jp.tkms.waffle.data.project.workspace.HasLocalPath;
import jp.tkms.waffle.data.project.workspace.convertor.WorkspaceConvertorRun;
import jp.tkms.waffle.data.project.workspace.run.AbstractRun;
import jp.tkms.waffle.data.project.workspace.run.ExecutableRun;
import jp.tkms.waffle.data.project.workspace.run.ProcedureRun;
import jp.tkms.waffle.data.util.Remote;
import jp.tkms.waffle.data.util.StringKeyHashMap;
import jp.tkms.waffle.script.ProcessorDescription;
import jp.tkms.waffle.script.ScriptProcessor;
import jp.tkms.waffle.script.ruby.util.RubyScript;
import jp.tkms.waffle.communicator.AbstractSubmitter;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.io.output.NullPrintStream;
import org.jruby.Ruby;
import org.jruby.RubyInstanceConfig;
import org.jruby.embed.*;
import org.jruby.embed.io.WriterOutputStream;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;

@ProcessorDescription("Ruby")
public class RubyScriptProcessor extends ScriptProcessor {
  public static final String EXTENSION = Constants.EXT_RUBY;

  @Override
  public String checkSyntax(Path scriptPath) {
    String error = "";
    ScriptingContainer container = new ScriptingContainer(LocalContextScope.SINGLETON);
    try {
      container.parse(PathType.ABSOLUTE, scriptPath.toString());
    } catch (ParseFailedException e) {
      error = e.getMessage().replaceFirst("^.*?:", "");
    }
    try {
      container.finalize();
    } catch (Throwable e) {
      ErrorLogMessage.issue(e);
    }
    return error;
  }

  @Override
  public void processProcedure(ProcedureRun run, StringKeyHashMap<HasLocalPath> referable, String script, ArrayList<Object> arguments) {
    run.isSuccess(
      RubyScript.process((container) -> {
        try {
          container.runScriptlet(procedureTemplate());
          container.runScriptlet(script);
          container.callMethod(Ruby.newInstance().getCurrentContext(), "exec_procedure", run, referable);
        } catch (EvalFailedException e) {
          WarnLogMessage.issue(e);
        }
      })
    );
  }

  @Override
  public String procedureTemplate() {
    return
      "def procedure(me, refs)\n" +
      "end\n";
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
