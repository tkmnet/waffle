package jp.tkms.waffle.script.ruby;

import jp.tkms.waffle.data.log.message.WarnLogMessage;
import jp.tkms.waffle.data.project.workspace.run.ExecutableRun;
import jp.tkms.waffle.data.util.Remote;
import jp.tkms.waffle.script.ScriptProcessor;
import jp.tkms.waffle.script.ruby.util.RubyScript;
import jp.tkms.waffle.submitter.AbstractSubmitter;
import org.jruby.Ruby;
import org.jruby.embed.*;

import java.nio.file.Path;

public class RubyScriptProcessor extends ScriptProcessor {
  @Override
  public void processExtractor(AbstractSubmitter submitter, ExecutableRun run, String extractorName) {
    RubyScript.process((container) -> {
      try {
        container.runScriptlet(run.getExecutable().getExtractorScript(extractorName));
        container.callMethod(Ruby.newInstance().getCurrentContext(), "exec_parameter_extract", run);
      } catch (EvalFailedException e) {
        WarnLogMessage.issue(e);
      }
    });
  }

  @Override
  public String extractorTemplate() {
    return "def parameter_extract(run)\n" +
      "end";
  }

  @Override
  public void processCollector(AbstractSubmitter submitter, ExecutableRun run, String collectorName) {

    RubyScript.process((container) -> {
      try {
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
    return "def result_collect(run, remote)\n" +
      "end";
  }

  @Override
  public String checkSyntax(Path scriptPath) {
    String error = "";
    ScriptingContainer container = new ScriptingContainer(LocalContextScope.THREADSAFE);
    try {
      container.parse(PathType.ABSOLUTE, scriptPath.toString());
    } catch (ParseFailedException e) {
      error = e.getMessage().replaceFirst("^.*\\.rb:", "");
    }
    container.terminate();
    return error;
  }
}
