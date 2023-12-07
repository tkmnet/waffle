package jp.tkms.waffle.sub.servant;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class EventDirReader {
  public static final String SECTION_SEPARATING_MARK = "\n";

  private Path newDirPath;
  private Path curDirPath;
  private Path remainsFilePath;
  public EventDirReader(Path baseDirectory, Path workingDirectory) {
    if (!workingDirectory.isAbsolute()) {
      workingDirectory = baseDirectory.resolve(workingDirectory);
    }
    this.newDirPath = workingDirectory.resolve(Constants.EVENT_DIR).resolve("new");
    this.curDirPath = workingDirectory.resolve(Constants.EVENT_DIR).resolve("cur");
    this.remainsFilePath = workingDirectory.resolve(Constants.EVENT_DIR).resolve(Constants.REMAINS_FILE);
  }

  public void process(BiConsumer<String, String> consumer) {
    if (Files.isDirectory(newDirPath)) {
      try (Stream<Path> stream = Files.list(newDirPath)) {
        Files.createDirectories(curDirPath);
        stream.forEach(p -> {
          if (!Files.isRegularFile(p)) { return; }
          try {
            String[] data = (new String(Files.readAllBytes(p))).trim().split(SECTION_SEPARATING_MARK, 2);
            if (data.length == 2) {
              consumer.accept(data[0], data[1]);
            }
          } catch (IOException e) {
            //NOP
          }
          try {
            Files.move(p, curDirPath.resolve(p.getFileName()));
          } catch (IOException e) {
            //NOP
          }
        });
      } catch (IOException e) {
        // NOP
      }
    }
  }

  public void recordRemains() {
    if (Files.isDirectory(newDirPath)) {
      try (Stream<Path> stream = Files.list(newDirPath)) {
        ArrayList<String> remains = new ArrayList<>();
        stream.forEach(p -> {
          remains.add(p.getFileName().toString());
        });

        Files.createDirectories(remainsFilePath.getParent());
        Files.writeString(remainsFilePath, String.join(SECTION_SEPARATING_MARK, remains), StandardCharsets.UTF_8);
      } catch (IOException e) {
        //NOP
      }
    }
  }

  public boolean isFoundAllRemains() {
    if (Files.exists(remainsFilePath)) {
      try {
        for (String line : Files.readAllLines(remainsFilePath, StandardCharsets.UTF_8)) {
          if (!(newDirPath.resolve(line).toFile().exists() || curDirPath.resolve(line).toFile().exists())) {
            return false;
          }
        }
      } catch (IOException e) {
        //NOP
      }
    }
    return true;
  }
}
