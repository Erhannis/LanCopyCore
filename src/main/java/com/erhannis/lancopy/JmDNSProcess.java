/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

import com.erhannis.lancopy.data.BinaryData;
import com.erhannis.lancopy.data.Data;
import com.erhannis.lancopy.data.ErrorData;
import com.erhannis.lancopy.data.FilesData;
import com.erhannis.lancopy.data.TextData;
import com.erhannis.mathnstuff.utils.ObservableMap.Change;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import spark.Spark;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import xyz.gianlu.zeroconf.Service;
import xyz.gianlu.zeroconf.Zeroconf;

public class JmDNSProcess {
  private static class LCListener implements ServiceListener {
    private final DataOwner dataOwner;
    private final String localId;
    private final WsClient wsClient;

    public LCListener(DataOwner dataOwner, String localId, WsClient wsClient) {
      this.dataOwner = dataOwner;
      this.localId = localId;
      this.wsClient = wsClient;
      this.dataOwner.remoteNodes.subscribe((Change<String, NodeInfo> change) -> {
        //TODO This seems a little rickety to me
        if (change.wasRemoved) {
          if (change.wasAdded) {
            // Value swapped
            if (Objects.equals(change.valueRemoved.id, change.valueAdded.id)) {
              if (!Objects.equals(change.valueRemoved.url, change.valueAdded.url)) {
                // URL has changed
                //TODO Seems like this could result in a subsequent "connection closed" msg from the client, on the first URL, messing up the apparent state.
                wsClient.addNode(change.valueAdded);
              }
            }
          } else {
            //TODO Removed outright.  Do something?
          }
        } else if (change.wasAdded) {
          wsClient.addNode(change.valueAdded);
        }
      });
    }

    @Override
    public void serviceAdded(ServiceEvent event) {
      System.out.println("Service added: " + event.getInfo());
    }

    //TODO The updating responsibilities seem split weirdly between here and WsClient
    @Override
    public void serviceRemoved(ServiceEvent event) {
      System.out.println("Service removed: " + event.getInfo());
      //dataOwner.remoteServices.remove(event.getName()); //TODO Change
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
      System.out.println("Service resolved: " + event.getInfo());
      if (!Objects.equals(event.getName(), localId.toString())) {
        dataOwner.observedNode(new NodeInfo(event.getName(), event.getInfo().getURL("http"), "???", NodeInfo.State.ACTIVE));
      }
    }
  }

  public final String ID = "LanCopy-" + UUID.randomUUID();
  public final int PORT;

  private final DataOwner dataOwner;
  private final WsServer wsServer;
  private final WsClient wsClient;

  private final JmDNS jmdns;
  private final Zeroconf zeroconf;
  private final Service zcService;

  private JmDNSProcess(DataOwner dataOwner) {
    this.dataOwner = dataOwner;
    this.wsServer = new WsServer(dataOwner);
    this.wsClient = new WsClient(dataOwner);

    Spark.port(0);

//    try {
//      //TODO //SECURITY Change according to settings
//      dataOwner.localData.set(new TextData((String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor)));
//    } catch (UnsupportedFlavorException ex) {
//      Logger.getLogger(JmDNSProcess.class.getName()).log(Level.SEVERE, null, ex);
//    } catch (IOException ex) {
//      Logger.getLogger(JmDNSProcess.class.getName()).log(Level.SEVERE, null, ex);
//    }

    Spark.webSocket("/monitor", wsServer);
    Spark.get("/data", (request, response) -> { //TODO //SECURITY Note - this DOES mean your clipboard is always accessible by anyone.  OTOH, this is be design, until we have some form of authentication.
      //TODO Support other flavors
      Data data = dataOwner.localData.get();
      response.type(data.getMime());
      return data.serialize();
    });
    //TODO Support files
    Spark.awaitInitialization();
    PORT = Spark.port(); // There's a brief race condition, here, btw, if endpoint is called before this line
    System.out.println("JmDNSProcess " + ID + " starting on port " + PORT);

    //TODO Should it be the wsServer that registers itself??  Unsure.
    dataOwner.localSummary.subscribe((summary) -> {
      System.out.println("LS: " + summary);
      wsServer.broadcast(summary);
    });

    Zeroconf zeroconf0 = null;
    Service zcService0 = null;
    try {
      zeroconf0 = new Zeroconf();
      zeroconf0.setUseIpv4(true)
               .setUseIpv6(false)
               .addAllNetworkInterfaces();

      zcService0 = new Service(ID, "lancopy", PORT);
      zeroconf0.announce(zcService0);
    } catch (IOException ex) {
      Logger.getLogger(JmDNSProcess.class.getName()).log(Level.SEVERE, null, ex);
    }
    this.zeroconf = zeroconf0;
    this.zcService = zcService0;

    JmDNS jmdns0 = null;
    try {
      // Create a JmDNS instance
      jmdns0 = JmDNS.create(InetAddress.getLocalHost());

      jmdns0.addServiceListener("_lancopy._tcp.local.", new LCListener(dataOwner, ID, wsClient));
    } catch (IOException e) {
      System.out.println(e.getMessage());
    }
    this.jmdns = jmdns0;
  }

  /**
   * Static method, to hint that this kicks off threads
   *
   * @return
   */
  public static JmDNSProcess start(DataOwner dataOwner) {
    return new JmDNSProcess(dataOwner);
  }

  private final OkHttpClient client = new OkHttpClient();

  public Data pullFromNode(String id) throws IOException {
    Request request = new Request.Builder().url(dataOwner.remoteNodes.get(id).url + "/data").build();
    try (Response response = client.newCall(request).execute()) {
      switch (response.header("content-type")) {
        case "text/plain":
          return TextData.deserialize(response.body().byteStream());
        case "application/octet-stream":
          return BinaryData.deserialize(response.body().byteStream());
        case "lancopy/files":
          return FilesData.deserialize(response.body().byteStream());
        default:
          return new ErrorData("Unhandled MIME: " + response.header("content-type"));
      }
    }
  }

  public void shutdown() {
    jmdns.unregisterAllServices();
    wsClient.shutdown();
    zeroconf.unannounce(zcService);
    zeroconf.close();
  }
}
