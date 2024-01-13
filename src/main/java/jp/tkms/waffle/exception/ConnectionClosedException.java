package jp.tkms.waffle.exception;

import jp.tkms.waffle.communicator.AbstractSubmitter;

import java.io.IOException;

public class ConnectionClosedException extends WaffleException {
  public ConnectionClosedException(Throwable e) {
    super(e);
  }

  public ConnectionClosedException(AbstractSubmitter submitter) {
    setMessage("connection closed - " + submitter.getClass().getName());
  }
}
