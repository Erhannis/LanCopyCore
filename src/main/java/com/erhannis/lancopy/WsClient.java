/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.jmdns.ServiceInfo;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Registers with remote services to listen for remote summary updates.
 *
 * @author erhannis
 */
public class WsClient extends WebSocketListener {
  private final DataOwner dataOwner;
  private final ConcurrentHashMap<WebSocket, NodeInfo> socket2info = new ConcurrentHashMap<>();
  //TODO Bleh, I had a reason for adding this, what was it?
  private final ConcurrentHashMap<NodeInfo, WebSocket> info2socket = new ConcurrentHashMap<>();
  private final OkHttpClient client = new OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).build();

  private static final int NORMAL_CLOSURE_STATUS = 1000;
  
  public WsClient(DataOwner dataOwner) {
    this.dataOwner = dataOwner;
  }

  @Override
  public void onOpen(WebSocket webSocket, Response response) {
    System.out.println("CWS Open");
    //dataOwner.remoteServices.put(socketIds.get(webSocket), "???"); //TODO Change
  }

  @Override
  public void onMessage(WebSocket webSocket, String text) {
    System.out.println("CWS Receiving : " + text);
    text = text.substring(0, Math.min(text.length(), dataOwner.SUMMARY_LENGTH));
    NodeInfo info = socket2info.get(webSocket);
    dataOwner.observedNode(new NodeInfo(info.id, info.url, text, NodeInfo.State.ACTIVE));
  }

  @Override
  public void onMessage(WebSocket webSocket, ByteString bytes) {
    System.out.println("CWS Receiving bytes : " + bytes.hex());
  }

  @Override
  public void onClosing(WebSocket webSocket, int code, String reason) {
    webSocket.close(NORMAL_CLOSURE_STATUS, null); //TODO Should this?
    System.out.println("CWS Closing : " + code + " / " + reason);
    NodeInfo info = socket2info.get(webSocket);
    dataOwner.observedNode(new NodeInfo(info.id, info.url, info.summary, NodeInfo.State.INACTIVE));
  }

  @Override
  public void onFailure(WebSocket webSocket, Throwable t, Response response) {
    System.err.println("CWS Error : " + t.getMessage());
    NodeInfo info = socket2info.get(webSocket);
    dataOwner.observedNode(new NodeInfo(info.id, info.url, info.summary, NodeInfo.State.INACTIVE));
  }

  public void addNode(NodeInfo info) {
    System.out.println("CWS addNode " + info.id);
    //TODO Janky addressing again
    Request request = new Request.Builder().url("http://"+info.url+"/monitor").build();
    WebSocket ws = client.newWebSocket(request, this);
    socket2info.put(ws, info);
    info2socket.put(info, ws);
    // For availability robustness, self-report to server
    ws.send(dataOwner.ID+";"+dataOwner.PORT+";"+dataOwner.localSummary.get());
    //ws.send("asdfasdf");
  }

  public void shutdown() {
    for (WebSocket ws : socket2info.keySet()) {
      ws.close(NORMAL_CLOSURE_STATUS, "Shutting down");
    }
    client.dispatcher().executorService().shutdown();
  }
}
