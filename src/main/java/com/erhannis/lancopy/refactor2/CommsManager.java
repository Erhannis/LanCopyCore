/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.refactor.Advertisement;
import com.erhannis.lancopy.refactor2.tcp.TcpCommChannel;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.CSProcess;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;
import jcsp.lang.Guard;

/**
 *
 * @author erhannis
 */
public class CommsManager implements CSProcess {
    public static class CommsToken {
        // ???
    }

    private final DataOwner dataOwner;
    
    public CommsManager(DataOwner dataOwner) {
        this.dataOwner = dataOwner;
    }

    private final ChannelOutput<CommChannel> serverChannelOut;
    private final AltingChannelInput<Advertisement> aadIn;

    public void blah1() {
        //TODO Fallback to "Comms.tcp.enabled"?
        boolean tcpEnabled = (Boolean) dataOwner.options.getOrDefault("Comms.tcp.server_enabled", true);
        
        int port = (int) dataOwner.options.getOrDefault("Comms.tcp.server_port", 0);
        while (true) {
            try {
                TcpCommChannel.serverThread(sc -> {
                    TcpCommChannel tcpCommChannel = new TcpCommChannel(interrupt -> {
                        System.err.println("//TODO Handle interrupt properly");
                        return true;
                    }, sc);
                    serverChannelOut.write(tcpCommChannel);
                }, port);
            } catch (IOException ex) {
                Logger.getLogger(CommsManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    /**
     */
    private HashMap<UUID, NodeManager> nodes = new HashMap<>();
    
    @Override
    public void run() {
        Any2OneChannel<CommChannel> serverChannelChannel = Channel.<CommChannel> any2one();
        AltingChannelInput<CommChannel> serverChannelIn = serverChannelChannel.in();
        ChannelOutput<CommChannel> serverChannelOut = serverChannelChannel.out();
        
        //DO Upon initiating connection, send ID message
        //TODO Add verification to make sure nodes' claims match their TLS credentials
        
        Alternative alt = new Alternative(new Guard[]{aadIn, lsumIn, subIn, internalMsgRxIn, internalCommChannelIn});
        while (true) {
            switch (alt.priSelect()) {
                case 0: // aadIn
                    Advertisement ad = aadIn.read();
                    byte[] msg = dataOwner.serialize(ad);
                    
                    // Broadcast local ad
                    if (Objects.equals(ad.id, dataOwner.ID)) {
                        //System.out.println("BroadcastAdvertiser  txb " + MeUtils.cleanTextContent(new String(msg), "�"));
                        broadcastMsgOut.accept(msg);
                    }
                    
                    // Tx Ad to connected nodes
                    for (Iterator<ArrayList<CommChannel>> ccsi = connections.values().iterator(); ccsi.hasNext();) {
                        ArrayList<CommChannel> ccs = ccsi.next();
                        if (!ccs.isEmpty()) {
                            CommChannel cc = ccs.get(0);
                            try {
                                cc.write(ByteBuffer.wrap(msg));
                            } catch (IOException ex) {
                                Logger.getLogger(CommsManager.class.getName()).log(Level.SEVERE, null, ex);
                                System.err.println("CommsManager IOException broadcasting Ad - removing channel");
                                try {
                                    cc.close();
                                } catch (IOException ex1) {
                                }
                                ccs.remove(0);
                            }
                        } else {
                            //TODO Should we log?
                            //System.err.println("CommsManager - no comm open for " + );
                        }
                    }
                    break;
                case 1: // lsumIn
                    DO;
                    break;
                case 2: // subIn
                    //DO Make connection
                    DO;
                    break;
                case 3: // internalMsgRxIn
                    DO;
                    break;
                case 4: // internalCommChannelIn
                    DO;
                    break;
            }
        }
    }
}
