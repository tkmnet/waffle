package jp.tkms.waffle.sub.servant.message.response;

public class PodLostMessage extends AbstractResponseMessage {
  String podId;

  public PodLostMessage() { }

  public PodLostMessage(String podId) {
    this.podId = podId;
  }

  public String getPodId() {
    return podId;
  }
}
