/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.JmDNSProcess;
import com.erhannis.lancopy.data.Data;
import com.erhannis.lancopy.refactor.Advertisement;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.lancopy.refactor.Summary;
import com.erhannis.lancopy.refactor.tcp.TcpComm;
import com.erhannis.lancopy.refactor2.messages.DataChunkMessage;
import com.erhannis.lancopy.refactor2.messages.DataRequestMessage;
import com.erhannis.lancopy.refactor2.messages.DataStartMessage;
import com.erhannis.lancopy.refactor2.messages.IdentificationMessage;
import com.erhannis.lancopy.refactor2.tcp.TcpBroadcastBroadcastReceiver;
import com.erhannis.lancopy.refactor2.tcp.TcpBroadcastBroadcastTransmitter;
import com.erhannis.lancopy.refactor2.tcp.TcpCommChannel;
import com.erhannis.lancopy.refactor2.tcp.TcpMulticastBroadcastReceiver;
import com.erhannis.lancopy.refactor2.tcp.TcpMulticastBroadcastTransmitter;
import com.erhannis.lancopy.refactor2.tls.TlsWrapper;
import com.erhannis.mathnstuff.FactoryHashMap;
import com.erhannis.mathnstuff.MeUtils;
import com.erhannis.mathnstuff.Pair;
import fi.iki.elonen.NanoHTTPD;
import static fi.iki.elonen.NanoHTTPD.newChunkedResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import jcsp.helpers.BackpressureRegulator;
import jcsp.helpers.FCClient;
import jcsp.helpers.JcspUtils;
import jcsp.helpers.NameParallel;
import jcsp.helpers.SynchronousSplitter;
import jcsp.helpers.TCServer.TaskItem;
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.AltingChannelInputInt;
import jcsp.lang.AltingChannelOutput;
import jcsp.lang.AltingFCServer;
import jcsp.lang.AltingTCServer;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.Any2OneChannelInt;
import jcsp.lang.CSProcess;
import jcsp.lang.CSTimer;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;
import jcsp.lang.ChannelOutputInt;
import jcsp.lang.DisableableTimer;
import jcsp.lang.Guard;
import jcsp.lang.One2OneChannelSymmetric;
import jcsp.lang.Parallel;
import jcsp.lang.ProcessManager;
import jcsp.util.Buffer;
import jcsp.util.ChannelDataStore;
import jcsp.util.InfiniteBuffer;

/**
 *
 * @author erhannis
 */
public class CommsManager implements CSProcess {

    private static class IncomingTransferState {
        public OutputStream os;
        public final TaskItem<UUID, Pair<String, InputStream>> task;

        //TODO Deal with ordering and retransmission etc., for extra robustness spanning multiple comms
        //TODO Deal with ACK
        
        public IncomingTransferState(OutputStream os, TaskItem<UUID, Pair<String, InputStream>> task) {
            this.os = os;
            this.task = task;
        }
    }

    private static class OutgoingTransferState {
        public final UUID targetId;
        public final String mimeType;
        public final InputStream is;
        public int index = 0;

        //TODO Deal with ordering and retransmission etc., for extra robustness spanning multiple comms
        //TODO Deal with ACK

        public OutgoingTransferState(UUID targetId, String mimeType, InputStream is) {
            this.targetId = targetId;
            this.mimeType = mimeType;
            this.is = is;
        }
    }    
    
    private final DataOwner dataOwner;
    
