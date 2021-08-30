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
import java.io.IOException;
import java.net.InetAddress;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import xyz.gianlu.zeroconf.Service;
import xyz.gianlu.zeroconf.Zeroconf;

public class JmDNSProcess {
  private static class LCListener implements ServiceListener {
    private final DataOwner dataOwner;

    public LCListener(DataOwner dataOwner) {
      this.dataOwner = dataOwner;
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
      if (!Objects.equals(event.getName(), dataOwner.ID.toString())) {
        //TODO The address thing is kinda janky
        //dataOwner.observedNode(new NodeInfo(event.getName(), event.getInfo().getHostAddress()+":"+event.getInfo().getPort(), "???", NodeInfo.State.ACTIVE));
      }
    }
  }

  private final DataOwner dataOwner;

  private final JmDNS jmdns;
  private final Zeroconf zeroconf;
  private final Service zcService;

  private JmDNSProcess(DataOwner dataOwner) {
    this.dataOwner = dataOwner;

    Zeroconf zeroconf0 = null;
    Service zcService0 = null;
    try {
      zeroconf0 = new Zeroconf();
      zeroconf0.setUseIpv4(true)
               .setUseIpv6(false)
               .addAllNetworkInterfaces();

      if (1==1) throw new RuntimeException("//TODO BROKEN");
      int PORT = -1;
      zcService0 = new Service(dataOwner.ID, "lancopy", PORT);
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

      jmdns0.addServiceListener("_lancopy._tcp.local.", new LCListener(dataOwner));
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
    //TODO Janky addressing, again
    if (1==1) throw new RuntimeException("//TODO BROKEN");
    String addr = id;
    Request request = new Request.Builder().url("http://"+id+"/data").build();
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
    zeroconf.unannounce(zcService);
    zeroconf.close();
  }
}
