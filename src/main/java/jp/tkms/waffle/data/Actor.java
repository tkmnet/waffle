package jp.tkms.waffle.data;

import jp.tkms.waffle.conductor.AbstractConductor;
import jp.tkms.waffle.conductor.EmptyConductor;
import jp.tkms.waffle.conductor.RubyConductor;
import jp.tkms.waffle.data.log.ErrorLogMessage;
import jp.tkms.waffle.data.log.WarnLogMessage;
import jp.tkms.waffle.data.util.HostState;
import jp.tkms.waffle.data.util.Sql;
import jp.tkms.waffle.data.util.State;
import org.jruby.Ruby;
import org.jruby.embed.EvalFailedException;
import org.jruby.embed.LocalContextScope;
import org.jruby.embed.PathType;
import org.jruby.embed.ScriptingContainer;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

public class Actor extends AbstractRun {
  protected static final String TABLE_NAME = "conductor_run";
  public static final String ROOT_NAME = "ROOT";
  public static final String KEY_ACTOR = "actor";

  private String actorName;

  public Actor(Project project, UUID id, String name, RunNode runNode, String actorName) {
    super(project, id, name, runNode);
    this.actorName = actorName;
  }

  public Actor(Actor actor) {
    super(actor.getProject(), actor.getUuid(), actor.getName(), actor.getRunNode());
    this.actorName = actor.actorName;
  }

  protected Actor(Project project) {
    super(project);
  }

  @Override
  protected String getTableName() {
    return TABLE_NAME;
  }

