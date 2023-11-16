package jp.tkms.waffle.inspector;

import com.eclipsesource.json.JsonValue;
import jp.tkms.waffle.Main;
import jp.tkms.waffle.communicator.AbstractSubmitter;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.util.StringFileUtil;
import jp.tkms.waffle.sub.servant.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class LocalInspector extends Inspector {
  LocalInspector(Mode mode, Computer computer) {
    super(mode, computer);

    Path notifierPath = Paths.get(computer.getParameter(AbstractSubmitter.KEY_WORKBASE).toString()).resolve(Constants.NOTIFIER);
    StringFileUtil.write(notifierPath, UUID.randomUUID().toString());
    Main.registerFileChangeEventListener(notifierPath.getParent(), () -> {
      notifyUpdate();
    });
  }

  public void notifyUpdate(){
    int interval = 10;
    {
      JsonValue value = (JsonValue) computer.getParameter(AbstractSubmitter.KEY_POLLING);
      if (value != null && value.isNumber()) {
        interval = value.asInt();
      }
    }
    waitCount = toMilliSecond(interval) - waitingStep;
  }
}
