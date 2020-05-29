package jp.tkms.waffle.data;

import jp.tkms.waffle.Constants;
import jp.tkms.waffle.data.util.Sql;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

public class Spool extends Data {
  private static final String TABLE_NAME = "host";
  private static final UUID LOCAL_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
  private static final String KEY_WORKBASE = "work_base_dir";
  private static final String KEY_XSUB = "xsub_dir";
  private static final String KEY_POLLING = "polling_interval";
  private static final String KEY_MAX_JOBS = "maximum_jobs";
  private static final String KEY_OS = "os";
  private static final String KEY_PARAMETERS = "parameters";

  private String hostName = null;
  private String workBaseDirectory = null;
  private String xsubDirectory = null;
  private String os = null;
  private Integer pollingInterval = null;
  private Integer maximumNumberOfJobs = null;
  private JSONObject parameters = null;

  public Spool(UUID id, String name) {
    super(id, name);
  }

  public Spool() { }

  @Override
  protected String getTableName() {
    return TABLE_NAME;
  }

  public static Spool getInstance(String id) {
    final Spool[] host = {null};

    handleDatabase(new Spool(), new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        PreparedStatement statement = db.preparedStatement("select id,name from " + TABLE_NAME + " where id=?;");
        statement.setString(1, id);
        ResultSet resultSet = statement.executeQuery();
        while (resultSet.next()) {
          host[0] = new Spool(
            UUID.fromString(resultSet.getString("id")),
            resultSet.getString("name")
          );
        }
      }
    });

    return host[0];
  }

  public static Spool getInstanceByName(String name) {
    final Spool[] host = {null};

    handleDatabase(new Spool(), new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        PreparedStatement statement = db.preparedStatement("select id,name from " + TABLE_NAME + " where name=?;");
        statement.setString(1, name);
        ResultSet resultSet = statement.executeQuery();
        while (resultSet.next()) {
          host[0] = new Spool(
            UUID.fromString(resultSet.getString("id")),
            resultSet.getString("name")
          );
        }
      }
    });

    return host[0];
  }

  public static ArrayList<Spool> getList() {
    ArrayList<Spool> list = new ArrayList<>();

    handleDatabase(new Spool(), new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        ResultSet resultSet = db.executeQuery("select id,name from " + TABLE_NAME + ";");
        while (resultSet.next()) {
          list.add(new Spool(
            UUID.fromString(resultSet.getString("id")),
            resultSet.getString("name"))
          );
        }
      }
    });

    return list;
  }

  public static Spool create(String name) {
    Spool host = new Spool(UUID.randomUUID(), name);
    handleDatabase(new Spool(), new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        new Sql.Insert(db, TABLE_NAME,
          Sql.Value.equal(KEY_ID, host.getId()),
          Sql.Value.equal(KEY_NAME, host.getName()),
          Sql.Value.equal(KEY_WORKBASE, "/tmp/waffle"),
          Sql.Value.equal(KEY_XSUB, ""),
          Sql.Value.equal(KEY_MAX_JOBS, 1),
          Sql.Value.equal(KEY_POLLING, 10)).execute();
      }
    });
    return host;
  }

  public Path getLocation() {
    Path path = Paths.get( TABLE_NAME + File.separator + name + '_' + shortId );
    return path;
  }

  public boolean isLocal() {
    return LOCAL_UUID.equals(id);
  }

  public String getWorkBaseDirectory() {
    if (workBaseDirectory == null) {
      workBaseDirectory = getStringFromDB(KEY_WORKBASE);
    }
    return workBaseDirectory;
  }

  public void setWorkBaseDirectory(String workBaseDirectory) {
    if (
      setToDB(KEY_WORKBASE, workBaseDirectory)
    ) {
      this.workBaseDirectory = workBaseDirectory;
    }
  }

  public String getXsubDirectory() {
    if (xsubDirectory == null) {
      xsubDirectory = getStringFromDB(KEY_XSUB);
    }
    return xsubDirectory;
  }

  public void setXsubDirectory(String xsubDirectory) {
    if (
      setToDB(KEY_XSUB, xsubDirectory)
    ) {
      this.xsubDirectory = xsubDirectory;
    }
  }

  public String getOs() {
    if (os == null) {
      os = getStringFromDB(KEY_OS);
    }
    return os;
  }

  public String getDirectorySeparetor() {
    String directorySeparetor = "/";
    if (getOs().equals("U")) {
      directorySeparetor = "/";
    }
    return directorySeparetor;
  }

  public Integer getPollingInterval() {
    if (pollingInterval == null) {
      pollingInterval = Integer.valueOf(getStringFromDB(KEY_POLLING));
    }
    return pollingInterval;
  }

  public void setPollingInterval(Integer pollingInterval) {
    if (
      setToDB(KEY_POLLING, pollingInterval)
    ) {
      this.pollingInterval = pollingInterval;
    }
  }

  public Integer getMaximumNumberOfJobs() {
    if (maximumNumberOfJobs == null) {
      maximumNumberOfJobs = Integer.valueOf(getStringFromDB(KEY_MAX_JOBS));
    }
    return maximumNumberOfJobs;
  }

  public void setParameters(JSONObject jsonObject) {
    handleDatabase(this, new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        new Sql.Update(db, getTableName(), Sql.Value.equal(KEY_PARAMETERS, jsonObject.toString())).where(Sql.Value.equal(KEY_ID, getId())).execute();
      }
    });
  }

  @Override
  protected Updater getDatabaseUpdater() {
    return new Updater() {
      @Override
      String tableName() {
        return TABLE_NAME;
      }

      @Override
      ArrayList<UpdateTask> updateTasks() {
        return new ArrayList<UpdateTask>(Arrays.asList(
          new UpdateTask() {
            @Override
            void task(Database db) throws SQLException {
              db.execute("create table " + TABLE_NAME + "(id,name," +
                KEY_WORKBASE + "," +
                KEY_XSUB + "," +
                KEY_MAX_JOBS + "," +
                KEY_POLLING + "," +
                KEY_PARAMETERS + " default '{}'," +
                "timestamp_create timestamp default (DATETIME('now','localtime'))" +
                ");");

              new Sql.Insert(db, TABLE_NAME,
                Sql.Value.equal(KEY_ID, LOCAL_UUID.toString()),
                Sql.Value.equal(KEY_NAME, "LOCAL"),
                Sql.Value.equal(KEY_WORKBASE, Constants.LOCAL_WORK_DIR),
                Sql.Value.equal(KEY_XSUB, Constants.LOCAL_XSUB_DIR),
                Sql.Value.equal(KEY_MAX_JOBS, 1),
                Sql.Value.equal(KEY_POLLING, 5)
              ).execute();
              try {
                Files.createDirectories(Paths.get(Constants.LOCAL_WORK_DIR));
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
          },
          new UpdateTask() {
            @Override
            void task(Database db) throws SQLException {
              db.execute("alter table " + TABLE_NAME + " add " + KEY_OS + " default 'U';");
            }
          }
        ));
      }
    };
  }
}
