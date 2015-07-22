package com.xjeffrose.xio2.TLS;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;

class Tasker implements Runnable {

  private SSLEngine engine;
  private ByteBuffer inNet;
  private Boolean isTasking;
  private SSLEngineResult.HandshakeStatus hsStatus;


  Tasker(SSLEngine engine) {

    this.engine = engine;

  }

  public void run() {
    Runnable r;
    while ((r = engine.getDelegatedTask()) != null) {
      r.run();
    }
    if (inNet.position() > 0) {
      regnow(); // we may already have read what is needed
    }
    try {
      System.out.println(":" + engine.getHandshakeStatus());
      switch (engine.getHandshakeStatus()) {
        case NOT_HANDSHAKING:
          break;
        case FINISHED:
          System.err.println("Detected FINISHED in tasker");
          Thread.dumpStack();
          break;
        case NEED_TASK:
          System.err.println("Detected NEED_TASK in tasker");
          assert false;
          break;
        case NEED_WRAP:
          rereg(SelectionKey.OP_WRITE);
          break;
        case NEED_UNWRAP:
          rereg(SelectionKey.OP_READ);
          break;
      }
    } catch (Exception e) {
      e.printStackTrace();
      try {
        shutdown();
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
    hsStatus = engine.getHandshakeStatus();
    isTasking = false;
  }

  private void regnow() {
  }

  private void shutdown() {
  }

  private void rereg(int opRead) {
  }
}