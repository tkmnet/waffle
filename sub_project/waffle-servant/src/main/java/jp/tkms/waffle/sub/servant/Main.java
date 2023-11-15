package jp.tkms.waffle.sub.servant;

import jp.tkms.waffle.sub.servant.message.response.ExceptionMessage;
import jp.tkms.waffle.sub.servant.message.response.StorageWarningMessage;
import jp.tkms.waffle.sub.servant.processor.RequestProcessor;
import org.jruby.RubyProcess;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Main {
  public  static AtomicLong lastUpdatedTime = new AtomicLong(System.currentTimeMillis());
  public static String KEY_REBOOTKEY = "WAFFLE_REBOOTKEY";
  public static boolean isRebootable = System.getenv(KEY_REBOOTKEY) != null;

  public static void updateTimestamp(long time) {
    lastUpdatedTime.set(time);
  }

  public static void updateTimestamp() {
    updateTimestamp(System.currentTimeMillis());
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      exitWithInvalidArgumentsMessage("", "");
    }

    Path baseDirectory = Paths.get(args[0]);

    switch (args[1].toLowerCase(Locale.ROOT)) {
      case "main":
        setupBinDirectory(baseDirectory);
        if (args.length < 3) {
          exitWithInvalidArgumentsMessage("main", "[MESSAGE PATH]");
        }
        boolean isStdoutInMode = args[2].equals("-");
        if (isStdoutInMode) {
          if (args.length < 4) {
            exitWithInvalidArgumentsMessage("main", "- [TIMEOUT]");
          }
          int timeout = -1;
          try {
            timeout = Integer.parseInt(args[3]);
          } catch (NumberFormatException e) {
            exitWithInvalidArgumentsMessage("main", "- [TIMEOUT]");
          }

          timeout = 3600;
          int timeoutByMillis = timeout * 1000;
          Thread timeoutThread = new Thread(()->{
            while (true) {
              if (Main.lastUpdatedTime.get() < System.currentTimeMillis() - timeoutByMillis) {
                System.exit(3);
              }
              try {
                TimeUnit.SECONDS.sleep(1);
              } catch (InterruptedException e) {
                return;
              }
            }
          });
          updateTimestamp();
          timeoutThread.start();

          EnvelopeTransceiver transceiver = new EnvelopeTransceiver(baseDirectory, isRebootable, System.out, System.in, null,
            (me, request) -> {
              try {
                Main.updateTimestamp();
                Envelope response = new Envelope(baseDirectory);
                StorageWarningMessage.addMessageIfCritical(response);
                RequestProcessor.processMessages(baseDirectory, request, response);
                me.send(response);
              } catch (Exception e) {
                e.printStackTrace();
              }
            },
            (t) -> {
              Main.updateTimestamp(t);
            });
          transceiver.waitForShutdown();

          timeoutThread.interrupt();
          timeoutThread.join();
        } else {
          Path envelopePath = Paths.get(args[2]);
          if (Files.exists(Envelope.getResponsePath(envelopePath))) {
            System.err.println("Already exists a response for " + envelopePath.getFileName());
            break;
          }
          Envelope request = Envelope.loadAndExtract(baseDirectory, envelopePath);
        /*
        if ("1".equals(System.getenv("DEBUG"))) {
          request.getMessageBundle().print("SERVANT:");
        }
         */
          //request.getMessageBundle().print("SERVANT-IN:");
          Envelope response = new Envelope(baseDirectory);
          StorageWarningMessage.addMessageIfCritical(response);
          RequestProcessor.processMessages(baseDirectory, request, response);
          response.save(Envelope.getResponsePath(envelopePath));
          //response.getMessageBundle().print("SERVANT-OUT:");
          Files.delete(envelopePath);
        }
        break;
      case "exec":
        if (args.length < 3) {
          exitWithInvalidArgumentsMessage("exec", "[TASK JSON PATH]");
        }
        TaskExecutor executor = new TaskExecutor(baseDirectory, Paths.get(args[2]));
        Runtime.getRuntime().addShutdownHook(new Thread() {
          @Override
          public void run() {
            executor.shutdown();
          }
        });
        executor.execute();
        break;
      case "terminal":
        if (args.length < 3) {
          exitWithInvalidArgumentsMessage("terminal", "[TERMINAL ID]");
        }
        PseudoTerminal pseudoTerminal = new PseudoTerminal(baseDirectory, args[2]);
        Runtime.getRuntime().addShutdownHook(new Thread() {
          @Override
          public void run() {
            pseudoTerminal.shutdown();
          }
        });
        pseudoTerminal.run();
        break;
      case "put_result":
        if (args.length < 4) {
          exitWithInvalidArgumentsMessage("put_result", "[KEY] [VALUE]");
        }
        EventRecorder.createEventRecord(args[2], args[3]);
        break;
      case "push_file":
        if (args.length < 4) {
          exitWithInvalidArgumentsMessage("push_file", "[TASK JSON PATH] [FILE NAME]");
        }
        (new PushFileCommand(baseDirectory, Paths.get(args[2]), Paths.get(args[3]))).run();
        break;
      case "get_value":
        boolean isSuccess = false;
        if (args.length == 4) {
          isSuccess = (new GetValueCommand(baseDirectory, Paths.get(args[2]), args[3])).run();
        } else if (args.length == 6) {
          isSuccess = (new GetValueCommand(baseDirectory, Paths.get(args[2]), args[3], args[4], args[5])).run();
        } else if (args.length >= 7) {
          isSuccess = (new GetValueCommand(baseDirectory, Paths.get(args[2]), args[3], args[4], args[5], Integer.parseInt(args[6]))).run();
        } else {
          exitWithInvalidArgumentsMessage("get_value", "[TASK JSON PATH] [KEY] [FILTER OP] [FILTER VAL] [TIMEOUT]");
        }
        if (!isSuccess) {
          System.exit(1);
        }
        break;
      case "sync_hash":
        DirectoryHash directoryHash = new DirectoryHash(baseDirectory, Paths.get(".").toAbsolutePath());
        directoryHash.save();
        break;
      case "bench":
        EasyBench easyBench = new EasyBench();
        easyBench.print();
        break;
      default:
        exitWithInvalidArgumentsMessage("", "");
    }

    return;
  }

  private static void exitWithInvalidArgumentsMessage(String mode, String additionalOption) {
    System.err.println("usage: java -jar <JAVA JAR> [BASE DIRECTORY] "
      + (mode == null || mode.equals("") ? "[MODE{main,exec,terminal,sync_hash,bench}]" : mode) + " " + additionalOption);
    System.exit(1);
  }

  private static void setupBinDirectory(Path baseDirectory) {
    Path binDirectory = getBinDirectory(baseDirectory);
    if (!Files.exists(binDirectory)) {
      try {
        createWaffleCommandFile(binDirectory.resolve("waffle-push"), baseDirectory, "push_file ${" + Constants.WAFFLE_TASK_JSONFILE + "} \"$@\"");
        createWaffleCommandFile(binDirectory.resolve("waffle-pull"), baseDirectory, "pull_file  ${" + Constants.WAFFLE_TASK_JSONFILE + "} \"$@\"");
        createWaffleCommandFile(binDirectory.resolve("waffle-put"), baseDirectory, "put_result \"$@\"");
        createWaffleCommandFile(binDirectory.resolve("waffle-get"), baseDirectory, "get_value ${" + Constants.WAFFLE_TASK_JSONFILE + "} \"$@\"");
      } catch (IOException | URISyntaxException e) {
        e.printStackTrace();
      }
    }
  }

  private static void createWaffleCommandFile(Path path, Path baseDirectory, String options) throws IOException, URISyntaxException {
    final String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    final File currentJar = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    if(!currentJar.getName().endsWith(".jar")) { return; }

    writeExecutableFile(path, "#!/bin/sh\n\"" + javaBin + "\"" +
      " --illegal-access=deny --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED" +
      " -jar \"" + currentJar.toPath().toAbsolutePath() + "\" \"" + baseDirectory.toAbsolutePath() + "\" " + options + " 2>/dev/null\n");
  }

  private static void writeExecutableFile(Path path, String contents) throws IOException {
    Files.createDirectories(path.getParent());
    Files.writeString(path, contents);
    path.toFile().setExecutable(true, false);
  }

  public static Path getBinDirectory(Path baseDirectory) {
    return baseDirectory.resolve("bin");
  }
}
