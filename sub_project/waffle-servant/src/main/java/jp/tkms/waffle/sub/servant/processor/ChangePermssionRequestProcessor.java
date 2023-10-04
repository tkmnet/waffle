package jp.tkms.waffle.sub.servant.processor;

import jp.tkms.waffle.sub.servant.Envelope;
import jp.tkms.waffle.sub.servant.message.request.ChangePermissionMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class ChangePermssionRequestProcessor extends RequestProcessor<ChangePermissionMessage> {
  protected ChangePermssionRequestProcessor() {
  }

  @Override
  protected void processIfMessagesExist(Path baseDirectory, Envelope request, Envelope response, ArrayList<ChangePermissionMessage> messageList) throws ClassNotFoundException, IOException {
    messageList.stream().parallel().forEach(message -> {
      try {
        Path path = baseDirectory.resolve(message.getPath());
        changePermission(path, message.getPermission(), message.isIgnoreDir());
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

  protected static void changePermission(Path path, String permission, boolean isIgnoreDir) {
    try {
      if (!Files.isDirectory(path) || !isIgnoreDir) {
        Files.setPosixFilePermissions(path, toPermissionSet(path, permission));
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (Files.isDirectory(path)) {
      try (Stream<Path> stream = Files.list(path)) {
        stream.forEach(p -> changePermission(p, permission, isIgnoreDir));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  protected static Set<PosixFilePermission> toPermissionSet(Path path, String permission) throws IOException {
    Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
    String t = null;
    String o = null;
    int index = 0;
    for (String s : permission.toLowerCase().split("")) {
      index += 1;
      switch (index % 3) {
        case 1:
          t = s;
          break;
        case 2:
          o = s;
          break;
        case 0:
          Set<PosixFilePermission> set = new HashSet<>();
          if ("u".equals(t) || "a".equals(t)) {
            if ("w".equals(s) || "a".equals(s)) {
              set.add(PosixFilePermission.OWNER_WRITE);
            }
            if ("r".equals(s) || "a".equals(s)) {
              set.add(PosixFilePermission.OWNER_READ);
            }
            if ("x".equals(s) || "a".equals(s)) {
              set.add(PosixFilePermission.OWNER_EXECUTE);
            }
          }
          if ("g".equals(t) || "a".equals(t)) {
            if ("w".equals(s) || "a".equals(s)) {
              set.add(PosixFilePermission.GROUP_WRITE);
            }
            if ("r".equals(s) || "a".equals(s)) {
              set.add(PosixFilePermission.GROUP_READ);
            }
            if ("x".equals(s) || "a".equals(s)) {
              set.add(PosixFilePermission.GROUP_EXECUTE);
            }
          }
          if ("o".equals(t) || "a".equals(t)) {
            if ("w".equals(s) || "a".equals(s)) {
              set.add(PosixFilePermission.OTHERS_WRITE);
            }
            if ("r".equals(s) || "a".equals(s)) {
              set.add(PosixFilePermission.OTHERS_READ);
            }
            if ("x".equals(s) || "a".equals(s)) {
              set.add(PosixFilePermission.OTHERS_EXECUTE);
            }
          }

          if ("+".equals(o)) {
            perms.addAll(set);
          } else {
            perms.removeAll(set);
          }
      }
    }

    return perms;
  }
}
