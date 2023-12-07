package jp.tkms.waffle.sub.servant;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class DirectoryHash {
  public static final Path[] DEFAULT_TARGET = new Path[]{
    Paths.get(Constants.BASE),
    Paths.get(Constants.EXIT_STATUS_FILE),
    Paths.get(Constants.STDOUT_FILE),
    Paths.get(Constants.STDERR_FILE),
    Paths.get(Constants.EVENT_FILE),
    Paths.get(Constants.EVENT_DIR).resolve(Constants.REMAINS_FILE),
    Paths.get("task.json")
  };

  public static final String HASH_FILE = ".WAFFLE_HASH";
  public static final String IGNORE_FLAG = ".WAFFLE_HASH_IGNORE";
  private static final String SEPARATOR = ":";

  Path baseDirectory;
  Path directoryPath;
  byte[] hash = null;

  public DirectoryHash(Path baseDirectory, Path directoryPath, boolean isReady) {
    this.baseDirectory = baseDirectory;
    if (!directoryPath.isAbsolute()) {
      this.directoryPath = baseDirectory.resolve(directoryPath).normalize();
    } else {
      this.directoryPath = directoryPath.normalize();
    }

    if (isReady) {
      calculate();
    }
  }

  public DirectoryHash(Path baseDirectory, Path directoryPath) {
    this(baseDirectory, directoryPath, true);
  }

  public byte[] getHash() {
    if (hash == null) {
      calculate();
    }
    return hash;
  }

  public void calculate() {
    TreeSet<String> fileSet = new TreeSet<>();
    ArrayList<Path> targetList = new ArrayList<>();
    for (Path target : DEFAULT_TARGET) {
      targetList.add(directoryPath.resolve(target));
    }
    collectFilesStatusTo(fileSet, targetList.toArray(new Path[targetList.size()]));
    StringBuilder chainedStatus = new StringBuilder();
    for (String s : fileSet) {
      chainedStatus.append(s);
      chainedStatus.append(SEPARATOR);
    }

    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      hash = sha256.digest(chainedStatus.toString().getBytes());
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
  }

  void collectFilesStatusTo(TreeSet<String> fileSet, Path... targets) {
    for (Path p : targets) {
      if (Files.exists(p)) {
        if (Files.isSymbolicLink(p)) {
          // NOP
        } else if (Files.isDirectory(p)) {
          collectDirectoryStatusTo(fileSet, p);
        } else if (!p.toFile().getName().equals(HASH_FILE) && !p.toFile().getName().endsWith(IGNORE_FLAG)) {
          try {
            fileSet.add(p.getFileName().toString() + SEPARATOR + Files.size(p));
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }
  }

  void collectDirectoryStatusTo(TreeSet<String> fileSet, Path target) {
    boolean hasIgnoreFlag = false;
    try (Stream<Path> stream = Files.list(target)) {
      hasIgnoreFlag = !stream.noneMatch(p -> p.getFileName().toString().equals(IGNORE_FLAG));
    } catch (IOException e) {
      e.printStackTrace();
    }

    if (!hasIgnoreFlag) {
      try (Stream<Path> stream = Files.list(target)) {
        stream.forEach(p -> {
          collectFilesStatusTo(fileSet, p);
        });
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private Path getHashFilePath() {
    return directoryPath.resolve(HASH_FILE);
  }

  public boolean hasHashFile() {
    return Files.exists(getHashFilePath());
  }

  public boolean isMatchToHashFile() {
    return Arrays.equals(readHashFile(directoryPath), getHash());
  }

  public boolean waitToMatch(int timeout) {
    boolean isMatched = false;
    int count = 0;
    while (!(isMatched = isMatchToHashFile()) && count < timeout) {
      try {
        TimeUnit.SECONDS.sleep(1);
      } catch (InterruptedException e) {
        // NOP
      }
      calculate();
      if (timeout >= 0) {
        count += 1;
      }
    }
    return isMatched;
  }

  public void createEmptyHashFile() {
    if (!hasHashFile()) {
      try {
        Files.createFile(getHashFilePath());
        Runtime.getRuntime().exec("chmod 666 '" + getHashFilePath() + "'").waitFor();
      } catch (IOException | InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public void save() {
    try (FileOutputStream stream = new FileOutputStream(getHashFilePath().toFile(), false)) {
      stream.write(getHash());
      stream.flush();
      Runtime.getRuntime().exec("chmod 666 '" + getHashFilePath() + "'").waitFor();
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  public boolean update() {
    if (hasHashFile()) {
      calculate();
      if (!isMatchToHashFile()) {
        save();
        return true;
      }
    } else {
      save();
      return true;
    }
    return false;
  }

  public static byte[] readHashFile(Path directoryPath) {
    try {
      return Files.readAllBytes(directoryPath.resolve(HASH_FILE));
    } catch (IOException e) {
      return null;
    }
  }
}
