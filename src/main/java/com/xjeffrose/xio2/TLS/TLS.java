package com.xjeffrose.xio2.TLS;

import com.xjeffrose.xio2.http.Http;
import com.xjeffrose.xio2.http.HttpResponse;
import com.xjeffrose.xio2.server.ChannelContext;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

public class TLS {

  private SSLContext sslCtx;
  private SSLEngine engine;

  private ByteBuffer clientOut;       // write side of clientEngine
  private ByteBuffer clientIn;        // read side of clientEngine

  private SSLEngine serverEngine;     // server Engine
  private ByteBuffer serverOut;       // write side of serverEngine
  private ByteBuffer serverIn;        // read side of serverEngine

  private ByteBuffer cTOs;            // "reliable" transport client->server
  private ByteBuffer sTOc;            // "reliable" transport server->client

  private ChannelContext ctx;

  TLS(ChannelContext ctx) {
    this.ctx = ctx;

    try {
      sslCtx = SSLContext.getInstance("TLSv1.2");
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
  }

  private void createEngine() {
    engine = sslCtx.createSSLEngine();
  }

  private void configureEngine() {
    // client
    engine.setUseClientMode(false);
    engine.setNeedClientAuth(false);

    engine.setEnabledCipherSuites(new String[]{"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"});

    engine.setEnabledProtocols(new String[]{"TLSv1.2"});

    engine.setNeedClientAuth(true);
    SSLParameters params = new SSLParameters();

    params.setCipherSuites(new String[]{"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"});
    params.setProtocols(new String[]{"TLSv1.2"});
    engine.setSSLParameters(params);
  }

  private void createBuffers() {

    SSLSession session = engine.getSession();
    int appBufferMax = session.getApplicationBufferSize();
    int netBufferMax = session.getPacketBufferSize();

    clientIn = ByteBuffer.allocateDirect(appBufferMax + 50);
    serverIn = ByteBuffer.allocateDirect(appBufferMax + 50);

    cTOs = ByteBuffer.allocateDirect(netBufferMax);
    sTOc = ByteBuffer.allocateDirect(netBufferMax);

    clientOut = ByteBuffer.wrap("Hi Server, I'm Client".getBytes());
    serverOut = ByteBuffer.wrap("Hello Client, I'm Server".getBytes());
  }

  private void doHandshake() {
    SSLEngineResult.HandshakeStatus handshakeStatus;
    ByteBuffer encryptedResponse = ByteBuffer.allocate(200000);
    ByteBuffer rawResponse = ByteBuffer.allocate(200000);
    try {
      engine.beginHandshake();
      handshakeStatus = engine.getHandshakeStatus();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED &&
        handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
      System.out.println(handshakeStatus.toString());
      switch (handshakeStatus) {

        case NEED_TASK:
          Runnable task;
          while ((task = engine.getDelegatedTask()) != null) {
            new Thread(task).start();
          }
          break;

        case NEED_UNWRAP:
          int nread = 1;
          while (nread >= 0) {
            try {
              nread = ctx.channel.read(encryptedResponse);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }

          encryptedResponse.flip();

          try {
            SSLEngineResult result = engine.unwrap(encryptedResponse, rawResponse);
            rawResponse.compact();
            handshakeStatus = result.getHandshakeStatus();
            SSLEngineResult.Status status = result.getStatus();

            switch (result.getStatus()) {
              case OK:

                //rawResponse.flip();

                byte[] responseArray = new byte[rawResponse.capacity()];
                rawResponse.put(responseArray);
                String response = new String(responseArray, Charset.forName("UTF-8"));
                System.out.print(response);
                break;
              case BUFFER_UNDERFLOW:
                break;
              case BUFFER_OVERFLOW:
                break;
              case CLOSED:
                break;
              default:
                break;
            }
          } catch (SSLException e) {
            e.printStackTrace();
          }
          break;

        case NEED_WRAP:
          ByteBuffer rawClientHandshakeData = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize() + 1000);
          ByteBuffer encryptedClientHandshakeData = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize() + 1000);
          try {
            SSLEngineResult result = engine.wrap(
                HttpResponse.DefaultResponse(Http.Version.HTTP1_1, Http.Status.OK).toBB(),
                encryptedClientHandshakeData);
            handshakeStatus = result.getHandshakeStatus();
            SSLEngineResult.Status status = result.getStatus();
            switch (result.getStatus()) {
              case OK:
                encryptedClientHandshakeData.flip();

                // Send the handshaking data to peer
                while (encryptedClientHandshakeData.hasRemaining()) {
                  try {
                    ctx.write(encryptedClientHandshakeData);
                  } catch (Exception e) {
                    e.printStackTrace();
                  }
                }
                System.out.print("Leaving NEED_WRAP");
                break;
              case BUFFER_UNDERFLOW:
                break;
              case BUFFER_OVERFLOW:
                if (engine.getSession().getApplicationBufferSize() > rawClientHandshakeData.capacity()) {
                  System.out.print("Buffer needs to be bigger");
                } else {
                  rawClientHandshakeData.clear();
                }
                break;
              case CLOSED:
                System.out.print("Closed");
                break;
              default:
                break;
            }

          } catch (SSLException e) {
            e.printStackTrace();
          }

          break;


        default:
          break;
      }
    }
  }

  public void encrypt() {

  }

  public void decrypt() {

  }

  public void close() {

  }

  public void terminate() {

  }
}