  public static Actor getInstance(Project project, String id) {
    final Actor[] conductorRun = {null};

    handleDatabase(new Actor(project), new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        ResultSet resultSet = new Sql.Select(db, TABLE_NAME, KEY_ID, KEY_NAME, KEY_RUNNODE, KEY_ACTOR).where(Sql.Value.equal(KEY_ID, id)).executeQuery();
        while (resultSet.next()) {
          conductorRun[0] = new Actor(
            project,
            UUID.fromString(resultSet.getString(KEY_ID)),
            resultSet.getString(KEY_NAME),
            RunNode.getInstance(project, resultSet.getString(KEY_RUNNODE)),
            resultSet.getString(KEY_ACTOR)
          );
        }
      }
    });

    return conductorRun[0];
  }

  public static Actor getInstanceByName(Project project, String name) {
    final Actor[] conductorRun = {null};

    handleDatabase(new Actor(project), new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        ResultSet resultSet = new Sql.Select(db, TABLE_NAME, KEY_ID, KEY_NAME, KEY_RUNNODE, KEY_ACTOR).where(Sql.Value.equal(KEY_NAME, name)).executeQuery();
        while (resultSet.next()) {
          RunNode runNode = RunNode.getInstance(project, resultSet.getString(KEY_RUNNODE));
          if (runNode == null) {
            new Sql.Delete(db, TABLE_NAME).where(Sql.Value.equal(KEY_ID, resultSet.getString(KEY_ID))).execute();
          } else {
            conductorRun[0] = new Actor(
              project,
              UUID.fromString(resultSet.getString(KEY_ID)),
              resultSet.getString(KEY_NAME),
              runNode,
              resultSet.getString(KEY_ACTOR)
            );
          }
        }
      }
    });

    return conductorRun[0];
  }

  public static Actor getRootInstance(Project project) {
    final Actor[] conductorRun = {null};

    handleDatabase(new Actor(project), new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        ResultSet resultSet = new Sql.Select(db, TABLE_NAME, KEY_ID, KEY_NAME, KEY_RUNNODE, KEY_ACTOR).where(Sql.Value.and(Sql.Value.equal(KEY_NAME, ROOT_NAME), Sql.Value.equal(KEY_PARENT, ""))).executeQuery();
        while (resultSet.next()) {
          conductorRun[0] = new Actor(
            project,
            UUID.fromString(resultSet.getString(KEY_ID)),
            resultSet.getString(KEY_NAME),
            RunNode.getRootInstance(project),
            resultSet.getString(KEY_ACTOR)
          );
        }
      }
    });

    return conductorRun[0];
  }

  public static Actor find(Project project, String key) {
    if (key.matches("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}")) {
      return getInstance(project, key);
    }
    return getInstanceByName(project, key);
  }

  public static ArrayList<Actor> getList(Project project, Actor parent) {
    ArrayList<Actor> list = new ArrayList<>();

    handleDatabase(new Actor(project), new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        ResultSet resultSet = new Sql.Select(db, TABLE_NAME, KEY_ID, KEY_NAME, KEY_RUNNODE, KEY_ACTOR)
          .where(Sql.Value.equal(KEY_PARENT, parent.getId()))
          .orderBy(KEY_TIMESTAMP_CREATE, true).orderBy(KEY_ROWID, true).executeQuery();
        while (resultSet.next()) {
          Actor conductorRun = new Actor(
            project,
            UUID.fromString(resultSet.getString(KEY_ID)),
            resultSet.getString(KEY_NAME),
            RunNode.getInstance(project, resultSet.getString(KEY_RUNNODE)),
            resultSet.getString(KEY_ACTOR)
          );
          list.add(conductorRun);
        }
      }
    });

    return list;
  }

  public static ArrayList<Actor> getList(Project project, ActorGroup actorGroup) {
    ArrayList<Actor> list = new ArrayList<>();

    handleDatabase(new Actor(project), new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        ResultSet resultSet = new Sql.Select(db, TABLE_NAME, KEY_ID, KEY_NAME, KEY_RUNNODE, KEY_ACTOR)
          .where(Sql.Value.equal(KEY_CONDUCTOR, actorGroup.getId()))
          .orderBy(KEY_TIMESTAMP_CREATE, true).orderBy(KEY_ROWID, true).executeQuery();
        while (resultSet.next()) {
          Actor conductorRun = new Actor(
            project,
            UUID.fromString(resultSet.getString(KEY_ID)),
            resultSet.getString(KEY_NAME),
            RunNode.getInstance(project, resultSet.getString(KEY_RUNNODE)),
            resultSet.getString(KEY_ACTOR)
          );
          list.add(conductorRun);
        }
      }
    });

    return list;
  }

  public static Actor getLastInstance(Project project, ActorGroup actorGroup) {
    final Actor[] conductorRun = {null};

    handleDatabase(new Actor(project), new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        ResultSet resultSet = new Sql.Select(db, TABLE_NAME, KEY_ID, KEY_NAME, KEY_RUNNODE, KEY_ACTOR)
          .where(Sql.Value.equal(KEY_CONDUCTOR, actorGroup.getId())).orderBy(KEY_TIMESTAMP_CREATE, true).limit(1).executeQuery();
        while (resultSet.next()) {
          conductorRun[0] = new Actor(
            project,
            UUID.fromString(resultSet.getString(KEY_ID)),
            resultSet.getString(KEY_NAME),
            RunNode.getInstance(project, resultSet.getString(KEY_RUNNODE)),
            resultSet.getString(KEY_ACTOR)
          );
        }
      }
    });

    return conductorRun[0];
  }

  public static ArrayList<Actor> getList(Project project, String parentId) {
    return getList(project, getInstance(project, parentId));
  }

  public static ArrayList<Actor> getNotFinishedList(Project project) {
    ArrayList<Actor> list = new ArrayList<>();

    handleDatabase(new Actor(project), new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        ResultSet resultSet = new Sql.Select(db, TABLE_NAME, KEY_ID, KEY_NAME, KEY_RUNNODE, KEY_ACTOR)
          .where(Sql.Value.lessThan(KEY_STATE, State.Finished.ordinal())).executeQuery();
        while (resultSet.next()) {
          list.add(new Actor(
            project,
            UUID.fromString(resultSet.getString(KEY_ID)),
            resultSet.getString(KEY_NAME),
            RunNode.getInstance(project, resultSet.getString(KEY_RUNNODE)),
            resultSet.getString(KEY_ACTOR)
          ));
        }
      }
    });

    return list;
  }

  public static Actor create(RunNode runNode, Actor parent, ActorGroup actorGroup, String actorName) {
    Project project = parent.getProject();
    String conductorId = (actorGroup == null ? "" : actorGroup.getId());
    String conductorName = (actorGroup == null ? "NON_CONDUCTOR" : actorGroup.getName());
    String name = conductorName + " : " + LocalDateTime.now().toString();
    Actor conductorRun = new Actor(project, UUID.randomUUID(), name, runNode, actorName);

    handleDatabase(new Actor(project), new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        new Sql.Insert(db, TABLE_NAME,
          Sql.Value.equal( KEY_ID, conductorRun.getId() ),
          Sql.Value.equal( KEY_NAME, conductorRun.getName() ),
          Sql.Value.equal( KEY_PARENT, parent.getId() ),
          Sql.Value.equal( KEY_CONDUCTOR, conductorId ),
          Sql.Value.equal( KEY_VARIABLES, parent.getVariables().toString() ),
          Sql.Value.equal( KEY_STATE, State.Created.ordinal() ),
          Sql.Value.equal( KEY_RUNNODE, runNode.getId()),
          Sql.Value.equal( KEY_ACTOR, actorName)
        ).execute();
      }
    });

    return conductorRun;
  }

  public static Actor create(RunNode runNode, Actor parent, ActorGroup actorGroup) {
    return create(runNode, parent, actorGroup, ActorGroup.KEY_REPRESENTATIVE_ACTOR_NAME);
  }

  public void finish() {
    if (getState().isRunning()) {
      for (String actorId : getFinalizers()) {
        Actor finalizer = Actor.getInstance(getProject(), actorId);
        if (finalizer != null) {
          finalizer.processMessage(this);
        } else {
          WarnLogMessage.issue("the actor(" + actorId + ") is not found");
        }
      }

      setState(State.Finished);
    }
    if (!isRoot()) {
      getParent().update(this);
    }
  }

  public ArrayList<Actor> getChildActorRunList() {
    return getList(getProject(), this);
  }

  public boolean hasRunningChildSimulationRun() {
    return SimulatorRun.getNumberOfRunning(getProject(), this) > 0;
  }

  public State getState() {
    return State.valueOf(getIntFromDB(KEY_STATE));
  }

  public void setState(State state) {
    setToDB(KEY_STATE, state.ordinal());
  }

  @Override
  public boolean isRunning() {
    if (hasRunningChildSimulationRun()) {
      return true;
    }

    for (Actor conductorRun : getChildActorRunList()) {
      if (conductorRun.isRunning()) {
        return true;
      }
    }

    return false;
  }

  public void start() {
    start(false);
  }

  public void start(boolean async) {
    isStarted = true;
    setState(State.Running);
    if (!isRoot()) {
      getParent().setState(State.Running);
    }
    //AbstractConductor abstractConductor = AbstractConductor.getInstance(this);
    //abstractConductor.start(this, async);

    Thread thread = new Thread() {
      @Override
      public void run() {
        super.run();
        processMessage(null); //?????

        if (! isRunning()) {
          finish();
        }
        return;
      }
    };
    thread.start();
    if (!async) {
      try {
        thread.join();
      } catch (InterruptedException e) {
        ErrorLogMessage.issue(e);
      }
    }
  }

  public void update(AbstractRun run) {
    if (!isRoot()) {
      //eventHandler(conductorRun, run);
      if (! isRunning()) {
        if (! run.getState().equals(State.Finished)) {
          setState(State.Failed);
        }
        finish();
      }

      //TODO: do refactor
      if (getConductor() != null) {
        int runningCount = 0;
        for (Actor notFinished : Actor.getNotFinishedList(getProject()) ) {
          if (notFinished.getConductor() != null && notFinished.getConductor().getId().equals(getConductor().getId())) {
            runningCount += 1;
          }
        }
        BrowserMessage.addMessage("updateConductorJobNum('" + getConductor().getId() + "'," + runningCount + ")");
      }
    }
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
              new Sql.Create(db, TABLE_NAME, KEY_ID, KEY_NAME, KEY_PARENT, KEY_CONDUCTOR,
                Sql.Create.withDefault(KEY_VARIABLES, "'{}'"),
                Sql.Create.withDefault(KEY_FINALIZER, "'[]'"),
                KEY_STATE,
                KEY_RUNNODE,
                KEY_ACTOR,
                Sql.Create.timestamp(KEY_TIMESTAMP_CREATE),
                KEY_PARENT_RUNNODE).execute();
              new Sql.Insert(db, TABLE_NAME,
                Sql.Value.equal(KEY_ID, UUID.randomUUID().toString()),
                Sql.Value.equal(KEY_NAME, ROOT_NAME),
                Sql.Value.equal(KEY_PARENT, ""),
                Sql.Value.equal(KEY_RUNNODE, RunNode.getRootInstance(getProject()).getId())// for compatibility
              ).execute();
            }
          }
        ));
      }
    };
  }

  private Path getActorScriptPath() {
    if (ActorGroup.KEY_REPRESENTATIVE_ACTOR_NAME.equals(actorName)) {
      return getConductor().getRepresentativeActorScriptPath();
    }
    return getConductor().getActorScriptPath(actorName);
  }

  public void processMessage(AbstractRun caller) {
    ScriptingContainer container = new ScriptingContainer(LocalContextScope.THREADSAFE);
    try {
      container.runScriptlet(RubyConductor.getInitScript());
      container.runScriptlet(RubyConductor.getConductorTemplateScript());
    } catch (EvalFailedException e) {
      ErrorLogMessage.issue(e);
    }
    try {
      container.runScriptlet(PathType.ABSOLUTE, getActorScriptPath().toAbsolutePath().toString());
      container.callMethod(Ruby.newInstance().getCurrentContext(), "exec_actor_script", this, caller);
    } catch (Exception e) {
      WarnLogMessage.issue(e);
      getRunNode().appendErrorNote(e.getMessage());
    }
    container.terminate();
  }

  private ConductorTemplate conductorTemplate = null;
  private ListenerTemplate listenerTemplate = null;
  private ArrayList<AbstractRun> transactionRunList = new ArrayList<>();

  public Actor createActor(String name) {
    ActorGroup actorGroup = ActorGroup.find(getProject(), name);
    if (actorGroup == null) {
      throw new RuntimeException("Conductor\"(" + name + "\") is not found");
    }

    if (getRunNode() instanceof SimulatorRunNode) {
      setRunNode(((SimulatorRunNode) getRunNode()).moveToVirtualNode());
    }

    Actor actor = Actor.create(getRunNode().createInclusiveRunNode(""), this, actorGroup);
    transactionRunList.add(actor);
    return actor;
  }

  public SimulatorRun createSimulatorRun(String name, String hostName) {
    Simulator simulator = Simulator.find(getProject(), name);
    if (simulator == null) {
      throw new RuntimeException("Simulator(\"" + name + "\") is not found");
    }
    Host host = Host.find(hostName);
    if (host == null) {
      throw new RuntimeException("Host(\"" + hostName + "\") is not found");
    }
    //host.update();
    if (! host.getState().equals(HostState.Viable)) {
      throw new RuntimeException("Host(\"" + hostName + "\") is not viable");
    }

    if (getRunNode() instanceof SimulatorRunNode) {
      setRunNode(((SimulatorRunNode) getRunNode()).moveToVirtualNode());
    }

    SimulatorRun createdRun = SimulatorRun.create(getRunNode().createSimulatorRunNode(""), this, simulator, host);
    transactionRunList.add(createdRun);
    return createdRun;
  }

  protected void commit() {
    //TODO: do refactor
    if (conductorTemplate != null) {
      String script = conductorTemplate.getMainScript();
      ScriptingContainer container = new ScriptingContainer(LocalContextScope.THREADSAFE);
      try {
        container.runScriptlet(RubyConductor.getInitScript());
        container.runScriptlet(RubyConductor.getListenerTemplateScript());
        container.runScriptlet(script);
        container.callMethod(Ruby.newInstance().getCurrentContext(), "exec_conductor_template_script", this, conductorTemplate);
      } catch (EvalFailedException e) {
        WarnLogMessage.issue(e);
      }
      container.terminate();
    } else if (listenerTemplate != null) {
      String script = listenerTemplate.getScript();
      ScriptingContainer container = new ScriptingContainer(LocalContextScope.THREADSAFE);
      try {
        container.runScriptlet(RubyConductor.getInitScript());
        container.runScriptlet(RubyConductor.getListenerTemplateScript());
        container.runScriptlet(script);
        container.callMethod(Ruby.newInstance().getCurrentContext(), "exec_listener_template_script", this, this);
      } catch (EvalFailedException e) {
        WarnLogMessage.issue(e);
      }
      container.terminate();
    }

    if (transactionRunList.size() > 1) {
      getRunNode().switchToParallel();
    }

    for (AbstractRun createdRun : transactionRunList) {
      if (! createdRun.isStarted()) {
        createdRun.start();
      }
    }

    transactionRunList.clear();;
  }
}
