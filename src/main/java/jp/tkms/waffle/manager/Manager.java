package jp.tkms.waffle.manager;

import jp.tkms.waffle.data.internal.guard.ProcedureRunGuard;
import jp.tkms.waffle.data.internal.guard.ProcedureRunGuardStore;
import jp.tkms.waffle.data.internal.guard.ValueGuard;
import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.project.workspace.HasLocalPath;
import jp.tkms.waffle.data.project.workspace.Workspace;
import jp.tkms.waffle.data.project.workspace.run.AbstractRun;
import jp.tkms.waffle.data.project.workspace.run.ConductorRun;
import jp.tkms.waffle.data.project.workspace.run.ExecutableRun;
import jp.tkms.waffle.data.project.workspace.run.ProcedureRun;
import jp.tkms.waffle.data.util.State;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

public class Manager {
  private Workspace workspace;
  private ExecutorService executorService;

  Manager(Workspace workspace) {
    this.workspace = workspace;
    this.executorService = Executors.newSingleThreadScheduledExecutor();
  }

  public void signalUpdated(ProcedureRunGuardStore store, AbstractRun run) {
    executorService.submit(() -> updateProcess(store, run));
  }

  public void signalFinished(ProcedureRunGuardStore store, AbstractRun run) {
    executorService.submit(() -> finishProcess(store, run));
  }

  private boolean isPassed(ValueGuard valueGuard, Object value) {
    return (new Filter(valueGuard)).apply(value);
  }

  private void deactivateAndTryRun(ProcedureRunGuardStore store, ProcedureRunGuard guard) {
    store.remove(guard);
    ProcedureRun procedureRun = guard.getProcedureRun();
    procedureRun.deactivateGuard(guard.getOriginalDescription());
    if (procedureRun.getActiveGuardList().size() <= 0) {
      procedureRun.run();
    }
  }

  private void updateProcess(ProcedureRunGuardStore store, AbstractRun run) {
    if (run.getWorkspace().isFinished()) {
      return;
    }

    store.getList(run).stream().filter(ProcedureRunGuard::isValueGuard)
        .forEach(guard -> {
          ProcedureRun procedureRun = guard.getProcedureRun();
          for (String guardString : procedureRun.getActiveGuardList()) {
            try {
              ValueGuard valueGuard = new ValueGuard(guardString);
              if (run instanceof ConductorRun) {
                ConductorRun conductorRun = (ConductorRun) run;
                if (isPassed(valueGuard, conductorRun.getVariable(valueGuard.getKey()))) {
                  deactivateAndTryRun(store, guard);
                  break;
                }
              } else if (run instanceof ExecutableRun) {
                ExecutableRun executableRun = (ExecutableRun) run;
                if (isPassed(valueGuard, executableRun.getResult(valueGuard.getKey()))) {
                  deactivateAndTryRun(store, guard);
                  break;
                }
              }
            } catch (Exception e) {
              ErrorLogMessage.issue(e);
            }
          }
        });
  }

  private void finishProcess(ProcedureRunGuardStore store, AbstractRun run) {
    if (run.getWorkspace().isFinished()) {
      return;
    }

    State state = run.getState();
    if (State.Finalizing.equals(state) || State.Finished.equals(state) || State.Canceled.equals(state)) {
      store.getList(run).stream().filter(Predicate.not(ProcedureRunGuard::isValueGuard))
        .forEach(guard -> deactivateAndTryRun(store, guard));

      ConductorRun parentConductorRun = run.getParentConductorRun();
      if (parentConductorRun != null) {
        parentConductorRun.unregisterChildRun((HasLocalPath) run);;
      }
    }
  }
}
