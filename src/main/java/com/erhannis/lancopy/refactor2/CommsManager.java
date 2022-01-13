/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.refactor.Advertisement;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.lancopy.refactor.Summary;
import com.erhannis.lancopy.refactor.tcp.TcpComm;
import com.erhannis.lancopy.refactor2.messages.IdentificationMessage;
import com.erhannis.lancopy.refactor2.tcp.TcpBroadcastBroadcastReceiver;
import com.erhannis.lancopy.refactor2.tcp.TcpBroadcastBroadcastTransmitter;
import com.erhannis.lancopy.refactor2.tcp.TcpCommChannel;
import com.erhannis.lancopy.refactor2.tcp.TcpMulticastBroadcastReceiver;
import com.erhannis.lancopy.refactor2.tcp.TcpMulticastBroadcastTransmitter;
import com.erhannis.mathnstuff.FactoryHashMap;
import com.erhannis.mathnstuff.MeUtils;
import com.erhannis.mathnstuff.Pair;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import jcsp.helpers.JcspUtils;
import jcsp.helpers.SynchronousSplitter;
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.CSProcess;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;
import jcsp.lang.DisableableTimer;
import jcsp.lang.Guard;
import jcsp.lang.Parallel;
import jcsp.lang.ProcessManager;

/**
 *
 * @author erhannis
 */
public class CommsManager implements CSProcess {
    public static class CommsToken {
        // ???
    }

    private final DataOwner dataOwner;
    
    public CommsManager(DataOwner dataOwner, ChannelOutput<List<Comm>> lcommsOut, ChannelOutput<Advertisement> radOut, AltingChannelInput<Advertisement> aadIn, ChannelOutput<Summary> rsumOut, AltingChannelInput<Summary> lsumIn, ChannelOutput<Pair<Comm,Boolean>> statusOut, AltingChannelInput<List<Comm>> subscribeIn) {
        this.dataOwner = dataOwner;

        this.lcommsOut = lcommsOut;
        this.radOut = radOut;
        this.aadIn = aadIn;
        this.rsumOut = rsumOut;
        this.lsumIn = lsumIn;
        this.statusOut = statusOut;
        this.subscribeIn = subscribeIn;
        
        Any2OneChannel<Pair<NodeManager.CRToken,byte[]>> internalRxMsgChannel = Channel.<Pair<NodeManager.CRToken,byte[]>> any2one();
        this.internalRxMsgIn = internalRxMsgChannel.in();
        this.internalRxMsgOut = JcspUtils.logDeadlock(internalRxMsgChannel.out());
        
        Any2OneChannel<NodeManager.ChannelReader> internalChannelReaderShuffleAChannel = Channel.<NodeManager.ChannelReader> any2one();
        this.internalChannelReaderShuffleAIn = internalChannelReaderShuffleAChannel.in();
        this.internalChannelReaderShuffleAOut = JcspUtils.logDeadlock(internalChannelReaderShuffleAChannel.out());
        
        Any2OneChannel<Pair<Comm,Boolean>> internalCommStatusChannel = Channel.<Pair<Comm,Boolean>> any2one();
        this.internalCommStatusIn = internalCommStatusChannel.in();
        this.internalCommStatusOut = JcspUtils.logDeadlock(internalCommStatusChannel.out());
        
        this.broadcastMsgSplitter = new SynchronousSplitter<byte[]>();
        this.txMsgSplitter = new SynchronousSplitter<byte[]>();
    }

    private final ChannelOutput<List<Comm>> lcommsOut;
    private final ChannelOutput<Advertisement> radOut;
    private final AltingChannelInput<Advertisement> aadIn;
    private final ChannelOutput<Summary> rsumOut;
    private final AltingChannelInput<Summary> lsumIn;
    private final ChannelOutput<Pair<Comm,Boolean>> statusOut;
    private final AltingChannelInput<List<Comm>> subscribeIn;
    private final AltingChannelInput<Pair<NodeManager.CRToken,byte[]>> internalRxMsgIn;
    private final ChannelOutput<Pair<NodeManager.CRToken,byte[]>> internalRxMsgOut;
    private final AltingChannelInput<NodeManager.ChannelReader> internalChannelReaderShuffleAIn;
    private final ChannelOutput<NodeManager.ChannelReader> internalChannelReaderShuffleAOut;
    private final AltingChannelInput<Pair<Comm,Boolean>> internalCommStatusIn;
    private final ChannelOutput<Pair<Comm,Boolean>> internalCommStatusOut;
    private final SynchronousSplitter<byte[]> broadcastMsgSplitter;
    private final SynchronousSplitter<byte[]> txMsgSplitter;

