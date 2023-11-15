package jp.tkms.waffle.sub.servant;

import jp.tkms.waffle.sub.servant.message.response.ExceptionMessage;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnvelopeTransceiverTest {
  @Test
  void success() {
    String testString = "asdfgh<WAFFLE_ESCAPE|END>jkl;";

    Path tmpPath = Paths.get("/tmp");
    PipedOutputStream outputStream1 = new PipedOutputStream();
    PipedOutputStream outputStream2 = new PipedOutputStream();
    PipedInputStream inputStream1 = null;
    try {
      inputStream1 = new PipedInputStream(outputStream2);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    AtomicReference<Envelope> envelope2 = new AtomicReference<>();
    EnvelopeTransceiver transceiver1 = new EnvelopeTransceiver(tmpPath, false, outputStream1, inputStream1, (u, p) -> {}, (transceiver, envelope) -> {}, (t) -> {});
    Envelope envelope1 = new Envelope(tmpPath);
    envelope1.add(new ExceptionMessage(testString));
    PipedInputStream inputStream2 = null;
    try {
      inputStream2 = new PipedInputStream(outputStream1);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    try {
      transceiver1.send(envelope1);
      transceiver1.flush();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    EnvelopeTransceiver transceiver2 = new EnvelopeTransceiver(tmpPath, false, outputStream2, inputStream2, (u, p) ->{}, (transceiver, envelope) -> {
      envelope2.set(envelope);
    }, (t) -> {});
    try {
      transceiver1.shutdown(false);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    AtomicInteger counter = new AtomicInteger(0);
    AtomicReference<String> received = new AtomicReference<>("");
    envelope2.get().getMessageBundle().getCastedMessageList(ExceptionMessage.class).forEach(m -> {
      counter.incrementAndGet();
      received.set(m.getMessage());
    });
    assertEquals(1, counter.get());
    assertEquals(testString, received.get());
    transceiver1.waitForShutdown();
    transceiver2.waitForShutdown();
  }
}
