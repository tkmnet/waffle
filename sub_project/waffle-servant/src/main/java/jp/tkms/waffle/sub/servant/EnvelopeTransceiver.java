package jp.tkms.waffle.sub.servant;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class EnvelopeTransceiver {
  static final byte B_DLE = 0x10;
  static final byte B_STX = 0x01;
  static final byte B_ETX = 0x03;
  static final byte B_EOT = 0x04;
  static final byte B_ACK = 0x05;
  static final byte B_BEL = 0x07;
  static final byte B_DC4 = 0x14;
  static final byte B_SYN = 0x16;
  static final byte B_ETB = 0x17;
  static final byte B_EM = 0x19;
  static final byte B_ESC = 0x1b;
  static final byte[] TAG_BEGIN = {B_DLE, B_STX};
  static final byte[] TAG_END = {B_DLE, B_ETX};
  static final byte[] TAG_EXECUTE = {B_DLE, B_EOT};
  static final byte[] TAG_EXECUTE_FILE = {B_DLE, B_ETB};
  static final byte[] TAG_WAIT = {B_DLE, B_DC4};
  static final byte[] TAG_BYE = {B_DLE, B_EM};
  static final byte[] TAG_ESCAPE_DLE = {B_DLE, B_ESC};
  static final byte[] TAG_REBOOT = {B_DLE, B_BEL};
  static final byte[] TAG_SYNC = {B_DLE, B_SYN};
  static final byte[] TAG_SYNC_RESPONSE = {B_DLE, B_ACK};
  private static final long TIMEOUT = 30000;
  private static final long MAX_STREAM_SIZE = 1024 * 1024; // 1MB

  Path baseDirectory;
  Path tmpDirectory;
  InputStreamProcessor inputStreamProcessor;
  BufferedOutputStream outputStream;
  BufferedInputStream inputStream;
  BiConsumer<Boolean, Path> fileTransmitter;
  boolean isAlive = true;

  public EnvelopeTransceiver(Path baseDirectory, boolean isRebootable, OutputStream outputStream, InputStream inputStream, BiConsumer<Boolean, Path> fileTransmitter, BiConsumer<EnvelopeTransceiver, Envelope> messageProcessor, Consumer<Long> updateNotifier) {
    this.baseDirectory = baseDirectory;
    this.tmpDirectory = baseDirectory.resolve(".INTERNAL").resolve("tmp");
    try {
      Files.createDirectories(tmpDirectory);
    } catch (IOException e) {
      e.printStackTrace();
    }

    this.outputStream = new BufferedOutputStream(outputStream);
    this.inputStream = new BufferedInputStream(inputStream);
    this.fileTransmitter = fileTransmitter;

    inputStreamProcessor = new InputStreamProcessor(this.baseDirectory, this.tmpDirectory, isRebootable, this.inputStream, this.fileTransmitter,
      s -> {
        try {
          messageProcessor.accept(this, Envelope.loadAndExtract(baseDirectory, s));
        } catch (Exception e) {
          e.printStackTrace();
        }
      }, updateNotifier,
      () -> { //syncResponder
        synchronized (outputStream) {
          try {
            outputStream.write(TAG_SYNC_RESPONSE);
            outputStream.write("\n\n".getBytes());
            outputStream.flush();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      },
      () -> {  //waitCommander
        synchronized (outputStream) {
          try {
            outputStream.write("\n\n".getBytes());
            outputStream.write(TAG_WAIT);
            outputStream.write("\n\n".getBytes());
            outputStream.flush();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      },
      (isReboot) -> { //finalizer
        try {
          shutdown(isReboot);
          inputStream.close();
          outputStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      });
    inputStreamProcessor.start();
  }

  public void flush() throws IOException {
    outputStream.flush();
  }

  public void shutdown(boolean isReboot) throws IOException {
    if (isAlive) {
      isAlive = false;
      if (isReboot) {
        String rebootkey = System.getenv(Main.KEY_REBOOTKEY);
        if (rebootkey != null) {
          try {
            Files.createFile(Paths.get(rebootkey));
          } catch (Exception e) {}
        }
      } else {
        outputStream.write(TAG_BYE);
      }
      try {
        flush();
        try {
          inputStreamProcessor.join(TIMEOUT);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      } catch (IOException e) {
        System.err.println("Stream is broken; it will be recovered");
      }
    }
  }

  public void send(Envelope envelope) throws Exception {
    if (envelope.getFileSize() < MAX_STREAM_SIZE) {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      envelope.save(stream);
      envelope.clear();
      synchronized (outputStream) {
        outputStream.write(TAG_BEGIN);
        sanitizeAndWrite(new ByteArrayInputStream(stream.toByteArray()), outputStream);
        outputStream.write(TAG_END);
        sanitizeAndWrite(new ByteArrayInputStream(toSHA1(stream.toByteArray())), outputStream);
        outputStream.write(TAG_EXECUTE);
        outputStream.write("\n\n".getBytes());
        outputStream.flush();
      }
    } else {
      synchronized (outputStream) {
        outputStream.write("\n\n".getBytes());
        outputStream.write(TAG_WAIT);
        outputStream.write("\n\n".getBytes());
      }
      System.err.println("Large size data will be created");
      Files.createDirectories(tmpDirectory);
      Path tmpFile = tmpDirectory.resolve("envelope-" + UUID.randomUUID());
      Files.createFile(tmpFile);
      tmpFile.toFile().deleteOnExit();
      envelope.save(tmpFile);
      envelope.clear();
      if (fileTransmitter != null) {
        fileTransmitter.accept(true, baseDirectory.relativize(tmpFile));
      }
      synchronized (outputStream) {
        outputStream.write(TAG_BEGIN);
        sanitizeAndWrite(new ByteArrayInputStream(tmpFile.getFileName().toString().getBytes()), outputStream);
        outputStream.write(TAG_END);
        sanitizeAndWrite(new ByteArrayInputStream(String.valueOf(Files.size(tmpFile)).getBytes()), outputStream);
        outputStream.write(TAG_EXECUTE_FILE);
        outputStream.write("\n\n".getBytes());
        outputStream.flush();
      }
      if (fileTransmitter != null) {
        Files.deleteIfExists(tmpFile);
      } else {
        tmpFile.toFile().deleteOnExit();
      }
    }
  }

  public void requestReboot() throws Exception {
    synchronized (outputStream) {
      outputStream.write(TAG_REBOOT);
      outputStream.write("\n\n".getBytes());
      outputStream.flush();
    }
  }

  public boolean sync(long timeout) throws Exception {
    long timeLimit = System.currentTimeMillis() + timeout;
    long requestedTime = System.currentTimeMillis();
    while (this.inputStreamProcessor.syncedTime.get() < requestedTime && isAlive) {
      if (timeLimit < System.currentTimeMillis()) return false;
      synchronized (outputStream) {
        outputStream.write(TAG_SYNC);
        outputStream.write("\n\n".getBytes());
        outputStream.flush();
      }
      TimeUnit.MILLISECONDS.sleep(50);
    }
    return true;
  }

  private void sanitizeAndWrite(InputStream in, OutputStream out) {
    byte[] buf = new byte[1];
    try {
      while (in.read(buf, 0, 1) != -1) {
        if (buf[0] == B_DLE) {
          out.write(TAG_ESCAPE_DLE);
        } else {
          out.write(buf);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void waitForShutdown() {
    while (isAlive) {
      try {
        inputStreamProcessor.join(TIMEOUT);
      } catch (InterruptedException e) {
        return;
      }
    }
  }

  private static class InputStreamProcessor extends Thread {
    public AtomicLong syncedTime = new AtomicLong(0);
    private Path baseDirectory;
    private Path tmpDirectory;
    private InputStream inputStream;
    private BiConsumer<Boolean, Path> fileTransmitter;
    private Consumer<InputStream> messageProcessor;
    private Consumer<Long> updateNotifier;
    Runnable syncResponder;
    Runnable waitCommander;
    private Consumer<Boolean> finalizer;
    private AtomicBoolean aliveFlag = new AtomicBoolean(true);
    private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private ByteArrayOutputStream secondBuffer = null;
    private boolean isRebootable;

    public InputStreamProcessor(Path baseDirectory, Path tmpDirectory, boolean isRebootable, InputStream inputStream, BiConsumer<Boolean, Path> fileTransmitter, Consumer<InputStream> messageProcessor, Consumer<Long> updateNotifier, Runnable syncResponder, Runnable waitCommander, Consumer<Boolean> finalizer) {
      this.baseDirectory = baseDirectory;
      this.tmpDirectory = tmpDirectory;
      this.isRebootable = isRebootable;
      this.inputStream = inputStream;
      this.fileTransmitter = fileTransmitter;
      this.messageProcessor = messageProcessor;
      this.updateNotifier = updateNotifier;
      this.syncResponder = syncResponder;
      this.waitCommander = waitCommander;
      this.finalizer = finalizer;
    }

    public void shutdown(boolean isReboot) {

      (new Thread(() -> {
        finalizer.accept(isReboot);
      })).start();
      aliveFlag.set(false);
    }

    @Override
    public void run() {
      boolean isTagMode = false;
      byte[] buf = new byte[1];
      try {
        while (aliveFlag.get() && inputStream.read(buf, 0, 1) != -1) {
          if (buf[0] == B_DLE) {
            isTagMode = true;
          } else if (isTagMode) {
            isTagMode = false;
            switch (buf[0]) {
              case B_STX:
                resetBuffer();
                break;
              case B_ETX:
                pushBuffer();
                break;
              case B_EOT:
                processMessage();
                break;
              case B_ETB:
                processMessageFile();
                break;
              case B_ESC:
                buffer.write(B_DLE);
                break;
              case B_BEL:
                if (isRebootable) {
                  shutdown(true);
                  return;
                }
              case B_EM:
                shutdown(false);
                return;
              case B_SYN:
                syncResponder.run();
                break;
              case B_ACK:
                syncedTime.set(System.currentTimeMillis());
                break;
              case B_DC4:
                updateNotifier.accept(System.currentTimeMillis() + 3600000);
                break;
              default:
                buffer.write(B_DLE);
                buffer.write(buf);
            }
          } else {
            buffer.write(buf);
          }
        }
      } catch (IOException e) {
        shutdown(false);
      }
    }

    private void resetBuffer() {
      buffer = new ByteArrayOutputStream();
      secondBuffer = null;
    }

    private void pushBuffer() {
      secondBuffer = buffer;
      buffer = new ByteArrayOutputStream();
    }

    private boolean checkConsistency() {
      if (buffer != null && secondBuffer != null) {
        return Arrays.equals(buffer.toByteArray(), toSHA1(secondBuffer.toByteArray()));
      }
      return false;
    }

    private void processMessage() {
      if (checkConsistency()) {
        messageProcessor.accept(new ByteArrayInputStream(secondBuffer.toByteArray()));
      } else {
        System.err.println("RECEIVED ENVELOPE IS BROKEN: " + buffer.size() + " bytes (" + secondBuffer.size() + " bytes)");
      }
    }

    private boolean checkFileConsistency() {
      if (buffer != null && secondBuffer != null) {
        Path path = tmpDirectory.resolve(new String(secondBuffer.toByteArray()));
        if (Files.exists(path)) {
          try {
            return Files.size(path) == Long.valueOf(new String(buffer.toByteArray()));
          } catch (Throwable e) {
            return false;
          }
        }
      }
      return false;
    }

    private void processMessageFile() {
      if (fileTransmitter != null && secondBuffer != null) {
        Path filePath = tmpDirectory.resolve(new String(secondBuffer.toByteArray()));
        waitCommander.run();
        fileTransmitter.accept(false, baseDirectory.relativize(filePath));
        filePath.toFile().deleteOnExit();
      }

      if (secondBuffer != null) {
        Path path = tmpDirectory.resolve(new String(secondBuffer.toByteArray()));
        path.toFile().deleteOnExit();
        if (checkFileConsistency()) {
          try {
            messageProcessor.accept(new BufferedInputStream(new FileInputStream(path.toFile())));
          } catch (FileNotFoundException e) {
            e.printStackTrace();
          }
        } else {
          System.err.println("RECEIVED ENVELOPE IS BROKEN: " + (new String(secondBuffer.toByteArray())) + " (" + (new String(buffer.toByteArray())) + " bytes)");
        }
      }

      try {
        Files.deleteIfExists(tmpDirectory.resolve(new String(secondBuffer.toByteArray())));
      } catch (Throwable e) {
        //NOP
      }
    }
  }

  public static byte[] toSHA1(byte[] bytes) {
    MessageDigest messageDigest = null;
    try {
      messageDigest = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    messageDigest.update(bytes);
    return messageDigest.digest();
  }
}