    private HashMap<UUID, NodeManager.NMInterface> nodes = new HashMap<>();
    
    private NodeManager.NMInterface startNodeManager(UUID id, boolean registerToSplitter) {
        // Blehhhhh, this is boilerplatey
        Any2OneChannel<byte[]> txMsgChannel = Channel.<byte[]> any2one();
        AltingChannelInput<byte[]> txMsgIn = txMsgChannel.in();
        ChannelOutput<byte[]> txMsgOut = JcspUtils.logDeadlock(txMsgChannel.out());
        if (registerToSplitter) {
            this.txMsgSplitter.register(txMsgOut);
        }

        
        Any2OneChannel<NodeManager.CRToken> demandShuffleChannelChannel = Channel.<NodeManager.CRToken> any2one();
        AltingChannelInput<NodeManager.CRToken> demandShuffleChannelIn = demandShuffleChannelChannel.in();
        ChannelOutput<NodeManager.CRToken> demandShuffleChannelOut = JcspUtils.logDeadlock(demandShuffleChannelChannel.out());
        
        
        Any2OneChannel<NodeManager.ChannelReader> channelReaderShuffleBChannel = Channel.<NodeManager.ChannelReader> any2one();
        AltingChannelInput<NodeManager.ChannelReader> channelReaderShuffleBIn = channelReaderShuffleBChannel.in();
        ChannelOutput<NodeManager.ChannelReader> channelReaderShuffleBOut = JcspUtils.logDeadlock(channelReaderShuffleBChannel.out());
        
        Any2OneChannel<CommChannel> incomingConnectionChannel = Channel.<CommChannel> any2one();
        AltingChannelInput<CommChannel> incomingConnectionIn = incomingConnectionChannel.in();
        ChannelOutput<CommChannel> incomingConnectionOut = JcspUtils.logDeadlock(incomingConnectionChannel.out());

        Any2OneChannel<List<Comm>> subscribeChannel = Channel.<List<Comm>> any2one();
        AltingChannelInput<List<Comm>> subscribeIn = subscribeChannel.in();
        ChannelOutput<List<Comm>> subscribeOut = JcspUtils.logDeadlock(subscribeChannel.out());
        
        
        new ProcessManager(new NodeManager(dataOwner, id, txMsgIn, internalRxMsgOut, demandShuffleChannelIn, internalChannelReaderShuffleAOut, channelReaderShuffleBIn, incomingConnectionIn, subscribeIn, internalCommStatusOut)).start();
        NodeManager.NMInterface nmi = new NodeManager.NMInterface(txMsgOut, internalRxMsgIn, demandShuffleChannelOut, internalChannelReaderShuffleAIn, channelReaderShuffleBOut, incomingConnectionOut, subscribeOut, internalCommStatusIn);
        nodes.put(id, nmi);
        return nmi;
    }
    
