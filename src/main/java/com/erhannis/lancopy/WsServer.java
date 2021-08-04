/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jetty.util.MultiException;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

/**
 * Accepts incoming connections, and emits local summary updates
 *
 * @author erhannis
 */
@WebSocket
public class WsServer {
  private final DataOwner dataOwner;

  private static final Queue<Session> sessions = new ConcurrentLinkedQueue<>();

  public WsServer(DataOwner dataOwner) {
    this.dataOwner = dataOwner;
  }

  @OnWebSocketConnect
  public void connected(Session session) throws IOException {
    System.out.println("SWS Connected");
    sessions.add(session);
    try {
      String str = dataOwner.localSummary.get();
      session.getRemote().sendString(str != null ? str : "");
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  @OnWebSocketClose
  public void closed(Session session, int statusCode, String reason) {
    System.out.println("SWS Closing : " + statusCode + " / " + reason);
    sessions.remove(session);
  }

  @OnWebSocketMessage
  public void message(Session session, String message) throws IOException {
      System.out.println("SWS Receiving : " + message);
      String[] parts = message.split(";", 3);
      String id = parts[0];
      int port = Integer.parseInt(parts[1]);
      String summary = parts[2];
      String url = session.getRemoteAddress().getAddress().getHostAddress()+":"+port;
      dataOwner.observedNode(new NodeInfo(id, url, summary, NodeInfo.State.ACTIVE));
  }

  public void broadcast(String str) {
    MultiException me = new MultiException();
    for (Session s : sessions) {
      try {
        s.getRemote().sendString(str);
      } catch (IOException ex) {
        me.addSuppressed(ex);
      }
    }
    if (me.getSuppressed().length > 0) {
      try {
        throw me;
      } catch (MultiException ex) {
        Logger.getLogger(WsServer.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
  }
}
