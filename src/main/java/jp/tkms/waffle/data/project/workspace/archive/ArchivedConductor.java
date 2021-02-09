package jp.tkms.waffle.data.project.workspace.archive;

import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.project.conductor.Conductor;
import jp.tkms.waffle.data.project.workspace.HasWorkspace;
import jp.tkms.waffle.data.project.workspace.Workspace;
import jp.tkms.waffle.data.project.workspace.conductor.StagedConductor;
import jp.tkms.waffle.data.util.WaffleId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ArchivedConductor extends Conductor implements HasWorkspace, ArchivedEntity {
  private Workspace workspace;
  private WaffleId id;

  public ArchivedConductor(Workspace workspace, String name, WaffleId id) {
    super(workspace.getProject(), name);
    this.workspace = workspace;
    this.id = id;
    initialise();
  }

  public ArchivedConductor(Workspace workspace, String name) {
    this(workspace, name, new WaffleId());
  }

  public static ArchivedConductor create(StagedConductor stagedConductor) {
    ArchivedConductor archivedConductor = new ArchivedConductor(stagedConductor.getWorkspace(), stagedConductor.getName());
    try {
      stagedConductor.copyDirectory(archivedConductor.getDirectoryPath());
    } catch (IOException e) {
      ErrorLogMessage.issue(e);
      return null;
    }
    return archivedConductor;
  }

  public static ArchivedConductor getInstanceOrCreate(StagedConductor stagedConductor, ArchivedConductor comparison) {
    if (stagedConductor.hasNotDifference(comparison, Paths.get(StagedConductor.ARCHIVE_ID))) {
      return comparison;
    } else {
      return create(stagedConductor);
    }
  }

  public static ArchivedConductor getInstance(Workspace workspace, String name, WaffleId id) {
    if (name != null && !name.equals("") && Files.exists(getDirectoryPath(workspace, name, id))) {
      return new ArchivedConductor(workspace, name, id);
    }
    return null;
  }

  public static ArchivedConductor getInstance(Workspace workspace, String archiveName) {
    return getInstance(workspace, archiveName.replaceFirst("-.+?$", ""), WaffleId.valueOf(archiveName.replaceFirst("^.+-", "")));
  }

  public static Path getDirectoryPath(Workspace workspace, String name, WaffleId id) {
    return workspace.getDirectoryPath().resolve(Workspace.ARCHIVE).resolve(CONDUCTOR).resolve(ArchivedEntity.getArchiveName(name, id));
  }

  @Override
  public Workspace getWorkspace() {
    return workspace;
  }

  @Override
  public WaffleId getId() {
    return id;
  }

  @Override
  public Path getDirectoryPath() {
    return getDirectoryPath(workspace, getName(), id);
  }
}
