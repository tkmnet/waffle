package jp.tkms.waffle.sub.servant;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import jp.tkms.waffle.sub.servant.message.AbstractMessage;
import jp.tkms.waffle.sub.servant.message.request.JobMessage;
import jp.tkms.waffle.sub.servant.message.request.*;
import jp.tkms.waffle.sub.servant.message.response.*;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MessageBundle {
  private HashMap<Class<? extends AbstractMessage>, ArrayList<AbstractMessage>> messageListSet;

  MessageBundle() {
    // for serialization
    messageListSet =  new HashMap<>();
  }

  public ArrayList<AbstractMessage> getMessageList(Class<? extends AbstractMessage> messageClass) {
    synchronized (messageListSet) {
      ArrayList<AbstractMessage> messageList = messageListSet.get(messageClass);
      if (messageList == null) {
        messageList = new ArrayList<>();
        messageListSet.put(messageClass, messageList);
      }
      return messageList;
    }
  }

  public <T extends AbstractMessage> ArrayList<T> getCastedMessageList(Class<T> messageClass) {
    ArrayList<T> castedMessageList = new ArrayList<>();
    synchronized (messageListSet) {
      for (AbstractMessage message : getMessageList(messageClass)) {
        castedMessageList.add((T) message);
      }
    }
    return castedMessageList;
  }

  public void print(String tag) {
    System.err.println(tag + "{");
    for (Map.Entry<Class<? extends AbstractMessage>, ArrayList<AbstractMessage>> entry : messageListSet.entrySet()) {
      System.err.println(entry.getKey().getName() + " : " + entry.getValue().size());
    }
    System.err.println("}");
  }

  public boolean isEmpty() {
    synchronized (messageListSet) {
      for (ArrayList<AbstractMessage> messageList : messageListSet.values()) {
        if (!messageList.isEmpty()) {
          return false;
        }
      }
      return true;
    }
  }

  public void add(AbstractMessage message) {
    synchronized (messageListSet) {
      getMessageList(message.getClass()).add(message);
    }
  }

  public void clear() {
    messageListSet.clear();
  }

  public void serialize(OutputStream stream) throws IOException {
    //GZIPOutputStream outputStream = new GZIPOutputStream(stream);;
    Kryo kryo = new Kryo();
    registerClassesToKryo(kryo);
    Output output = new Output(stream);
    kryo.writeObject(output, this);
    output.flush();
  }

  public static MessageBundle load(InputStream stream) throws IOException {
    //GZIPInputStream inputStream = new GZIPInputStream(stream);
    Kryo kryo = new Kryo();
    registerClassesToKryo(kryo);
    Input input = new Input(stream);
    MessageBundle data = kryo.readObject(input, MessageBundle.class);
    return data;
  }

  public static void registerClassesToKryo(Kryo kryo) {
    kryo.register(MessageBundle.class);
    kryo.register(java.util.HashMap.class);
    kryo.register(Class.class);
    kryo.register(ArrayList.class);
    kryo.register(HashMap.class);
    kryo.register(ExceptionMessage.class);
    kryo.register(PutTextFileMessage.class);
    kryo.register(JobMessage.class);
    kryo.register(SubmitJobMessage.class);
    kryo.register(JobExceptionMessage.class);
    kryo.register(UpdateJobIdMessage.class);
    kryo.register(CancelJobMessage.class);
    kryo.register(JobCanceledMessage.class);
    kryo.register(CollectStatusMessage.class);
    kryo.register(UpdateStatusMessage.class);
    kryo.register(SendXsubTemplateMessage.class);
    kryo.register(UpdateXsubTemplateMessage.class);
    kryo.register(UpdateResultMessage.class);
    kryo.register(SubmitPodTaskMessage.class);
    kryo.register(CollectPodTaskStatusMessage.class);
    kryo.register(PodTaskFinishedMessage.class);
    kryo.register(CollectPodStatusMessage.class);
    kryo.register(UpdatePodStatusMessage.class);
    kryo.register(PodTaskRefusedMessage.class);
    kryo.register(CancelPodTaskMessage.class);
    kryo.register(PodTaskCanceledMessage.class);
    kryo.register(PutValueMessage.class);
    kryo.register(PutFileMessage.class);
    kryo.register(SendValueMessage.class);
    kryo.register(ChangePermissionMessage.class);
    kryo.register(ConfirmPreparingMessage.class);
    kryo.register(UpdatePreparedMessage.class);
    kryo.register(StorageWarningMessage.class);
    kryo.register(RequestRepreparingMessage.class);
    kryo.register(SyncRequestMessage.class);
    kryo.register(SyncResponseMessage.class);
    kryo.register(CheckJobIdMessage.class);
    kryo.register(PodLostMessage.class);
  }
}