    @Override
    public void run() {
        Any2OneChannel<CommChannel> internalCommChannelChannel = Channel.<CommChannel> any2one();
        AltingChannelInput<CommChannel> internalCommChannelIn = internalCommChannelChannel.in();
        ChannelOutput<CommChannel> internalCommChannelOut = JcspUtils.logDeadlock(internalCommChannelChannel.out());
        
        Any2OneChannel<byte[]> internalRxBroadcastChannel = Channel.<byte[]> any2one();
        AltingChannelInput<byte[]> internalRxBroadcastIn = internalRxBroadcastChannel.in();
        ChannelOutput<byte[]> internalRxBroadcastOut = JcspUtils.logDeadlock(internalRxBroadcastChannel.out());
        
        
        
        
        //TODO Move specifics elsewhere?
        //TODO This is kinda cluttered

        int ipv4MulticastPort = (int) dataOwner.options.getOrDefault("Comms.tcp.multicast.ipv4.port", 12113);
        String ipv4MulticastAddress = (String) dataOwner.options.getOrDefault("Comms.tcp.multicast.ipv4.address", "234.119.187.64");
        int ipv6MulticastPort = (int) dataOwner.options.getOrDefault("Comms.tcp.multicast.ipv6.port", 12114);
        String ipv6MulticastAddress = (String) dataOwner.options.getOrDefault("Comms.tcp.multicast.ipv6.address", "[ff05:acbc:d10a:5fa4:9dac:4ff5:3dbe:aacc]"); //TODO Figure out port
        
        int broadcastPort = (int) dataOwner.options.getOrDefault("Comms.tcp.broadcast.port", 12115);
        //TODO It'd be nice if LanCopy automatically responded to network configuration changes, but that's a liiiitle out of scope for now
        String[] broadcastAddresses = TcpBroadcastBroadcastTransmitter.enumerateBroadcastAddresses();
        ArrayList<CSProcess> tbbts = new ArrayList<>();
        for (int i = 0; i < broadcastAddresses.length; i++) {
            tbbts.add(new TcpBroadcastBroadcastTransmitter(broadcastAddresses[i], broadcastPort, this.broadcastMsgSplitter.register()));
        }
        
        //TODO Move these somewhere else?  Abstract?
        new ProcessManager(new Parallel(new CSProcess[] {
            this.broadcastMsgSplitter,
            this.txMsgSplitter,
            
            // TCP
            () -> { // TCP server listener
                //TODO Fallback to "Comms.tcp.enabled"?
                boolean tcpEnabled = (Boolean) dataOwner.options.getOrDefault("Comms.tcp.server_enabled", true);

                if (tcpEnabled) {
                    int port = (int) dataOwner.options.getOrDefault("Comms.tcp.server_port", 0);
                    try {
                        TcpCommChannel.ServerThread st = TcpCommChannel.serverThread(internalCommChannelOut, port);

                        // Determine Comms
                        //TODO Allow whitelist/blacklist interfaces
                        dataOwner.errOnce("CommsManager.TCP //TODO Deal with interface changes?");
                        ArrayList<Comm> newComms = new ArrayList<>();
                        try {
                            for (InetAddress addr : MeUtils.listAllInterfaceAddresses()) {
                                try {
                                    newComms.add(new TcpComm(null, InetAddress.getByAddress(addr.getAddress()).getHostAddress(), st.boundPort));
                                } catch (UnknownHostException ex) {
                                    Logger.getLogger(CommsManager.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                        } catch (SocketException ex) {
                            Logger.getLogger(CommsManager.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        lcommsOut.write(newComms); //TODO Note that these stack on top of whatever Comms are already in the Ad
                        
                        while (true) {
                            try {
                                st.run();
                            } catch (IOException ex) {
                                Logger.getLogger(CommsManager.class.getName()).log(Level.SEVERE, null, ex);
                                //TODO Will I need to rebind the port or something?  And reset the Ad?
                            }
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(CommsManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            },
            new TcpBroadcastBroadcastReceiver(broadcastPort, internalRxBroadcastOut), //TODO Make channel poisonable, for closing?
            new Parallel(tbbts.toArray(new CSProcess[0])),
            new TcpMulticastBroadcastReceiver(ipv4MulticastPort, ipv4MulticastAddress, internalRxBroadcastOut), //TODO Ditto
            new TcpMulticastBroadcastTransmitter(ipv4MulticastAddress, ipv4MulticastPort, this.broadcastMsgSplitter.register()), //TODO Ditto
            new TcpMulticastBroadcastReceiver(ipv6MulticastPort, ipv6MulticastAddress, internalRxBroadcastOut), //TODO Ditto
            new TcpMulticastBroadcastTransmitter(ipv6MulticastAddress, ipv6MulticastPort, this.broadcastMsgSplitter.register()), //TODO Ditto
        })).start();
        
        
        
        
        
        // Start NM to hold incame connections
        startNodeManager(null, false);
        
        //TODO Add verification to make sure nodes' claims match their TLS credentials
        //DO Read Identification message first off, transfer channel
        //DO Find deadlock cycles
        //DO Make sure logdeadlock
        //DO Check TODO table for messages to send when
        //DO Double check case/breaks
        //DO Add manual URLs
        
        byte[] lastBroadcast = null;
        DisableableTimer rebroadcastTimer = new DisableableTimer();
        long rebroadcastInterval = (long) dataOwner.options.getOrDefault("Advertisers.rebroadcast_interval", 30000L);
        if (rebroadcastInterval < 0) {
            rebroadcastTimer.turnOff();
        } else {
            rebroadcastTimer.setAlarm(rebroadcastTimer.read() + rebroadcastInterval);
        }
        Alternative alt = new Alternative(new Guard[]{aadIn, lsumIn, subscribeIn, internalRxMsgIn, internalChannelReaderShuffleAIn, internalCommStatusIn, internalCommChannelIn, internalRxBroadcastIn, rebroadcastTimer});
        while (true) {
            try {
                switch (alt.priSelect()) {
                    case 0: { // aadIn
                        Advertisement ad = aadIn.read();
                        byte[] msg = dataOwner.serialize(ad);

                        // Broadcast local ad
                        if (Objects.equals(ad.id, dataOwner.ID)) {
                            //System.out.println("BroadcastAdvertiser  txb " + MeUtils.cleanTextContent(new String(msg), "�"));
                            lastBroadcast = msg;
                            broadcastMsgSplitter.write(msg);
                        }

                        // Tx Ad to connected nodes
                        //DO Skip null keys?
                        this.txMsgSplitter.write(msg);
                        break;
                    }
                    case 1: { // lsumIn
                        //DO Cache, and autosend to new connections?
                        Summary summary = lsumIn.read();
                        byte[] msg = dataOwner.serialize(summary);
                        //DO Skip null keys?
                        this.txMsgSplitter.write(msg);
                        break;
                    }
                    case 2: { // subscribeIn
                        List<Comm> comms = subscribeIn.read();
                        FactoryHashMap<UUID, ArrayList<Comm>> separated = new FactoryHashMap<UUID, ArrayList<Comm>>((input) -> {
                            return new ArrayList<Comm>();
                        });
                        for (Comm comm : comms) {
                            separated.get(comm.owner.id).add(comm);
                        }
                        for (UUID id : separated.keySet()) {
                            if (!nodes.containsKey(id)) {
                                startNodeManager(id, true);
                            }
                            nodes.get(id).subscribeOut.write(separated.get(id));
                        }
                        break;
                    }
                    case 3: { // internalRxMsgIn
                        Pair<NodeManager.CRToken,byte[]> msg = internalRxMsgIn.read();
                        Object o = dataOwner.deserialize(msg.b);
                        if (o instanceof IdentificationMessage) {
                            IdentificationMessage im = (IdentificationMessage) o;
                            if (!Objects.equals(im.nodeId, msg.a.nodeId)) {
                                UUID fromId = msg.a.nodeId;
                                // Wrong NodeManager; shuffle to correct
                                msg.a.nodeId = im.nodeId;
//                                if (!nodes.containsKey(msg.a.nodeId)) {
//                                    startNodeManager(msg.a.nodeId, true);
//                                }
                                nodes.get(fromId).demandShuffleChannelOut.write(msg.a);
                            }
                        } else if (o instanceof Advertisement) {
                            Advertisement ad = (Advertisement) o;
                            radOut.write(ad);
                        } else if (o instanceof Summary) {
                            Summary rsum = (Summary) o;
                            rsumOut.write(rsum);
                        } else {
                            //dataOwner.errOnce("ERR CommsManager got unhandled msg: " + o);
                            System.err.println("ERR CommsManager got unhandled msg: " + o);
                        }
                        //DO ;
                        // Request data: get data, add to list, set timer to 0 while list, on timer write chunks
                        break;
                    }
                    case 4: { // internalChannelReaderShuffleAIn
                        NodeManager.ChannelReader cr = internalChannelReaderShuffleAIn.read();
                        UUID id = cr.token.nodeId;
                        if (!nodes.containsKey(id)) {
                            startNodeManager(id, true);
                        }
                        nodes.get(id).channelReaderShuffleBOut.write(cr);
                        break;
                    }
                    case 5: { // internalCommStatusIn
                        Pair<Comm,Boolean> status = internalCommStatusIn.read();
                        //TODO Status of incame connections, too?
                        statusOut.write(status);
                        break;
                    }
                    case 6: { // internalCommChannelIn
                        CommChannel cc = internalCommChannelIn.read();
                        // We don't know which node connected to us, yet, so it goes to the blank NM
                        nodes.get(null).incomingConnectionOut.write(cc);
                        break;
                    }
                    case 7: { // internalRxBroadcastIn
                        //SECURITY Note that this source is untrusted
                        byte[] msg = internalRxBroadcastIn.read();
                        Object o = dataOwner.deserialize(msg);
                        //System.out.println("BroadcastAdvertiser  rx  " + MeUtils.cleanTextContent(new String(msg), "�"));
                        if (o instanceof Advertisement) {
                            Advertisement ad = (Advertisement) o;
                            radOut.write(ad);
                        } else {
                            //dataOwner.errOnce("ERR CommsManager got unhandled broadcast msg: " + o);
                            System.err.println("ERR CommsManager got unhandled broadcast msg: " + o);
                        }
                        break;
                    }
                    case 8: // rebroadcastTimer
                    {
                        //TODO Permit separate intervals for different broadcast mechanisms?
                        rebroadcastInterval = (long) dataOwner.options.getOrDefault("Advertisers.rebroadcast_interval", 30000L);
                        rebroadcastTimer.setAlarm(rebroadcastTimer.read() + rebroadcastInterval);
                        if (lastBroadcast != null) {
                            System.out.println("CommsManager rebroadcast");
                            //System.out.println("BroadcastAdvertiser rtxb " + MeUtils.cleanTextContent(new String(msg), "�"));
                            broadcastMsgSplitter.write(lastBroadcast);
                        }
                        break;
                    }
                }
            } catch (Throwable t) {
                System.err.println("CommsManager got error; continuing");
                t.printStackTrace();
            }
        }
    }
}
