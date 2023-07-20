package jp.tkms.waffle.data.internal;

import jp.tkms.waffle.Constants;
import jp.tkms.waffle.Main;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.util.ResourceFile;
import jp.tkms.waffle.data.web.UserSession;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ServantScript {

  private static final String SERVANT_SH = "SERVANT.sh";
  public static final String JAR_FILE = "waffle-servant-all.jar";
  private static final String JAR_RESOURCE = "/" + JAR_FILE + ".bin";
  private static final Path JAR_PATH = InternalFiles.getPath(JAR_FILE);
  public static final String JRE_FILE = "waffle-servant-jre.tar.gz";
  private static final String JRE_RESOURCE = "/" + JRE_FILE + ".bin";
  private static final Path JRE_PATH = InternalFiles.getPath(JRE_FILE);
  private Computer computer;
  public ServantScript(Computer computer) {
    this.computer = computer;
  }

  public Path generate() {
    Path scriptPath = Constants.WORK_DIR.resolve(getScriptPath());
    try {
      Files.createDirectories(scriptPath.getParent());
    } catch (IOException e) {
      ErrorLogMessage.issue(e);
    }
    try (FileWriter writer = new FileWriter(scriptPath.toFile())) {
      writeln(writer, "#!/bin/sh\n");
      writeln(writer, "WAFFLE_SERVANT_SCRIPT_WORKDIR=\"$(pwd)\"");
      writeln(writer, "cd \"$(dirname $0)\"");
      writeln(writer, "WAFFLE_SERVANT_SCRIPT_DIR=\"$(pwd)\"");
      writeln(writer, "WAFFLE_SERVANT_JAR=\"$WAFFLE_SERVANT_SCRIPT_DIR/" + JAR_FILE + "\"");
      writeln(writer, "if [ ! -e \"waffle-servant-jre/bin/java\" ];then");
      writeln(writer, "if [ -e \"" + JRE_FILE + "\" ];then");
      writeln(writer, "tar xf " + JRE_FILE);
      writeln(writer, "else");
      writeln(writer, "echo JRE archive is not found");
      writeln(writer, "fi");
      writeln(writer, "fi");
      writeln(writer, "cd ../../../");
      writeln(writer, "WAFFLE_SERVANT_SCRIPT_BASEDIR=\"$(pwd)\"");
      writeln(writer, "cd \"$WAFFLE_SERVANT_SCRIPT_WORKDIR\"");
      writeln(writer, "WAFFLE_JAVA=\"$WAFFLE_SERVANT_SCRIPT_DIR/waffle-servant-jre/bin/java\"");
      writeln(writer, "WAFFLE_MINISERVANT=\"$WAFFLE_SERVANT_SCRIPT_DIR/waffle-servant-jre/bin/miniservant\"");
      //writeln(writer, computer.getJvmActivationCommand());
      writeln(writer, "if [ \"$1\" = \"exec\" ];then");
      writeln(writer, "MINIOUT=$(\"$WAFFLE_MINISERVANT\" \"$WAFFLE_SERVANT_SCRIPT_BASEDIR\" exec \"$2\" 2>&1)");
      writeln(writer, "EC=$?");
      writeln(writer, "if [ $EC = 1 -a $(echo \"$MINIOUT\" | grep GLIB | wc -l) -gt 0 ];then");
      writeln(writer, "EC=126");
      writeln(writer, "fi");
      writeln(writer, "if [ $EC = 126 -o $EC = 127 -o $EC = 139 -o $EC = 134 ];then");
      writeln(writer, "\"$WAFFLE_JAVA\" -client --illegal-access=deny --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED -jar \"$WAFFLE_SERVANT_JAR\" \"$WAFFLE_SERVANT_SCRIPT_BASEDIR\" exec \"$2\"");
      writeln(writer, "fi");
      writeln(writer, "elif [ \"$1\" = \"main\" ];then");
      writeln(writer, "\"$WAFFLE_JAVA\" --illegal-access=deny --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED -jar \"$WAFFLE_SERVANT_JAR\" \"$WAFFLE_SERVANT_SCRIPT_BASEDIR\" main \"$2\" $3");
      writeln(writer, "else");
      writeln(writer, "if [ -e \"$WAFFLE_SERVANT_JAR\" ];then");
      writeln(writer, "echo " + Main.VERSION);
      writeln(writer, "else");
      writeln(writer, "echo JAR is not found");
      writeln(writer, "fi");
      writeln(writer, "fi");
      writer.flush();
    } catch (IOException e) {
      ErrorLogMessage.issue(e);
    }
    return scriptPath;
  }

  public Path getScriptPath() {
    return getScriptPath(computer);
  }

  public Path getJrePath() {
    return getScriptPath(computer).getParent().resolve(JRE_FILE);
  }

  public Path getJarPath() {
    return getScriptPath(computer).getParent().resolve(JAR_FILE);
  }

  public static Path getScriptPath (Computer computer) {
    return InternalFiles.getLocalPath(Constants.COMPUTER).resolve(computer.getName() +'.' + UserSession.getWaffleId()).resolve(SERVANT_SH);
  }

  static void writeln(FileWriter writer, String line) throws IOException {
    writer.write(line);
    writer.write('\n');
  }

  public Path getJre() {
    if (!Files.exists(JRE_PATH)) {
      synchronized (this) {
        if (!Files.exists(JRE_PATH)) {
          ResourceFile.copyFile(JRE_RESOURCE, JRE_PATH);
        }
      }
    }
    JRE_PATH.toFile().deleteOnExit();
    return JRE_PATH;
  }

  public Path getJar() {
    if (!Files.exists(JAR_PATH)) {
      synchronized (this) {
        if (!Files.exists(JAR_PATH)) {
          ResourceFile.copyFile(JAR_RESOURCE, JAR_PATH);
        }
      }
    }
    JAR_PATH.toFile().deleteOnExit();
    return JAR_PATH;
  }
}