    public CommsManager(DataOwner dataOwner, ChannelOutput<List<Comm>> lcommsOut, ChannelOutput<Advertisement> radOut, AltingChannelInput<Advertisement> aadIn, ChannelOutput<Summary> rsumOut, AltingChannelInput<Summary> lsumIn, ChannelOutput<Pair<Comm, Boolean>> statusOut, AltingChannelInput<List<Comm>> subscribeIn, ChannelOutputInt showLocalFingerprintOut, AltingChannelInput<CommChannel> enrollCommChannelIn, FCClient<UUID, Summary> summaryClient, FCClient<UUID, Advertisement> aadClient, FCClient<Void, List<Advertisement>> rosterClient, FCClient<Void, Data> ldataClient, AltingTCServer<UUID, Pair<String, InputStream>> dataServer, FCClient<String, Boolean> confirmationClient) {
        this.dataOwner = dataOwner;

        this.lcommsOut = lcommsOut;
        this.radOut = radOut;
        this.aadIn = aadIn;
        this.rsumOut = rsumOut;
        this.lsumIn = lsumIn;
        this.statusOut = statusOut;
        this.subscribeIn = subscribeIn;
        this.showLocalFingerprintOut = showLocalFingerprintOut;
        this.enrollCommChannelIn = enrollCommChannelIn;
        
        this.summaryClient = summaryClient;
        this.aadClient = aadClient;
        this.rosterClient = rosterClient;
        this.ldataClient = ldataClient;
        this.dataServer = dataServer;
        this.confirmationClient = confirmationClient;
        
        Any2OneChannel<Pair<NodeManager.CRToken,byte[]>> internalRxMsgChannel = Channel.<Pair<NodeManager.CRToken,byte[]>> any2one();
        this.internalRxMsgIn = internalRxMsgChannel.in();
        this.internalRxMsgOut = JcspUtils.logDeadlock(internalRxMsgChannel.out());
        
        Any2OneChannel<NodeManager.ChannelReader> internalChannelReaderShuffleAChannel = Channel.<NodeManager.ChannelReader> any2one();
        this.internalChannelReaderShuffleAIn = internalChannelReaderShuffleAChannel.in();
        this.internalChannelReaderShuffleAOut = JcspUtils.logDeadlock(internalChannelReaderShuffleAChannel.out());
        
        Any2OneChannel<Pair<Comm,Boolean>> internalCommStatusChannel = Channel.<Pair<Comm,Boolean>> any2one();
        this.internalCommStatusIn = internalCommStatusChannel.in();
        this.internalCommStatusOut = JcspUtils.logDeadlock(internalCommStatusChannel.out());
        
        Any2OneChannelInt internalShowLocalFingerprintChannel = Channel.any2oneInt();
        this.internalShowLocalFingerprintIn = internalShowLocalFingerprintChannel.in();
        this.internalShowLocalFingerprintOut = JcspUtils.logDeadlock(internalShowLocalFingerprintChannel.out());
        
        this.broadcastMsgSplitter = new SynchronousSplitter<byte[]>();
        this.txMsgSplitter = new SynchronousSplitter<byte[]>();
    }

    private final ChannelOutput<List<Comm>> lcommsOut; //TODO Support changes?  Removals?
    private final ChannelOutput<Advertisement> radOut;
    private final AltingChannelInput<Advertisement> aadIn;
    private final ChannelOutput<Summary> rsumOut;
    private final AltingChannelInput<Summary> lsumIn;
    private final ChannelOutput<Pair<Comm,Boolean>> statusOut;
    private final AltingChannelInput<List<Comm>> subscribeIn;
    private final ChannelOutputInt showLocalFingerprintOut;
    private final AltingChannelInput<CommChannel> enrollCommChannelIn;
    private final AltingChannelInput<Pair<NodeManager.CRToken,byte[]>> internalRxMsgIn;
    private final ChannelOutput<Pair<NodeManager.CRToken,byte[]>> internalRxMsgOut;
    private final AltingChannelInput<NodeManager.ChannelReader> internalChannelReaderShuffleAIn;
    private final ChannelOutput<NodeManager.ChannelReader> internalChannelReaderShuffleAOut;
    private final AltingChannelInput<Pair<Comm,Boolean>> internalCommStatusIn;
    private final ChannelOutput<Pair<Comm,Boolean>> internalCommStatusOut;
    private final AltingChannelInputInt internalShowLocalFingerprintIn;
    private final ChannelOutputInt internalShowLocalFingerprintOut;

    private final SynchronousSplitter<byte[]> broadcastMsgSplitter;
    private final SynchronousSplitter<byte[]> txMsgSplitter;

    private final FCClient<UUID, Summary> summaryClient;
    private final FCClient<UUID, Advertisement> aadClient;
    private final FCClient<Void, List<Advertisement>> rosterClient;
    private final FCClient<Void, Data> ldataClient;
    private final AltingTCServer<UUID, Pair<String, InputStream>> dataServer;    
    private final FCClient<String, Boolean> confirmationClient;

