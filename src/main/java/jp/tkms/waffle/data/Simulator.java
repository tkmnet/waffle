package jp.tkms.waffle.data;

import jp.tkms.waffle.Constants;
import jp.tkms.waffle.collector.AbstractResultCollector;
import jp.tkms.waffle.collector.JsonResultCollector;
import jp.tkms.waffle.collector.RubyResultCollector;
import jp.tkms.waffle.data.util.ResourceFile;
import jp.tkms.waffle.extractor.RubyParameterExtractor;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

public class Simulator extends ProjectData {
  public static final String KEY_SIMULATOR = "simulator";
  public static final String KEY_EXTRACTOR = "extractor";
  public static final String KEY_COMMAND_ARGUMENTS = "command arguments";
  public static final String KEY_COLLECTOR = "collector";

  public static final String KEY_MASTER = "master";
  public static final String KEY_REMOTE = "REMOTE";

  protected static final String TABLE_NAME = "simulator";
  private static final String KEY_SIMULATION_COMMAND = "simulation_command";

  private String simulationCommand = null;

  public Simulator(Project project, UUID id, String name) {
    super(project, id, name);
  }

  public Simulator(Project project) {
    super(project);
  }

  @Override
  protected String getTableName() {
    return TABLE_NAME;
  }

  @Override
  protected Path getPropertyStorePath() {
    return getDirectory().resolve(KEY_SIMULATOR + Constants.EXT_JSON);
  }

