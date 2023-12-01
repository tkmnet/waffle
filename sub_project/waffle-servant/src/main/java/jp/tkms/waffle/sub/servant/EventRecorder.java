package jp.tkms.waffle.sub.servant;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class EventRecorder {
  // [WAFFLE_RESULT:<Result Name>:<Result Value>]
  private static final char[] EVENT_LABEL = "<WAFFLE_RESULT:".toCharArray();
  private static final byte STATE_WAITING = 0;
  private static final byte STATE_READING_NAME = 1;
  private static final byte STATE_READING_VALUE = 2;
  private static final char ESCAPING_MARK = '\\';
  private static final char END_MARK = '>';
  private static final char SECTION_SEPARATING_MARK = ':';

  private Path recordPath;
  private byte state;
  private StringBuilder nameBuilder;
  private StringBuilder valueBuilder;
  private int cursor;
  private boolean escape;

  public EventRecorder(Path baseDirectory, Path recordPath) {
    if (recordPath.isAbsolute()) {
      this.recordPath = recordPath;
    } else {
      this.recordPath = baseDirectory.resolve(recordPath);
    }
    this.state = 0;
    this.nameBuilder = new StringBuilder();
    this.valueBuilder = new StringBuilder();
    this.cursor = 0;
    this.escape = false;
  }

  private static Object writerLocker = new Object();
  private void write(String name, String value) {
    synchronized (writerLocker) {
      try {
        FileWriter writer = new FileWriter(recordPath.toFile(), StandardCharsets.UTF_8, true);
        writer.write(name + Constants.EVENT_VALUE_SEPARATOR + value + Constants.EVENT_SEPARATOR);
        writer.flush();
        writer.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public void input(char[] ch, int len) {
    if (state == STATE_WAITING) {
      if (isSameChars(ch, EVENT_LABEL[cursor])) {
        cursor += 1;
        if (EVENT_LABEL.length == cursor) {
          cursor = 0;
          state = STATE_READING_NAME;
        }
      } else if (isSameChars(ch, EVENT_LABEL[0])) {
        cursor = 1;
      } else {
        cursor = 0;
      }
    } else if (state == STATE_READING_NAME) {
      if (!escape && isSameChars(ch, SECTION_SEPARATING_MARK)) {
        state = STATE_READING_VALUE;
      } else if (!escape && isSameChars(ch, ESCAPING_MARK)) {
        escape = true;
      } else {
        escape = false;
        nameBuilder.append(ch, 0, len);
      }
    } else if (state == STATE_READING_VALUE) {
      if (!escape && isSameChars(ch, END_MARK)) {
        write(nameBuilder.toString(), valueBuilder.toString());
        nameBuilder.delete(0, nameBuilder.length());
        valueBuilder.delete(0, valueBuilder.length());
        state = STATE_WAITING;
      } else if (!escape && isSameChars(ch, ESCAPING_MARK)) {
        escape = true;
      } else {
        escape = false;
        valueBuilder.append(ch, 0, len);
      }
    }
  }

  private boolean isSameChars(char[] ch, char c) {
    return ch[0] == c && ch[1] == '\0';
  }

  public static void printEventString(String key, String value) {
    System.err.println(String.valueOf(EVENT_LABEL)
      + key.replaceAll("\\\\", "\\\\").replaceAll(String.valueOf(SECTION_SEPARATING_MARK), "\\" + SECTION_SEPARATING_MARK)
      + SECTION_SEPARATING_MARK
      + value.replaceAll("\\\\", "\\\\").replaceAll(String.valueOf(END_MARK), "\\" + END_MARK)
      + END_MARK);
  }

  public static void createEventRecord(String key, String value) {
    if (System.getenv().containsKey(Constants.WAFFLE_BATCH_WORKING_DIR)) {
      Path eventDirPath = Paths.get(System.getenv().get(Constants.WAFFLE_BATCH_WORKING_DIR)).resolve(Constants.EVENT_DIR);
      Path tmpPath = eventDirPath.resolve("tmp");
      Path newPath = eventDirPath.resolve("new");
      Path recordPath;
      do {
        recordPath = tmpPath.resolve(String.format("%015d", System.currentTimeMillis()));
      } while (Files.exists(recordPath));
      try {
        Files.createDirectories(tmpPath);
        Files.createDirectories(newPath);
        Files.writeString(recordPath, key + EventDirReader.SECTION_SEPARATING_MARK + value);
        Files.move(recordPath, newPath.resolve(recordPath.getFileName()));
      } catch (IOException e) {
        //NOP
      }
    }
  }
}
