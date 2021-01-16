package jp.tkms.waffle.data.job;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import jp.tkms.waffle.Constants;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.log.message.InfoLogMessage;
import jp.tkms.waffle.data.util.WaffleId;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class JobStore {
  private LinkedHashMap<WaffleId, Job> jobMap;
  private LinkedHashMap<String, ArrayList<Job>> computerJobListMap;

  public JobStore() {
    jobMap = new LinkedHashMap<>();
    computerJobListMap = new LinkedHashMap<>();
  }

  public Job getJob(UUID id) {
    synchronized (jobMap) {
      return jobMap.get(id.toString());
    }
  }

  public ArrayList<Job> getList() {
    synchronized (jobMap) {
      return new ArrayList<>(jobMap.values());
    }
  }

  public ArrayList<Job> getList(Computer computer) {
    synchronized (jobMap) {
      ArrayList list = computerJobListMap.get(computer.getName());
      if (list == null) {
        list = new ArrayList();
        computerJobListMap.put(computer.getName(), list);
      }
      return list;
    }
  }

  public boolean contains(UUID id) {
    synchronized (jobMap) {
      return jobMap.containsKey(id);
    }
  }

  public void register(Job job) {
    synchronized (jobMap) {
      jobMap.put(job.getId(), job);
      getList(job.getComputer()).add(job);
    }
  }

  public void remove(WaffleId id) {
    if (id == null) {
      return;
    }
    synchronized (jobMap) {
      Job removedJob = jobMap.remove(id);
      if (removedJob != null) {
        getList(removedJob.getComputer()).remove(removedJob);
      }
    }
  }

  public void save() throws IOException {
    InfoLogMessage.issue("Waiting for the job store to release");
    synchronized (jobMap) {
      GZIPOutputStream outputStream = new GZIPOutputStream(new FileOutputStream(getFilePath().toFile()));
      Kryo kryo = new Kryo();
      Output output = new Output(outputStream);
      kryo.writeObject(output, this);
      output.flush();
      output.close();
      InfoLogMessage.issue("The snapshot of job store saved");
    }
  }

  public static JobStore load() {
    JobStore data = null;
    try {
      GZIPInputStream inputStream = new GZIPInputStream(new FileInputStream(getFilePath().toFile()));
      Kryo kryo = new Kryo();
      Input input = new Input(inputStream);
      data = kryo.readObject(input, JobStore.class);
      input.close();
    } catch (Exception e) {
      data = new JobStore();
    }
    return data;
  }

  public static Path getFilePath() {
    return Constants.WORK_DIR.resolve(".jobstore.dat");
  }
}
