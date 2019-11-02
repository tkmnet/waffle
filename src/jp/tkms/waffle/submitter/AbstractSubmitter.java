package jp.tkms.waffle.submitter;

import jp.tkms.waffle.data.BrowserMessage;
import jp.tkms.waffle.data.Host;
import jp.tkms.waffle.data.Job;
import jp.tkms.waffle.data.Run;
import org.json.JSONObject;

abstract public class AbstractSubmitter {
  protected static final String RUN_DIR = "run";
  protected static final String INNER_WORK_DIR = "WORK";
  protected static final String BATCH_FILE = "batch.sh";

  abstract String getWorkDirectory(Run run);
  abstract void prepare(Run run);
  abstract String exec(Run run, String command);
  abstract public void close();

  public static AbstractSubmitter getInstance(Host host) {
    AbstractSubmitter submitter = null;
    if (host.isLocal()) {
      submitter = new LocalSubmitter();
    }
    return submitter;
  }

  public void submit(Job job) {
    Run run = job.getRun();
    prepare(run);
    processXsubSubmit(job, exec(run, xsubSubmitCommand(job)));
  }

  public Run.State update(Job job) {
    Run run = job.getRun();
    processXstat(job, exec(run, xstatCommand(job)));
    return run.getState();
  }

  String makeBatchFileText(Run run) {
    return "#!/bin/sh\n" +
      "\n" +
      "mkdir " + INNER_WORK_DIR + "\n" +
      "cd " + INNER_WORK_DIR + "\n" +
      run.getSimulator().getSimulationCommand() + "\n";
  }

  String getTemplate() {
    String template = ". <%= _job_file %>";



    return template;
  }

  String xsubSubmitCommand(Job job) {
    //return xsubCommand(job) + " -d '" + getWorkDirectory(job.getRun()) + "' " + BATCH_FILE;
    return xsubCommand(job) + " " + BATCH_FILE;
  }

  String xsubCommand(Job job) {
    Host host = job.getHost();
    return "EP=`pwd`; if test ! $XSUB_TYPE; then XSUB_TYPE=None; fi; cd '"
      + getWorkDirectory(job.getRun())
      + "'; XSUB_TYPE=$XSUB_TYPE $EP" + host.getDirectorySeparetor()
      + host.getXsubDirectory() + host.getDirectorySeparetor() + "bin" + host.getDirectorySeparetor() + "xsub ";
  }

  String xstatCommand(Job job) {
    Host host = job.getHost();
    return "if test ! $XSUB_TYPE; then XSUB_TYPE=None; fi; XSUB_TYPE=$XSUB_TYPE "
      + host.getXsubDirectory() + host.getDirectorySeparetor()
      + "bin" + host.getDirectorySeparetor() + "xstat " + job.getJobId();
  }

  void processXsubSubmit(Job job, String json) {
    JSONObject object = new JSONObject(json);
    try {
      String jobId = object.getString("job_id");
      job.setJobId(jobId);
      job.getRun().setState(Run.State.Submitted);
    } catch (Exception e) {}
  }

  void processXstat(Job job, String json) {
    JSONObject object = new JSONObject(json);
    try {
      String status = object.getString("status");
      switch (status) {
        case "running" :
          job.getRun().setState(Run.State.Running);
          break;
        case "finished" :
          if (true) {
            job.getRun().setState(Run.State.Finished);
            job.remove();
            BrowserMessage.addMessage("runFinished('" + job.getRun().getId() + "')");
          } else {

          }
          break;
      }
    } catch (Exception e) {}
  }

  void processXdel(Job job, String json) {}

  @Override
  public String toString() {
    return super.toString();
  }

}