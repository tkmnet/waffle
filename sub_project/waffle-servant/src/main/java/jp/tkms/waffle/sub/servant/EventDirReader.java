package jp.tkms.waffle.sub.servant;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class EventDirReader {
  public static final String SECTION_SEPARATING_MARK = "\n";

  private Path newDirtPath;
  private Path curDirtPath;
  public EventDirReader(Path baseDirectory, Path workingDirectory) {
    if (!workingDirectory.isAbsolute()) {
      workingDirectory = baseDirectory.resolve(workingDirectory);
    }
    this.newDirtPath = workingDirectory.resolve(Constants.EVENT_DIR).resolve("new");
    this.curDirtPath = workingDirectory.resolve(Constants.EVENT_DIR).resolve("cur");
  }

  public void process(BiConsumer<String, String> consumer) {
    if (Files.isDirectory(newDirtPath)) {
      try (Stream<Path> stream = Files.list(newDirtPath)) {
        Files.createDirectories(curDirtPath);
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
            Files.move(p, curDirtPath.resolve(p.getFileName()));
          } catch (IOException e) {
            //NOP
          }
        });
      } catch (IOException e) {
        // NOP
      }
    }
  }
}
