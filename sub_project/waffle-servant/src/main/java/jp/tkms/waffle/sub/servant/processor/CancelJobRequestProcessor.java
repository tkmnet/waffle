package jp.tkms.waffle.sub.servant.processor;

import jp.tkms.waffle.sub.servant.Constants;
import jp.tkms.waffle.sub.servant.Envelope;
import jp.tkms.waffle.sub.servant.XsubFile;
import jp.tkms.waffle.sub.servant.message.request.CancelJobMessage;
import jp.tkms.waffle.sub.servant.message.response.ExceptionMessage;
import jp.tkms.waffle.sub.servant.message.response.JobCanceledMessage;
import org.jruby.embed.LocalContextScope;
import org.jruby.embed.LocalVariableBehavior;
import org.jruby.embed.PathType;
import org.jruby.embed.ScriptingContainer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CancelJobRequestProcessor extends RequestProcessor<CancelJobMessage> {
  public static final String XSUB_TYPE = "XSUB_TYPE";
  public static final String NONE = "None";

  protected CancelJobRequestProcessor() {
  }

  @Override
  protected void processIfMessagesExist(Path baseDirectory, Envelope request, Envelope response, ArrayList<CancelJobMessage> messageList) throws ClassNotFoundException, IOException {
    Map environments = new HashMap(System.getenv());
    if (!environments.containsKey(XSUB_TYPE)) {
      environments.put(XSUB_TYPE, NONE);
    }

    messageList.stream().parallel().forEach(message -> {
      ScriptingContainer container = new ScriptingContainer(LocalContextScope.SINGLETHREAD, LocalVariableBehavior.TRANSIENT);
      synchronized (this) {
        try {
          container.setEnvironment(environments);
          container.setArgv(new String[]{message.getJobId()});
          container.runScriptlet("require 'jruby'");
          container.runScriptlet(PathType.ABSOLUTE, XsubFile.getXdelPath(baseDirectory).toString());
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      container.clear();
      try {
        container.finalize();
      } catch (Throwable e) {
        response.add(new ExceptionMessage(e.getMessage()));
      }
      response.add(new JobCanceledMessage(message));
      response.add(message.getWorkingDirectory().resolve(Constants.STDOUT_FILE));
      response.add(message.getWorkingDirectory().resolve(Constants.STDERR_FILE));
    });
  }
}
