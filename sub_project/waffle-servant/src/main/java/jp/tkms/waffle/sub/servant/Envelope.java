package jp.tkms.waffle.sub.servant;

import jp.tkms.waffle.sub.servant.message.AbstractMessage;
import jp.tkms.waffle.sub.servant.message.request.ConfirmPreparingMessage;
import jp.tkms.waffle.sub.servant.message.response.ExceptionMessage;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Envelope {
  private static final String MESSAGE_BUNDLE = MessageBundle.class.getSimpleName();
  private static final Path FILES = Paths.get("FILES");

  private Path baseDirectory;
  private MessageBundle messageBundle;
  private ArrayList<Path> filePathList;

  public Envelope(Path baseDirectory) {
    this.baseDirectory = baseDirectory.toAbsolutePath();
    messageBundle = new MessageBundle();
    filePathList = new ArrayList<>();
  }

  public boolean isEmpty() {
    return messageBundle.isEmpty() && filePathList.isEmpty();
  }

  public void add(AbstractMessage message) {
    synchronized (messageBundle) {
      messageBundle.add(message);
    }
  }

  public void add(Path path) {
    if (path.isAbsolute()) {
      path = baseDirectory.relativize(path).normalize();
    }
    synchronized (filePathList) {
      filePathList.add(path);
    }
  }

  public boolean exists(Path path) {
    if (path == null) {
      return false;
    }
    if (path.isAbsolute()) {
      path = baseDirectory.relativize(path).normalize();
    }
    synchronized (filePathList) {
      return filePathList.contains(path);
    }
  }

  public MessageBundle getMessageBundle() {
    return messageBundle;
  }

  public void save(Path dataPath) throws Exception {
    try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(dataPath.toFile()))) {
      save(outputStream);
    } catch (Exception e) {
      throw e;
    }
  }

  public void save(OutputStream outputStream) throws Exception {
    try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
      Set<Path> sourceSet = new LinkedHashSet<>();
      for (Path filePath : filePathList) {
        if (Files.exists(baseDirectory.resolve(filePath))) {
          addPathsToSet(sourceSet, filePath);
        }
      }

      zipOutputStream.putNextEntry(new ZipEntry(MESSAGE_BUNDLE));
      messageBundle.serialize(zipOutputStream);
      zipOutputStream.closeEntry();

      for (Path source : sourceSet) {
        Path destination = FILES.resolve(source);
        if (Files.isDirectory(baseDirectory.resolve(source))) {
          zipOutputStream.putNextEntry(createDirectoryZipEntry(destination));
        } else {
          try (InputStream in = new BufferedInputStream(new FileInputStream(baseDirectory.resolve(source).toFile()))){
            zipOutputStream.putNextEntry(new ZipEntry(pathToString(destination)));
            IOUtils.copy(in, zipOutputStream);
          }
        }
        zipOutputStream.closeEntry();
      }

      zipOutputStream.finish();

    } catch (Exception e) {
      throw e;
    }
  }

  public static Path getResponsePath(Path path) {
    return path.getParent().resolve(path.getFileName().toString() + Constants.RESPONSE_SUFFIX);
  }

  public static Envelope loadAndExtract(Path baseDirectory, Path dataPath) throws Exception {
    if (!dataPath.isAbsolute()) {
      dataPath = baseDirectory.resolve(dataPath);
    }

    try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(dataPath.toFile()))) {
      return loadAndExtract(baseDirectory, inputStream);
    }
  }

  public static Envelope loadAndExtract(Path baseDirectory, InputStream inputStream) throws Exception {
    Envelope envelope = new Envelope(baseDirectory);

    try (ZipInputStream zipInputStream = new ZipInputStream(inputStream, StandardCharsets.UTF_8)) {
      ZipEntry entry = null;
      ArrayList<BufferedOutputStream> streamList = new ArrayList<>();
      while ((entry = zipInputStream.getNextEntry()) != null) {
        if (entry.getName().equals(MESSAGE_BUNDLE)) {
          envelope.messageBundle = MessageBundle.load(zipInputStream);
        } else {
          Path entryPath = baseDirectory.resolve(FILES.relativize(Paths.get(entry.getName())));
          if (entryPath.toFile().exists()) {
            deleteFiles(entryPath);
          }
          envelope.filePathList.add(baseDirectory.relativize(entryPath));
          if (entry.isDirectory()) {
            Files.createDirectories(entryPath);
          } else {
            Files.createDirectories(entryPath.getParent());
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(entryPath.toFile()));
            streamList.add(out);
            IOUtils.copy(zipInputStream, out);

            if (streamList.size() > Constants.MAX_STREAM) {
              for (BufferedOutputStream s : streamList) {
                s.close();
              }
              streamList.clear();
            }
          }
        }
      }
      for (BufferedOutputStream stream : streamList) {
        stream.close();
      }
    }

    return envelope;
  }

  public void clear() {
    messageBundle.clear();
    filePathList.clear();
  }

  public Envelope getRawEnvelope() {
    Envelope envelope = new Envelope(baseDirectory);
    envelope.messageBundle = messageBundle;
    envelope.filePathList = filePathList;
    return envelope;
  }

  public long getFileSize() {
    Set<Path> sourceSet = new LinkedHashSet<>();
    for (Path filePath : filePathList) {
      if (Files.exists(baseDirectory.resolve(filePath))) {
        addPathsToSet(sourceSet, filePath);
      }
    }
    return sourceSet.stream().mapToLong(path -> getFileSize(path)).sum();
  }

  private static void deleteFiles(Path path) {
    if (Files.isDirectory(path)) {
      try (Stream<Path> files = Files.list(path)) {
        files.forEach(p -> deleteFiles(p));
      } catch (IOException e) {
        e.printStackTrace();
      }

      try {
        Files.delete(path);
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      try {
        Files.delete(path);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private void addPathsToSet(Set<Path> set, Path path) {
    Path absolutePath = baseDirectory.resolve(path);
    if (Files.isDirectory(baseDirectory.resolve(path))) {
      set.add(path.normalize());
      try (Stream<Path> files = Files.list(absolutePath)) {
        files.forEach(p -> addPathsToSet(set, baseDirectory.relativize(p)));
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      //TODO: handling symbolic link
      set.add(path.normalize());
    }
  }

  private long getFileSize(Path file) {
    Path path = baseDirectory.resolve(file);
    if (Files.isDirectory(path)) {
      try (Stream<Path> files = Files.list(path)) {
        return files.mapToLong(p -> getFileSize(p)).sum();
      } catch (IOException e) {
        return 0;
      }
    } else {
      try {
        return Files.size(path);
      } catch (IOException e) {
        return 0;
      }
    }
  }

  private static String pathToString(Path path) {
    // return path.toString(); // this method is not compatible to Windows
    StringBuilder stringBuilder = new StringBuilder();
    for (Path p : path.normalize()) {
      if (stringBuilder.length() > 0) {
        stringBuilder.append('/');
      }
      stringBuilder.append(p.toString());
    }
    return stringBuilder.toString();
  }

  private static ZipEntry createDirectoryZipEntry(Path path) {
    return new ZipEntry(pathToString(path) + '/');
  }
}
