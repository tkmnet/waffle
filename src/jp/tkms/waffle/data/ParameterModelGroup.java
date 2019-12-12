package jp.tkms.waffle.data;

import jp.tkms.util.Values;
import jp.tkms.waffle.collector.AbstractResultCollector;
import jp.tkms.waffle.data.util.Sql;
import org.json.JSONObject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

public class ParameterModelGroup extends SimulatorData {
  protected static final String TABLE_NAME = "parameter_model_group";
  private static final UUID ROOT_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  public static final String ROOT_NAME = "ROOT";
  private static final String KEY_PARENT = "parent";
  private static final String KEY_INSTANCE_SIZE = "instance_size";

  private ParameterModelGroup parent = null;
  private String parentId = null;
  private Integer instanceSize = null;

  public ParameterModelGroup(Simulator simulator) {
    super(simulator);
  }

  public ParameterModelGroup(Simulator simulator, String parentId, UUID id, String name) {
    super(simulator, id, name);
    this.parentId = parentId;
  }

  @Override
  protected String getTableName() {
    return TABLE_NAME;
  }

  public static ParameterModelGroup getInstance(Simulator simulator, String id) {
    final ParameterModelGroup[] extractor = {null};

    handleDatabase(new ParameterModelGroup(simulator), new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        PreparedStatement statement = new Sql.Select(db, TABLE_NAME, KEY_ID, KEY_NAME, KEY_PARENT).where(Sql.Value.equalP(KEY_ID)).toPreparedStatement();
        statement.setString(1, id);
        ResultSet resultSet = statement.executeQuery();
        while (resultSet.next()) {
          extractor[0] = new ParameterModelGroup(
            simulator,
            resultSet.getString(KEY_PARENT),
            UUID.fromString(resultSet.getString(KEY_ID)),
            resultSet.getString(KEY_NAME)
          );
        }
      }
    });

    return extractor[0];
  }

  public static ParameterModelGroup getRootInstance(Simulator simulator) {
    return getInstance(simulator, ROOT_UUID.toString());
  }

  public static ArrayList<ParameterModelGroup> getList(ParameterModelGroup parent) {
    ArrayList<ParameterModelGroup> collectorList = new ArrayList<>();

    handleDatabase(new ParameterModelGroup(parent.getSimulator()), new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        PreparedStatement statement = new Sql.Select(db, TABLE_NAME, KEY_ID, KEY_NAME, KEY_PARENT).where(Sql.Value.equalP(KEY_PARENT)).toPreparedStatement();
        statement.setString(1, parent.getId());
        ResultSet resultSet = statement.executeQuery();
        while (resultSet.next()) {
          collectorList.add(new ParameterModelGroup(
            parent.getSimulator(),
            resultSet.getString(KEY_PARENT),
            UUID.fromString(resultSet.getString(KEY_ID)),
            resultSet.getString(KEY_NAME))
          );
        }
      }
    });

    return collectorList;
  }

  public static ParameterModelGroup create(Simulator simulator, ParameterModelGroup parent, String name) {
    ParameterModelGroup group = new ParameterModelGroup(simulator, parent.getId(), UUID.randomUUID(), name);

    handleDatabase(new ParameterModelGroup(simulator), new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        PreparedStatement statement = new Sql.Insert(db, TABLE_NAME,
          KEY_ID, KEY_NAME, KEY_PARENT).toPreparedStatement();
        statement.setString(1, group.getId());
        statement.setString(2, group.getName());
        statement.setString(3, group.getParent().getId());
        statement.execute();
      }
    });

    return group;
  }

  public boolean isRoot() {
    return getId().equals(ROOT_UUID.toString());
  }

  public ParameterModelGroup getParent() {
    if (parent == null) {
      parent = getInstance(getSimulator(), parentId);
    }
    return parent;
  }

  public Integer getInstanceSize() {
    if (instanceSize == null) {
      instanceSize = Integer.valueOf(getFromDB(KEY_INSTANCE_SIZE));
    }
    return instanceSize;
  }

  public JSONObject toJSONObject() {
    JSONObject jsonObject = new JSONObject();
    for (ParameterModel parameter : ParameterModel.getList(this)) {
      jsonObject.put(parameter.getName(), Values.convertString(parameter.getDefaultValue()));
    }
    for (ParameterModelGroup group : ParameterModelGroup.getList(this)) {
      jsonObject.put(group.getName(), group.toJSONObject().toMap());
    }
    return jsonObject;
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
              new Sql.Create(db, TABLE_NAME,
                KEY_ID, KEY_NAME, Sql.Create.withDefault(KEY_PARENT, "''"),
                Sql.Create.withDefault(KEY_INSTANCE_SIZE, "1"),
                Sql.Create.timestamp("timestamp_create")
              ).execute();
            }
          },
          new UpdateTask() {
            @Override
            void task(Database db) throws SQLException {
              PreparedStatement statement = new Sql.Insert(db, TABLE_NAME, KEY_ID, KEY_NAME).toPreparedStatement();
              statement.setString(1, ROOT_UUID.toString());
              statement.setString(2, ROOT_NAME);
              statement.execute();
            }
          }
        ));
      }
    };
  }
}
