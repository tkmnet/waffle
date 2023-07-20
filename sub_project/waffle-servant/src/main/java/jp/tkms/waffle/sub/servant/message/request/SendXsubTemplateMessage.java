package jp.tkms.waffle.sub.servant.message.request;

public class SendXsubTemplateMessage extends AbstractRequestMessage {

  String computerName;
  String xsubType;
  public SendXsubTemplateMessage() { }

  public SendXsubTemplateMessage(String computerName, String xsubType) {
    this.computerName = computerName;
    this.xsubType = xsubType;
  }

  public String getComputerName() {
    return computerName;
  }

  public String getXsubType() {
    return xsubType;
  }
}