    private HashMap<UUID, NodeManager.NMInterface> nodes = new HashMap<>();
    
    private NodeManager.NMInterface startNodeManager(UUID id, boolean registerToSplitter) {
        // Blehhhhh, this is boilerplatey
        BackpressureRegulator<byte[]> txMsgOut = new BackpressureRegulator<>();
        if (registerToSplitter) {
            this.txMsgSplitter.register(txMsgOut);
        }

        
        Any2OneChannel<NodeManager.CRToken> demandShuffleChannelChannel = Channel.<NodeManager.CRToken> any2one(new InfiniteBuffer<>());
        AltingChannelInput<NodeManager.CRToken> demandShuffleChannelIn = demandShuffleChannelChannel.in();
        ChannelOutput<NodeManager.CRToken> demandShuffleChannelOut = JcspUtils.logDeadlock(demandShuffleChannelChannel.out());
        
        
        Any2OneChannel<NodeManager.ChannelReader> channelReaderShuffleBChannel = Channel.<NodeManager.ChannelReader> any2one(new InfiniteBuffer<>());
        AltingChannelInput<NodeManager.ChannelReader> channelReaderShuffleBIn = channelReaderShuffleBChannel.in();
        ChannelOutput<NodeManager.ChannelReader> channelReaderShuffleBOut = JcspUtils.logDeadlock(channelReaderShuffleBChannel.out());
        
        Any2OneChannel<CommChannel> incomingConnectionChannel = Channel.<CommChannel> any2one(new InfiniteBuffer<>());
        AltingChannelInput<CommChannel> incomingConnectionIn = incomingConnectionChannel.in();
        ChannelOutput<CommChannel> incomingConnectionOut = JcspUtils.logDeadlock(incomingConnectionChannel.out());

        Any2OneChannel<List<Comm>> subscribeChannel = Channel.<List<Comm>> any2one(new InfiniteBuffer<>());
        AltingChannelInput<List<Comm>> subscribeIn = subscribeChannel.in();
        ChannelOutput<List<Comm>> subscribeOut = JcspUtils.logDeadlock(subscribeChannel.out());
        
        
        new ProcessManager(new NodeManager(dataOwner, id, txMsgOut.in, internalRxMsgOut, demandShuffleChannelIn, internalChannelReaderShuffleAOut, channelReaderShuffleBIn, incomingConnectionIn, subscribeIn, internalCommStatusOut, internalShowLocalFingerprintOut)).start();
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
            tbbts.add(new TcpBroadcastBroadcastTransmitter(broadcastAddresses[i], broadcastPort, this.broadcastMsgSplitter.register(new InfiniteBuffer<>())));
        }
        
