package jp.tkms.waffle.communicator.util;

import jp.tkms.waffle.Constants;
import jp.tkms.waffle.communicator.AbstractSubmitter;
import jp.tkms.waffle.communicator.process.RemoteProcess;
import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.log.message.InfoLogMessage;
import jp.tkms.waffle.data.log.message.WarnLogMessage;
import jp.tkms.waffle.sub.servant.Envelope;
import jp.tkms.waffle.sub.servant.EnvelopeTransceiver;
import jp.tkms.waffle.sub.servant.message.AbstractMessage;
import net.schmizz.sshj.connection.ConnectionException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class SelfCommunicativeEnvelope extends Envelope {
  RemoteProcess remoteProcess;
  EnvelopeTransceiver transceiver;
  Consumer<Envelope> additionalEnvelopeConsumer = null;

  HashMap<String, Byte> confirmPreparingMessageMap;

  Object messageLocker = new Object();
  Object fileLocker = new Object();

  AtomicInteger entryCounter = new AtomicInteger(0);
  Thread autoFlusher = new Thread() {
    @Override
    public void run() {
      while (!isInterrupted()) {
        try {
          sleep(1000);
          flush(true);
        } catch (InterruptedException e) {
          return;
        }
      }
    }
  };

  public SelfCommunicativeEnvelope(Path baseDirectory, RemoteProcess remoteProcess, AbstractSubmitter submitter) {
    super(baseDirectory);
    this.confirmPreparingMessageMap = new HashMap<>();
    this.remoteProcess = remoteProcess;
    this.transceiver = new EnvelopeTransceiver(baseDirectory, remoteProcess.getOutputStream(), remoteProcess.getInputStream(),
      (isUpload, path) ->{
        InfoLogMessage.issue("Transfer large size files (This process needs few minute)");
        try {
          if (isUpload) {
            submitter.transferFilesToRemote(Constants.WORK_DIR.resolve(path), submitter.getAbsolutePath(path));
          } else {
            submitter.transferFilesFromRemote(submitter.getAbsolutePath(path), Constants.WORK_DIR.resolve(path));
          }
        } catch (Exception e) {
          WarnLogMessage.issue(e);
        }
        InfoLogMessage.issue("Finished the transferring process");
      },
      ((transceiver, envelope) -> {
        submitter.processResponse(envelope);
        if (additionalEnvelopeConsumer != null) {
          additionalEnvelopeConsumer.accept(envelope);
        }
      }), (t) -> {});
    autoFlusher.start();
  }

  public void setAdditionalEnvelopeConsumer(Consumer<Envelope> additionalEnvelopeConsumer) {
    this.additionalEnvelopeConsumer = additionalEnvelopeConsumer;
  }

  public boolean isClosed() {
    return remoteProcess.isClosed();
  }

  public void close() {
    autoFlusher.interrupt();
    flush();
    try {
      transceiver.shutdown();
      transceiver.waitForShutdown();
    } catch (ConnectionException e) {
      InfoLogMessage.issue("Stream connection failed");
    } catch (IOException e) {
      ErrorLogMessage.issue(e);
    }
    remoteProcess.close();
  }

  public void flush() {
    flush(false);
  }

  protected void flush(boolean isAuto) {
    if (entryCounter.get() > 0) {
      send(isAuto);
    }
  }

  @Override
  public void add(Path path) {
    synchronized (fileLocker) {
      entryCounter.incrementAndGet();
      super.add(path);
    }
  }

  @Override
  public void add(AbstractMessage message) {
    synchronized (messageLocker) {
      entryCounter.incrementAndGet();
      super.add(message);
    }
  }

  private void send(boolean isAuto) {
    synchronized (messageLocker) {
      synchronized (fileLocker) {
        if (!remoteProcess.isClosed()) {
          try {
            transceiver.send(getRawEnvelope());
          } catch (Exception e) {
            entryCounter.set(0);
            close();
          }
          clear();
          entryCounter.set(0);
          if (!isAuto) {
            confirmPreparingMessageMap.clear();
          }
        }
      }
    }
  }
}
