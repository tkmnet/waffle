package jp.tkms.waffle.sub.servant.processor;

import jp.tkms.waffle.sub.servant.Envelope;
import jp.tkms.waffle.sub.servant.XsubFile;
import jp.tkms.waffle.sub.servant.message.request.SendXsubTemplateMessage;
import jp.tkms.waffle.sub.servant.message.response.ExceptionMessage;
import jp.tkms.waffle.sub.servant.message.response.UpdateXsubTemplateMessage;
import org.jruby.embed.LocalContextScope;
import org.jruby.embed.LocalVariableBehavior;
import org.jruby.embed.PathType;
import org.jruby.embed.ScriptingContainer;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SendXsubTemplateRequestProcessor extends RequestProcessor<SendXsubTemplateMessage> {
  public static final String XSUB_TYPE = "XSUB_TYPE";

  protected SendXsubTemplateRequestProcessor() {
  }

  @Override
  protected void processIfMessagesExist(Path baseDirectory, Envelope request, Envelope response, ArrayList<SendXsubTemplateMessage> messageList) throws ClassNotFoundException, IOException {
    Map environments = new HashMap(System.getenv());
    for (SendXsubTemplateMessage message : messageList) {
      environments.put(XSUB_TYPE, message.getXsubType());
    }

    StringWriter outputWriter = new StringWriter();
    ScriptingContainer container = new ScriptingContainer(LocalContextScope.SINGLETHREAD, LocalVariableBehavior.TRANSIENT);
    container.setEnvironment(environments);
    container.setArgv(new String[]{"-t"});
    container.setOutput(outputWriter);
    container.runScriptlet("require 'jruby'");
    container.runScriptlet(PathType.ABSOLUTE, XsubFile.getXsubPath(baseDirectory).toString());
    outputWriter.flush();
    String template = outputWriter.toString();
    outputWriter.close();
    try {
      container.finalize();
    } catch (Throwable e) {
      response.add(new ExceptionMessage(e.getMessage()));
    }

    environments.put(XSUB_TYPE, "");
    outputWriter = new StringWriter();
    String options = "[]";
    try {
      container = new ScriptingContainer(LocalContextScope.SINGLETHREAD, LocalVariableBehavior.TRANSIENT);
      container.setEnvironment(environments);
      container.setOutput(outputWriter);
      container.runScriptlet("require 'jruby'");
      container.runScriptlet("Dir[File.join('" + XsubFile.getXsubPath(baseDirectory).getParent().getParent().resolve("lib") + "/**/*.rb' )].each {|f| require f}");
      container.runScriptlet("p Xsub::Scheduler.descendants.map {|k| k.name.split('::').last }");
      outputWriter.flush();
      options = outputWriter.toString();
      outputWriter.close();
    } catch (Throwable e) {
      response.add(new ExceptionMessage(e.getMessage()));
    } finally {
      try {
        container.finalize();
      } catch (Throwable e) {
        response.add(new ExceptionMessage(e.getMessage()));
      }
    }

    response.add(new UpdateXsubTemplateMessage(messageList.get(0), template, options));
  }
}