        //TODO Move these somewhere else?  Abstract?
        new ProcessManager(new NameParallel(new CSProcess[] {
            this.broadcastMsgSplitter,
            this.txMsgSplitter,
            
            () -> { //TODO Name thread
                while (true) {
                    internalCommChannelOut.write(enrollCommChannelIn.read());
                }
            },
            
            // TCP
            () -> {
                Thread.currentThread().setName("TCP server listener");
                //TODO Fallback to "Comms.tcp.enabled"?
                boolean tcpEnabled = (Boolean) dataOwner.options.getOrDefault("Comms.tcp.server_enabled", true);

                if (tcpEnabled) {
                    int port = (int) dataOwner.options.getOrDefault("Comms.tcp.server_port", 0);
                    try {
                        TcpCommChannel.ServerThread st = TcpCommChannel.serverThread(internalCommChannelOut, port);
                        System.out.println("CommsManager.TCP " + dataOwner.ID + " bound to port " + st.boundPort);

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
                        //TODO Note that this is running separately from the CM, and so defies convention a little
                        lcommsOut.write(newComms); //TODO Note that these stack on top of whatever Comms are already in the Ad
                        
                        //TODO Likewise
                        // Also, this is weirdly unrelated to the other stuff in this block
                        try {
                            JmDNSProcess.start(dataOwner, st.boundPort, radOut);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                        
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
            new NameParallel(tbbts.toArray(new CSProcess[0])),
            new TcpMulticastBroadcastReceiver(ipv4MulticastPort, ipv4MulticastAddress, internalRxBroadcastOut), //TODO Ditto
            new TcpMulticastBroadcastTransmitter(ipv4MulticastAddress, ipv4MulticastPort, this.broadcastMsgSplitter.register(new InfiniteBuffer<>())), //TODO Ditto
            new TcpMulticastBroadcastReceiver(ipv6MulticastPort, ipv6MulticastAddress, internalRxBroadcastOut), //TODO Ditto
            new TcpMulticastBroadcastTransmitter(ipv6MulticastAddress, ipv6MulticastPort, this.broadcastMsgSplitter.register(new InfiniteBuffer<>())), //TODO Ditto
            () -> {
                Thread.currentThread().setName("Traditional HTTP, manual url");
                //TODO This is a bit hacky; rebinds port every connection and doesn't accept more than one at a time
                //       And should probably be a class in its own right.
                while (true) {
                    try {
                        //TODO Option changes don't take effect until the next loop, maybe after a new connection attempt, which is bad
                        CSTimer timer = new CSTimer();
                        boolean plainHttpEnabled = (Boolean) dataOwner.options.getOrDefault("Comms.tcp.unauth_http.enabled", false);
                        int plainHttpPort = (int) dataOwner.options.getOrDefault("Comms.tcp.unauth_http.port", 12111);
                        boolean plainHttpRequireCert = (boolean) dataOwner.options.getOrDefault("Comms.tcp.unauth_http.require_cert", false);
                        boolean plainHttpConfirm = (boolean) dataOwner.options.getOrDefault("Comms.tcp.unauth_http.show_confirmation", false);
                        if (plainHttpEnabled) {
                            System.out.println("CMPH opening ssc");
                            ServerSocketChannel ss = ServerSocketChannel.open();
                            
                            try {
                                System.out.println("CMPH binding to " + plainHttpPort);
                                ss.socket().bind(new InetSocketAddress(plainHttpPort));
                                System.out.println("CMPH accepting...");
                                SocketChannel sc = ss.accept();
                                System.out.println("CMPH ...accepted");

                                if (!plainHttpConfirm || confirmationClient.call("Incoming plain http(s) connection.  Accept?")) {
                                    CommChannel channel = new TcpCommChannel(sc);
                                    if (dataOwner.encrypted) {
                                        TlsWrapper tlsWrapper = new TlsWrapper(dataOwner, TlsWrapper.ClientServerMode.SERVER, plainHttpRequireCert, channel, showLocalFingerprintOut);
                                        channel = tlsWrapper;
                                    }

                                    Data data = ldataClient.call(null);
                                    String mimeType = data.getMime(true);
                                    InputStream is = data.serialize(true);
                                    System.out.println("CMPH getting dumbHttpChunks");
                                    InputStream httpChunks = MeUtils.dumbHttpChunks(mimeType, is);

                                    final int N = 0x4000;
                                    byte[] buf = new byte[N];
                                    int read = 0;
                                    int total = 0;
                                    System.out.println("CMPH reading...");
                                    while ((read = httpChunks.read(buf)) >= 0) {
                                        if (read > 0) {
                                            total += read;
                                            //System.out.println("CMPH read " + read + " ; writing...");
                                            //sc.write(ByteBuffer.wrap(buf, 0, read));
                                            channel.write(ByteBuffer.wrap(buf, 0, read));
                                            //System.out.println("CMPH ...wrote");                                        
                                        }
                                        //System.out.println("CMPH reading...");
                                    }
                                    System.out.println("CMPH ...no more ; wrote " + total);

                                    while (true) {
                                        ByteBuffer response = ByteBuffer.allocate(0x10000);
                                        int count = channel.read(response);
                                        if (count >= 0) {
                                            //System.out.println("CMPH rx count " + count);
                                            //System.out.println("CMPH rx msg: " + new String(response.array(), 0, count));
                                        } else {
                                            System.out.println("CMPH rx finished");
                                            break;
                                        }
                                    }

                                    System.out.println("CMPH closing");
                                    httpChunks.close();
                                    channel.close();
                                }
                                sc.close();
                            } finally {
                                timer.sleep(1000);
                                ss.close();
                            }
                        }

                        timer.sleep(500);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        })).start();
        
        
        
        
        
        // Start NM to hold incame connections
        startNodeManager(null, false);
        
        //TODO Add verification to make sure nodes' claims match their TLS credentials
        //TODO Socket timeouts?

        byte[] lastBroadcast = null;
        DisableableTimer rebroadcastTimer = new DisableableTimer();
        long rebroadcastInterval = (long) dataOwner.options.getOrDefault("Advertisers.rebroadcast_interval", 30000L);
        if (rebroadcastInterval < 0) {
            rebroadcastTimer.turnOff();
        } else {
            rebroadcastTimer.setAlarm(rebroadcastTimer.read() + rebroadcastInterval);
        }
        DisableableTimer transferTimer = new DisableableTimer();
        HashMap<UUID, IncomingTransferState> incomingTransfers = new HashMap<>();
        HashMap<UUID, OutgoingTransferState> outgoingTransfers = new HashMap<>();
        Alternative alt = new Alternative(new Guard[]{aadIn, lsumIn, subscribeIn, internalRxMsgIn, internalChannelReaderShuffleAIn, internalCommStatusIn, internalCommChannelIn, internalRxBroadcastIn, internalShowLocalFingerprintIn, dataServer, rebroadcastTimer, transferTimer});
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
                        this.txMsgSplitter.write(msg);
                        break;
                    }
                    case 1: { // lsumIn
                        Summary summary = lsumIn.read();
                        byte[] msg = dataOwner.serialize(logSend(summary));
                        this.txMsgSplitter.write(msg);
                        break;
                    }
                    case 2: { // subscribeIn
                        List<Comm> comms = subscribeIn.read();
                        FactoryHashMap<UUID, ArrayList<Comm>> separated = new FactoryHashMap<UUID, ArrayList<Comm>>((input) -> {
                            return new ArrayList<Comm>();
                        });
                        for (Comm comm : comms) {
                            separated.get(comm.owner == null ? null : comm.owner.id).add(comm);
                        }
                        for (UUID id : separated.keySet()) {
                            if (!nodes.containsKey(id)) {
                                startNodeManager(id, true);
                            }
                            nodes.get(id).subscribeOut.write(separated.get(id));
                            // This might only be necessary on startNodeManager, but for robustness it shouldn't hurt to leave it here
                            nodes.get(id).txMsgOut.write(dataOwner.serialize(logSend(aadClient.call(dataOwner.ID))));
                            nodes.get(id).txMsgOut.write(dataOwner.serialize(logSend(summaryClient.call(dataOwner.ID))));
                            nodes.get(id).txMsgOut.write(dataOwner.serialize(logSend(rosterClient.call(null))));
                        }
                        break;
                    }
                    case 3: { // internalRxMsgIn
                        Pair<NodeManager.CRToken,byte[]> msg = internalRxMsgIn.read();
                        Object o = dataOwner.deserialize(msg.b);
                        System.out.println("CM rx " + o);
                        if (o instanceof IdentificationMessage) {
                            IdentificationMessage im = (IdentificationMessage) o;
                            if (!Objects.equals(im.nodeId, msg.a.nodeId)) {
                                UUID fromId = msg.a.nodeId;
                                // Wrong NodeManager; shuffle to correct
                                msg.a.nodeId = im.nodeId;
                                nodes.get(fromId).demandShuffleChannelOut.write(msg.a);
                            }
                        } else if (o instanceof Advertisement) {
                            int maxAdSize = (Integer) dataOwner.options.getOrDefault("CommsManager.MAX_AD_SIZE", 250000);
                            if (msg.b.length > maxAdSize) {
                                System.err.println("CM rx ad too big: " + msg.b.length + " > " + maxAdSize);
                                //TODO Respond with error?
                            } else {
                                Advertisement ad = (Advertisement) o;
                                radOut.write(ad);
                            }
                        } else if (o instanceof Summary) {
                            int maxSummarySize = (Integer) dataOwner.options.getOrDefault("CommsManager.MAX_SUMMARY_SIZE", 1000);
                            if (msg.b.length > maxSummarySize) {
                                System.err.println("CM rx summary too big: " + msg.b.length + " > " + maxSummarySize);
                                //TODO Respond with error?
                            } else {
                                Summary rsum = (Summary) o;
                                rsumOut.write(rsum);
                            }
                        } else if (o instanceof DataRequestMessage) {
                            DataRequestMessage drm = (DataRequestMessage) o;
                            Data data = ldataClient.call(null);
                            String mimeType = data.getMime(false);
                            InputStream is = data.serialize(false);

                            OutgoingTransferState state = new OutgoingTransferState(msg.a.nodeId, mimeType, is);
                            outgoingTransfers.put(drm.correlationId, state);
                            transferTimer.set(0);
                        } else if (o instanceof DataStartMessage) {
                            //TODO The data messages have byte[], which could be made huge; a DoS attack.  Consider.
                            DataStartMessage dsm = (DataStartMessage) o;
                            IncomingTransferState state = incomingTransfers.get(dsm.correlationId);
                            if (state != null) {
                                if (state.os != null) {
                                    dataOwner.errOnce("CommsManager rx duplicate(?) DataStartMessage " + dsm.correlationId);
                                    //TODO Respond with error?
                                } else {
                                    //TODO Make buffer dynamic?
                                    final PipedInputStream pis = new PipedInputStream(1024*256);
                                    final PipedOutputStream pos;
                                    try {
                                        pos = new PipedOutputStream(pis);
                                    } catch (IOException ex) {
                                        // This should never happen
                                        Logger.getLogger(CommsManager.class.getName()).log(Level.SEVERE, null, ex);
                                        throw new RuntimeException(ex);
                                    }
                                    state.os = pos;
                                    state.task.responseOut.write(Pair.gen(dsm.mimeType, pis));
                                    transferTimer.sleep(1000);
                                }
                                state.os.write(dsm.data);
                                if (dsm.eom) {
                                    System.out.println("CM close os " + dsm.correlationId);
                                    state.os.flush();
                                    state.os.close();
                                    incomingTransfers.remove(dsm.correlationId);
                                }
                                //TODO Clean up after error?
                                //TODO Verify no malicious packets?
                            } else {
                                System.err.println("CommsManager rx unsolicited DataStartMessage " + dsm.correlationId);
                                //TODO Respond with error?
                            }
                        } else if (o instanceof DataChunkMessage) {
                            DataChunkMessage dcm = (DataChunkMessage) o;
                            IncomingTransferState state = incomingTransfers.get(dcm.correlationId);
                            if (state != null) {
                                if (state.os == null) {
                                    dataOwner.errOnce("CommsManager rx DataChunkMessage before start " + dcm.correlationId);
                                    //TODO Respond with error?
                                }
                                state.os.write(dcm.data);
                                if (dcm.eom) {
                                    System.out.println("CM close os " + dcm.correlationId);
                                    state.os.flush();
                                    state.os.close();
                                    incomingTransfers.remove(dcm.correlationId);
                                }
                                //TODO Clean up after error?
                                //TODO Verify no malicious packets?
                            } else {
                                dataOwner.errOnce("CommsManager rx unsolicited DataChunkMessage " + dcm.correlationId);
                                //TODO Respond with error?
                            }
                        } else if (o instanceof List) {
                            if (o instanceof List) {
                                List l = (List) o;
                                
                                int maxAdSize = (Integer) dataOwner.options.getOrDefault("CommsManager.MAX_AD_SIZE", 250000);
                                if (msg.b.length > (maxAdSize * l.size())) { // Not exactly correct, but probably close enough...?
                                    System.err.println("CM rx ad too big: " + msg.b.length + " > " + maxAdSize);
                                    //TODO Respond with error?
                                }
                                //TODO Except they COULD send like 1000 ads, I guess.  Blah, I dunno
                                
                                for (Object a : l) {
                                    if (!(a instanceof Advertisement)) {
                                        throw new IllegalArgumentException("CWS rx list not of Advertisement");
                                    }
                                    radOut.write((Advertisement)a);
                                }
                            }
                        } else {
                            //dataOwner.errOnce("ERR CommsManager got unhandled msg: " + o);
                            System.err.println("ERR CommsManager got unhandled msg: " + o);
                        }
                        break;
                    }
                    case 4: { // internalChannelReaderShuffleAIn
                        NodeManager.ChannelReader cr = internalChannelReaderShuffleAIn.read();
                        UUID id = cr.token.nodeId;
                        if (!nodes.containsKey(id)) {
                            startNodeManager(id, true);
                        }
                        nodes.get(id).channelReaderShuffleBOut.write(cr);
                        // This might only be necessary on startNodeManager, but for robustness it shouldn't hurt to leave it here
                        nodes.get(id).txMsgOut.write(dataOwner.serialize(logSend(aadClient.call(dataOwner.ID))));
                        nodes.get(id).txMsgOut.write(dataOwner.serialize(logSend(summaryClient.call(dataOwner.ID))));
                        nodes.get(id).txMsgOut.write(dataOwner.serialize(logSend(rosterClient.call(null))));
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
                            System.err.println("ERR CommsManager got unhandled broadcast msg: " + o);
                        }
                        break;
                    }
                    case 8: { // internalShowLocalFingerprintIn
                        showLocalFingerprintOut.write(internalShowLocalFingerprintIn.read());
                        break;
                    }
                    case 9: { // dataServer
                        TaskItem<UUID, Pair<String, InputStream>> task = dataServer.read();
                        NodeManager.NMInterface nm = nodes.get(task.val);
                        DataRequestMessage drm = new DataRequestMessage();
                        byte[] msg = dataOwner.serialize(logSend(drm));
                        nm.txMsgOut.write(msg);
                        incomingTransfers.put(drm.correlationId, new IncomingTransferState(null, task));
                        break;
                    }
                    case 10: { // rebroadcastTimer
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
                    case 11: { // transferTimer
                        boolean somethingSent = false;
                        for (Iterator<Entry<UUID, OutgoingTransferState>> iter = outgoingTransfers.entrySet().iterator(); iter.hasNext();) {
                            Entry<UUID, OutgoingTransferState> entry = iter.next();
                            UUID correlationId = entry.getKey();
                            OutgoingTransferState state = entry.getValue();
                            NodeManager.NMInterface nm = nodes.get(state.targetId);
                            if (nm == null) {
                                System.err.println("CM Missing NM?? " + state.targetId);
                                continue;
                            } else {
                                if (!nm.txMsgOut.shouldWrite()) {
                                    System.out.println("CM channel not ready to rx; skipping...");
                                    continue;
                                }
                            }                            
                            
                            int chunkSize = (int) dataOwner.options.getOrDefault("Comms.chunk_size", 1024*16);
                            byte[] data = new byte[chunkSize];
                            int count = state.is.read(data);
                            boolean eom = false;
                            if (count < 0) {
                                eom = true;
                                data = new byte[0];
                                //TODO It'd be nice if we could set eom on the last chunk to contain data....
                            } else if (count != chunkSize) {
                                byte[] newData = new byte[count];
                                System.arraycopy(data, 0, newData, 0, count);
                                data = newData;
                            }
                            
                            byte[] msg;
                            if (state.index == 0) {
                                DataStartMessage dsm = new DataStartMessage(correlationId, state.mimeType, data, eom);
                                System.out.println("CM tx " + dsm);
                                msg = dataOwner.serialize(logSend(dsm));
                            } else {
                                DataChunkMessage dcm = new DataChunkMessage(correlationId, state.index, data, eom);
                                System.out.println("CM tx " + dcm);
                                msg = dataOwner.serialize(logSend(dcm));
                            }
                            nm.txMsgOut.write(msg);
                            somethingSent = true;

                            if (eom) {
                                iter.remove();
                                System.out.println("CM finished tx " + entry.getKey());
                            }
                            
                            state.index++;
                        }
                        if (!somethingSent) {
                            System.out.println("CM nothing ready to tx, delaying...");
                            //TODO Optionize?
                            transferTimer.setAlarm(transferTimer.read() + 100);
                        }
                        if (outgoingTransfers.isEmpty()) {
                            transferTimer.turnOff();
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
    
    private static <T> T logSend(T o) {
        System.out.println("CM sending " + o);
        return o;
    }
}
