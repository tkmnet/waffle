package jp.tkms.waffle.communicator;

import com.eclipsesource.json.JsonValue;
import jp.tkms.waffle.communicator.annotation.CommunicatorDescription;
import jp.tkms.waffle.data.ComputerTask;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.util.WrappedJson;
import jp.tkms.waffle.data.util.WrappedJsonArray;

import java.util.ArrayList;

@CommunicatorDescription("SSH (limited by thread and memory)")
public class ThreadAndMemoryLimitedSshSubmitter extends JobNumberLimitedSshSubmitter {
  public ThreadAndMemoryLimitedSshSubmitter(Computer computer) {
    super(computer);
  }

  @Override
  public WrappedJsonArray getFormSettings() {
    WrappedJsonArray settings = super.getFormSettings();
    {
      WrappedJson entry = new WrappedJson();
      entry.put(KEY_NAME, KEY_MAX_THREADS);
      entry.put(KEY_LABEL, "Maximum number of threads");
      entry.put(KEY_TYPE, "text");
      entry.put(KEY_CAST, "Double");
      entry.put(KEY_DEFAULT, 1.0);
      settings.add(entry);
    }
    {
      WrappedJson entry = new WrappedJson();
      entry.put(KEY_NAME, KEY_ALLOCABLE_MEMORY);
      entry.put(KEY_LABEL, "Allocable memory size (GB)");
      entry.put(KEY_TYPE, "text");
      entry.put(KEY_CAST, "Double");
      entry.put(KEY_DEFAULT, 1.0);
      settings.add(entry);
    }
    return settings;
  }

  @Override
  protected boolean isSubmittable(Computer computer, ComputerTask next, ArrayList<ComputerTask> list) {
    double globalFreeThread = 0.0;
    {
      JsonValue jsonValue = (JsonValue) computer.getParameter(KEY_MAX_THREADS, this);
      if (jsonValue.isNumber()) {
        globalFreeThread = jsonValue.asDouble();
      }
    }

    double globalFreeMemory = 0.0;
    {
      JsonValue jsonValue = (JsonValue) computer.getParameter(KEY_ALLOCABLE_MEMORY, this);
      if (jsonValue.isNumber()) {
        globalFreeMemory = jsonValue.asDouble();
      }
    }

    double thread = (next == null ? 0.0: next.getRequiredThread());
    thread += list.stream().mapToDouble(o->o.getRequiredThread()).sum();
    double memory = (next == null ? 0.0: next.getRequiredMemory());
    memory += list.stream().mapToDouble(o->o.getRequiredMemory()).sum();

    return (thread <= globalFreeThread && memory <= globalFreeMemory);
  }
}
