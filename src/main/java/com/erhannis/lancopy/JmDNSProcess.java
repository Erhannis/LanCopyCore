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
import com.erhannis.lancopy.refactor.Advertisement;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.lancopy.refactor.tcp.TcpComm;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import jcsp.lang.ChannelOutput;
import xyz.gianlu.zeroconf.Service;
import xyz.gianlu.zeroconf.Zeroconf;

public class JmDNSProcess {

    private static class LCListener implements ServiceListener {

        private final DataOwner dataOwner;
        private final ChannelOutput<Advertisement> radOut;

        public LCListener(DataOwner dataOwner, ChannelOutput<Advertisement> radOut) {
            this.dataOwner = dataOwner;
            this.radOut = radOut;
        }

        @Override
        public void serviceAdded(ServiceEvent event) {
            System.out.println("Service added: " + event.getInfo());
        }

        @Override
        public void serviceRemoved(ServiceEvent event) {
            System.out.println("Service removed: " + event.getInfo());
        }

        @Override
        public void serviceResolved(ServiceEvent event) {
            System.out.println("Service resolved: " + event.getInfo());
            try {
                if (!Objects.equals(event.getName(), dataOwner.ID.toString())) {
                    //TODO This conflicts a bit with other ads
                    // timestamp 0 so if anything else has come in, this will have no effect
                    //TODO Though, seems like maybe that'd be good in some cases

                    ArrayList<Comm> rComms = new ArrayList<>();
                    for (InetAddress addr : event.getInfo().getInetAddresses()) {
                        rComms.add(new TcpComm(null, addr.getHostAddress(), event.getInfo().getPort()));
                    }
                    //TODO Remote may not be encrypted
                    Advertisement rad = new Advertisement(UUID.fromString(event.getName()), 0L, rComms, true, null);
                    radOut.write(rad);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private final DataOwner dataOwner;

    private final JmDNS jmdns;
    private final Zeroconf zeroconf;
    private final Service zcService;

    private JmDNSProcess(DataOwner dataOwner, int port, ChannelOutput<Advertisement> radOut) {
        this.dataOwner = dataOwner;

        Zeroconf zeroconf0 = null;
        Service zcService0 = null;
        try {
            zeroconf0 = new Zeroconf();
            zeroconf0.setUseIpv4(true)
                    .setUseIpv6(true) //DO Do?
                    .addAllNetworkInterfaces();

            //if (1==1) throw new RuntimeException("//TODO BROKEN");
            zcService0 = new Service(dataOwner.ID.toString(), "lancopy", port);
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

            jmdns0.addServiceListener("_lancopy._tcp.local.", new LCListener(dataOwner, radOut));
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
    public static JmDNSProcess start(DataOwner dataOwner, int port, ChannelOutput<Advertisement> radOut) {
        return new JmDNSProcess(dataOwner, port, radOut);
    }

    public void shutdown() {
        jmdns.unregisterAllServices();
        zeroconf.unannounce(zcService);
        zeroconf.close();
    }
}
