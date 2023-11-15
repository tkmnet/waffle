package jp.tkms.waffle.communicator.processor;

import jp.tkms.waffle.communicator.AbstractSubmitter;
import jp.tkms.waffle.communicator.util.SelfCommunicativeEnvelope;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.util.ComputerState;
import jp.tkms.waffle.data.util.WrappedJson;
import jp.tkms.waffle.sub.servant.Envelope;
import jp.tkms.waffle.sub.servant.message.response.ExceptionMessage;
import jp.tkms.waffle.sub.servant.message.response.UpdateXsubTemplateMessage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

public class UpdateXsubTemplateMessageProcessor extends ResponseProcessor<UpdateXsubTemplateMessage> {
  @Override
  protected void processIfMessagesExist(AbstractSubmitter submitter, ArrayList<UpdateXsubTemplateMessage> messageList) throws ClassNotFoundException, IOException {
    for (UpdateXsubTemplateMessage message : messageList) {
      Computer target = Computer.getInstance(message.getComputerName());
      target.setXsubTemplate(new WrappedJson(message.getTemplate()));
      target.setParameters(target.getParameters());
      target.setState(ComputerState.Viable);
      target.setMessage("");
      Computer.updateXsubOptions(message.getOptions());
    }
  }
}