  public static Simulator getInstance(Project project, String id) {
    final Simulator[] simulator = {null};

    handleDatabase(new Simulator(project), new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        PreparedStatement statement = db.preparedStatement("select id,name from " + TABLE_NAME + " where id=?;");
        statement.setString(1, id);
        ResultSet resultSet = statement.executeQuery();
        while (resultSet.next()) {
          simulator[0] = new Simulator(
            project,
            UUID.fromString(resultSet.getString("id")),
            resultSet.getString("name")
          );
        }
      }
    });

    return simulator[0];
  }

  public static Simulator getInstanceByName(Project project, String name) {
    final Simulator[] simulator = {null};

    handleDatabase(new Simulator(project), new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        PreparedStatement statement = db.preparedStatement("select id,name from " + TABLE_NAME + " where name=?;");
        statement.setString(1, name);
        ResultSet resultSet = statement.executeQuery();
        while (resultSet.next()) {
          simulator[0] = new Simulator(
            project,
            UUID.fromString(resultSet.getString("id")),
            resultSet.getString("name")
          );
        }
      }
    });

    return simulator[0];
  }

  public static Simulator find(Project project, String key) {
    if (key.matches("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}")) {
      return getInstance(project, key);
    }
    return getInstanceByName(project, key);
  }

  public static ArrayList<Simulator> getList(Project project) {
    ArrayList<Simulator> simulatorList = new ArrayList<>();

    handleDatabase(new Simulator(project), new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        ResultSet resultSet = db.executeQuery("select id,name from " + TABLE_NAME + ";");
        while (resultSet.next()) {
          simulatorList.add(new Simulator(
            project,
            UUID.fromString(resultSet.getString("id")),
            resultSet.getString("name"))
          );
        }
      }
    });

    return simulatorList;
  }

  public static Simulator create(Project project, String name, String simulationCommand) {
    Simulator simulator = new Simulator(project, UUID.randomUUID(), name);

    if (
      handleDatabase(new Simulator(project), new Handler() {
        @Override
        void handling(Database db) throws SQLException {
          PreparedStatement statement
            = db.preparedStatement("insert into " + TABLE_NAME + "(id,name,"
            + KEY_SIMULATION_COMMAND
            + ") values(?,?,?);");
          statement.setString(1, simulator.getId());
          statement.setString(2, simulator.getName());
          statement.setString(3, simulationCommand);
          statement.execute();
        }
      })
    ) {
      try {
        Files.createDirectories(simulator.getDirectory());
        Files.createDirectories(simulator.getBinDirectory());
      } catch (IOException e) {
        e.printStackTrace();
      }

      simulator.setSimulatorCommand(simulationCommand);
      simulator.createExtractor(KEY_COMMAND_ARGUMENTS);
      simulator.updateExtractorScript(KEY_COMMAND_ARGUMENTS, ResourceFile.getContents("default_parameter_extractor.rb"));

      ResultCollector.create(simulator, "_output.json", AbstractResultCollector.getInstance(JsonResultCollector.class.getCanonicalName()));

      try{
        Git git = Git.init().setDirectory(simulator.getDirectory().toFile()).call();
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Initial").setAuthor("waffle","waffle@tkms.jp").call();
        git.branchCreate().setName(KEY_REMOTE).call();
        git.checkout().setName(KEY_MASTER).call();
      } catch (GitAPIException e) {
        e.printStackTrace();
      }
    }

    return simulator;
  }

  public void update() {
    try{
      Git git = Git.open(getDirectory().toFile());
      git.add().addFilepattern(".").call();

      for (String missing : git.status().call().getMissing()) {
        git.rm().addFilepattern(missing).call();
      }

      if (!git.status().call().isClean()) {
        Set<String> changed = new HashSet<>();
        changed.addAll(git.status().addPath(KEY_REMOTE).call().getAdded());
        changed.addAll(git.status().addPath(KEY_REMOTE).call().getModified());
        changed.addAll(git.status().addPath(KEY_REMOTE).call().getRemoved());
        changed.addAll(git.status().addPath(KEY_REMOTE).call().getChanged());

        git.commit().setMessage((changed.isEmpty()?"":"R ") + LocalDateTime.now()).setAuthor("waffle","waffle@tkms.jp").call();

        if (!changed.isEmpty()) {
          git.checkout().setName(KEY_REMOTE).call();
          git.merge().include(git.getRepository().findRef(KEY_MASTER)).setMessage("Merge master").call();
          git.checkout().setName(KEY_MASTER).call();
        }
      }
    } catch (GitAPIException | IOException e) {
      e.printStackTrace();
    }
  }

  public Path getDirectory() {
    Path path = Paths.get(getProject().getLocation().toAbsolutePath() + File.separator +
      TABLE_NAME + File.separator + name + '_' + shortId
    );
    return path;
  }

  public Path getBinDirectory() {
    Path path = Paths.get(getProject().getLocation().toAbsolutePath() + File.separator +
      TABLE_NAME + File.separator + name + '_' + shortId + File.separator + KEY_REMOTE
    );
    return path;
  }

  public String getSimulationCommand() {
    if (simulationCommand == null) {
      simulationCommand = getStringFromProperty(KEY_SIMULATION_COMMAND);
    }
    return simulationCommand;
  }

  public void setSimulatorCommand(String command) {
    simulationCommand = command;
    setToProperty(KEY_SIMULATION_COMMAND, simulationCommand);
  }

  public Path getExtractorScriptPath(String name) {
    return getDirectory().resolve(KEY_EXTRACTOR).resolve(name).toAbsolutePath();
  }

  public void createExtractor(String name) {
    Path path = getCollectorScriptPath(name);
    Path dirPath = getCollectorScriptPath(name).getParent();
    if (! Files.exists(dirPath)) {
      try {
        Files.createDirectories(dirPath);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    try {
      FileWriter filewriter = new FileWriter(path.toFile());
      filewriter.write(new RubyParameterExtractor().contentsTemplate());
      filewriter.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void updateExtractorScript(String name, String script) {
    Path path = getExtractorScriptPath(name);
    if (Files.exists(path)) {
      try {
        FileWriter filewriter = new FileWriter(path.toFile());
        filewriter.write(script);
        filewriter.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public String getExtractorScript(String name) {
    String script = "";

    Path path = getExtractorScriptPath(name);
    if (Files.exists(path)) {
      try {
        script = new String(Files.readAllBytes(path));
      } catch (IOException e) {
      }
    }

    return script;
  }

  public Path getCollectorScriptPath(String name) {
    return getDirectory().resolve(KEY_COLLECTOR).resolve(name).toAbsolutePath();
  }

  public void createCollector(String name) {
    Path path = getCollectorScriptPath(name);
    Path dirPath = getCollectorScriptPath(name).getParent();
    if (! Files.exists(dirPath)) {
      try {
        Files.createDirectories(dirPath);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    try {
      FileWriter filewriter = new FileWriter(path.toFile());
      filewriter.write(new RubyResultCollector().contentsTemplate());
      filewriter.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void updateCollectorScript(String name, String script) {
    Path path = getCollectorScriptPath(name);
    if (Files.exists(path)) {
      try {
        FileWriter filewriter = new FileWriter(path.toFile());
        filewriter.write(script);
        filewriter.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public String getCollectorScript(String name) {
    String script = "";

    Path path = getCollectorScriptPath(name);
    if (Files.exists(path)) {
      try {
        script = new String(Files.readAllBytes(path));
      } catch (IOException e) {
      }
    }

    return script;
  }

  @Override
  protected Updater getDatabaseUpdater() {
    return new Updater() {
      @Override
      String tableName() {
        return TABLE_NAME;
      }

      @Override
      ArrayList<Updater.UpdateTask> updateTasks() {
        return new ArrayList<Updater.UpdateTask>(Arrays.asList(
          new UpdateTask() {
            @Override
            void task(Database db) throws SQLException {
              db.execute("create table " + TABLE_NAME + "(" +
                "id,name," + KEY_SIMULATION_COMMAND + "," +
                "timestamp_create timestamp default (DATETIME('now','localtime'))" +
                ");");
            }
          }
        ));
      }
    };
  }
}
