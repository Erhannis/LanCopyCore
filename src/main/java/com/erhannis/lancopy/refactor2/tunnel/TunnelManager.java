/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2.tunnel;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.refactor2.NodeManager;
import com.erhannis.lancopy.refactor2.NodeManager.CRToken;
import com.erhannis.lancopy.refactor2.OTSSingle;
import com.erhannis.lancopy.refactor2.OutgoingTransferState;
import com.erhannis.lancopy.refactor2.messages.local.LocalMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelConnectionDataMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelConnectionErrorMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelConnectionRequestMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelConnectionSuccessMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelErrorMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelRequestMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelSuccessMessage;
import com.erhannis.mathnstuff.Pair;
import com.google.common.base.Objects;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import jcsp.helpers.FCClient;
import jcsp.helpers.JcspUtils;
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.AltingChannelOutput;
import jcsp.lang.AltingFCServer;
import jcsp.lang.AltingFunctionChannel;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.CSProcess;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;
import jcsp.lang.Guard;
import jcsp.lang.ProcessManager;
import jcsp.util.InfiniteBuffer;

/*
//WARNING: The tunnel system is particularly unstable.  In particular, I don't expect
it to deal well with errors or streams closing, on purpose or on accident.
It may leave connections on one side open, etc.  Your best bet is to close the
program and reopen it.

Between two nodes, there can exist zero or more tunnels.
Each tunnel has two endpoints: one listener, and one talker.
For a tunnel T, between nodes X and Y, X has one endpoint and Y has the other.
Each tunnel can host multiple connections.
Each endpoint has a UUID.
//THINK Should they, though?  Should the tunnel as a whole have its own id?

...Wait a minute.  I set up this class to permit multiple endpoints, but it only needs...ah, wait, I guess you could forward more than one port, say.

listenerEnd vs talkerEnd
  (though once a connection is established, they both do both)

TunnelRequestMessage
  initiatorTunnelId
  tunnelDirection
  incomingPort // Not strictly necessary on some messages, but might be useful
  outgoingAddress
  outgoingPort
  protocol
TunnelSuccessMessage
  initiatorTunnelId
  responseTunnelId
TunnelErrorMessage
  tunnelId
  message
TunnelConnectionRequestMessage
  connectionId
  targetTunnelId
TunnelConnectionSuccessMessage
  connectionId
TunnelConnectionErrorMessage
  connectionId
  message
TunnelConnectionDataMessage
  connectionId
  index
  data

*/

/**
 * Assumes all incoming messages have been verified to come from the node to which this Manager is assigned.
 * @author erhannis
 */
public class TunnelManager implements CSProcess {
    private static abstract class Endpoint2ManagerMessage {
        public final UUID endpointId; //THINK I'm not sure what this endpoint means.  Local, or remote, or irrelevant?

        public Endpoint2ManagerMessage(UUID endpointId) {
            this.endpointId = endpointId;
        }
    }
    
    private static class E2MConnectMessage extends Endpoint2ManagerMessage {
        public final UUID connectionId;
        
        public E2MConnectMessage(UUID endpointId, UUID connectionId) {
            super(endpointId);
            this.connectionId = connectionId;
        }
    }

    private static class E2MDataMessage extends Endpoint2ManagerMessage {
        public final UUID connectionId;
        public final byte[] data;

        public E2MDataMessage(UUID endpointId, UUID connectionId, byte[] data) {
            super(endpointId);
            this.connectionId = connectionId;
            this.data = data;
        }
    }

    private static class E2MErrorMessage extends Endpoint2ManagerMessage {
        public final UUID connectionId;
        
        public E2MErrorMessage(UUID endpointId, UUID connectionId) {
            super(endpointId);
            this.connectionId = connectionId;
        }        
    }
    
    //RAINY Implement shutdown
    public static abstract class Endpoint implements CSProcess {
        public final DataOwner dataOwner;
        public final UUID thisId;
        public final UUID pairId;
        public final ChannelOutput<Endpoint2ManagerMessage> rxDataOut; // Connection id, data
        public final AltingChannelInput<Pair<UUID, byte[]>> txDataIn; // Connection id, data

        public Endpoint(DataOwner dataOwner, UUID thisId, UUID pairId, ChannelOutput<Endpoint2ManagerMessage> rxDataOut, AltingChannelInput<Pair<UUID, byte[]>> txDataIn) {
            this.dataOwner = dataOwner;
            this.thisId = thisId;
            this.pairId = pairId;
            this.rxDataOut = rxDataOut;
            this.txDataIn = txDataIn;
        }
        
        void handleConnection(UUID conId, Socket clientSocket, AltingChannelInput<byte[]> internalTxDataIn) {
            //RAINY Do something with socket info?
            System.out.println("TM.EP "+thisId+"_"+conId+" handleConnection "+clientSocket);
            try {
                ProcessManager talker = new ProcessManager(() -> {
                    try (OutputStream out = clientSocket.getOutputStream()) {

                        while (true) {
                            byte[] data = internalTxDataIn.read();
                            //System.out.println("TM.EP "+thisId+"_"+conId+" write: "+new String(data, "US-ASCII"));
                            out.write(data);
                            out.flush();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        //CHECK Should I close the connection or something?
                    }
                    System.out.println("TM.EP "+thisId+"_"+conId+" talker ended");
                });

                int bufferSize = (int) dataOwner.options.getOrDefault("Comms.tunnels.chunk_size", 1024*16);
                
                ProcessManager listener = new ProcessManager(() -> {
                    //rxDataOut.write(Pair.gen(conId, new byte[0])); //THINK
                    try (InputStream in = clientSocket.getInputStream()) {

                       byte[] buffer = new byte[bufferSize];
                       int bytesRead;
                       while ((bytesRead = in.read(buffer)) != -1) {
                            //System.out.println(prefix+new String(buffer, 0, bytesRead, Charset.forName("ISO-8859-1")));
                            byte[] bout = new byte[bytesRead];
                            System.arraycopy(buffer, 0, bout, 0, bytesRead);
                            //System.out.println("TM.EP "+thisId+"_"+conId+" read: "+new String(bout, "US-ASCII"));
                            rxDataOut.write(new E2MDataMessage(thisId, conId, bout));
                        }
                    } catch (IOException e) {
                       e.printStackTrace();
                    }
                    System.out.println("TM.EP "+thisId+"_"+conId+" listener ended");
                });

                talker.start();
                listener.start();
                                
                talker.join(); //MISC Not sure if this is where this should go
                listener.join();
                
                System.out.println("TM.EP "+thisId+"_"+conId+" both ended");

                clientSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            rxDataOut.write(new E2MErrorMessage(thisId, conId));
        }

        AltingFunctionChannel<UUID, AltingChannelInput<byte[]>> getTxDataInChannelCall = new AltingFunctionChannel<>(true);

        @Override
        public void run() {
            new ProcessManager(() -> {
                HashMap<UUID, ChannelOutput<byte[]>> internalTxChannels = new HashMap<>();

                AltingFCServer<UUID, AltingChannelInput<byte[]>> getTxDataInChannelServer = getTxDataInChannelCall.getServer();
                Alternative alt = new Alternative(new Guard[]{getTxDataInChannelServer, txDataIn});
                while (true) {
                    try {
                        switch (alt.priSelect()) {
                            case 0: { // getTxDataInChannelServer
                                UUID id = getTxDataInChannelServer.startRead();
                                try {
                                    if (internalTxChannels.containsKey(id)) {
                                        System.err.println("ERR Request for new internal channel with ID that already exists: "+id+ " (on "+thisId+")");
                                        getTxDataInChannelServer.endRead(null);
                                        break;
                                    }
                                    Any2OneChannel<byte[]> internalTxDataChannel = Channel.<byte[]> any2one(new InfiniteBuffer<>());
                                    AltingChannelInput<byte[]> internalTxDataIn = internalTxDataChannel.in();
                                    ChannelOutput<byte[]> internalTxDataOut = JcspUtils.logDeadlock(internalTxDataChannel.out());
                                    internalTxChannels.put(id, internalTxDataOut);
                                    getTxDataInChannelServer.endRead(internalTxDataIn);
                                } catch (Throwable t) {
                                    getTxDataInChannelServer.endRead(null);
                                    throw t;
                                }
                                break;
                            }
                            case 1: { // txDataIn
                                Pair<UUID, byte[]> rq = txDataIn.read();
                                ChannelOutput<byte[]> internalTxDataOut = internalTxChannels.get(rq.a);
                                if (internalTxDataOut != null) {
                                    internalTxDataOut.write(rq.b);
                                } else {
                                    System.err.println("ERR Attempt to send on nonexistent connection: "+rq.a+ " (on "+thisId+")");
                                    //CHECK Should I return error somehow??
                                }
                                break;
                            }
                        }
                    } catch (Throwable t) {
                        System.err.println("TM.EP controller got error; continuing " + this.thisId);
                        t.printStackTrace();
                    }
                }
            }).start();
        }
    }
    
    public static class ListenerEndpoint extends Endpoint {
        public final int listeningPort;

        public ListenerEndpoint(DataOwner dataOwner, UUID thisId, UUID pairId, int listeningPort, AltingChannelInput<Pair<UUID, byte[]>> txDataIn, ChannelOutput<Endpoint2ManagerMessage> rxDataOut) {
            super(dataOwner, thisId, pairId, rxDataOut, txDataIn);
            this.listeningPort = listeningPort;
            System.out.println("TM.LE "+thisId+" created ("+listeningPort+")");
        }
        
        @Override
        public void run() {
            super.run();
            while (true) {
                try (ServerSocket serverSocket = new ServerSocket(listeningPort)) {
                    System.out.println("TM.LE "+thisId+" Listening on port " + listeningPort);

                    FCClient<UUID, AltingChannelInput<byte[]>> getTxDataInChannelClient = getTxDataInChannelCall.getClient();
                    while (true) {
                        Socket clientSocket = serverSocket.accept();
                        UUID conId = UUID.randomUUID();
                        System.out.println("TM.LE "+thisId+" RX connection "+conId+" on port " + listeningPort + " ; " + clientSocket);
                        AltingChannelInput<byte[]> internalTxDataIn = getTxDataInChannelClient.call(conId);
                        rxDataOut.write(new E2MConnectMessage(pairId, conId));
                        new ProcessManager(() -> handleConnection(conId, clientSocket, internalTxDataIn)).start();
                    }
                } catch (Throwable t) {
                    //THINK Should I report error?
                    //rxDataOut.write(new E2MErrorMessage(thisId, null));
                    System.err.println("LE got error; continuing " + this.thisId);
                    t.printStackTrace();
                }
            }
        }
    }
    
    public static class TalkerEndpoint extends Endpoint {
        public final String talkingAddress;
        public final int talkingPort;

        public TalkerEndpoint(DataOwner dataOwner, UUID thisId, UUID pairId, String talkingAddress, int talkingPort, AltingChannelInput<Pair<UUID, byte[]>> txDataIn, ChannelOutput<Endpoint2ManagerMessage> rxDataOut) {
            super(dataOwner, thisId, pairId, rxDataOut, txDataIn);
            this.talkingAddress = talkingAddress;
            this.talkingPort = talkingPort;
            System.out.println("TM.TE "+thisId+" created (" + talkingAddress + ":" + talkingPort + ")");
        }
        
        public void openConnection(UUID conId) throws IOException {
            Socket targetSocket = new Socket(talkingAddress, talkingPort);
            System.out.println("TM.TE "+thisId+" Connected to " + talkingAddress + ":" + talkingPort);

            FCClient<UUID, AltingChannelInput<byte[]>> getTxDataInChannelClient = getTxDataInChannelCall.getClient();

            System.out.println("TM.TE "+thisId+" TX connection "+conId+" on address "+talkingAddress+":"+talkingPort+" ; "+targetSocket);
            AltingChannelInput<byte[]> internalTxDataIn = getTxDataInChannelClient.call(conId);
            if (internalTxDataIn == null) {
                throw new IOException("Duplicate connection ID");
            }
            new ProcessManager(() -> handleConnection(conId, targetSocket, internalTxDataIn)).start();
        }
        
        @Override
        public void run() {
            super.run();
        }
    }
    
    // Class doesn't use this for checking, only for e.g. logging
    public final UUID nodeId;
    public final DataOwner dataOwner;
        
    // private final AltingChannelInput<Pair<UUID, byte[]>> internalTxDataIn; // ...give to endpoints?
    private final HashMap<UUID, ChannelOutput<Pair<UUID, byte[]>>> internalTxDataOuts = new HashMap<>(); // EndpointId -> channel(connection, data). In loop, send data to an endpoint
    private final AltingChannelInput<Endpoint2ManagerMessage> internalRxDataIn; // (connection, data) put on loop
    private final ChannelOutput<Endpoint2ManagerMessage> internalRxDataOut; // (connection, data) give to endpoints
    
    public AltingFCServer<LocalMessage,Boolean> localHandlerServer;
    public AltingFCServer<Pair<CRToken,Object>,Boolean> handlerServer;
    public ChannelOutput<OutgoingTransferState> txOtsOut;
    public FCClient<String, Boolean> confirmationClient;

    public HashMap<UUID, TunnelRequestMessage> pendingTunnels = new HashMap<>();
    public HashMap<UUID, UUID> endpointToLocal = new HashMap<>(); // any endpoint id -> associated local endpoint id //CHECK Normalize all endpoint uses
    public HashMap<UUID, Endpoint> localEndpoints = new HashMap<>();
    public HashMap<UUID, Endpoint> connectionsToEndpoints = new HashMap<>();

    private UUID endpointToRemote(UUID id) {
        UUID r = endpointToLocal.get(id);
        if (!Objects.equal(id, r)) {
            // `id` mapped to different local id, so `id` is remote
            return id;
        }
        // `id` is local or unrecorded
        // Find an id that maps to local `id`
        //LEAK Kinda heavy
        for (Map.Entry<UUID, UUID> e : endpointToLocal.entrySet()) {
            if (Objects.equal(id, e.getValue()) && !Objects.equal(id, e.getKey())) {
                // Found a key remote id that maps to local `id`
                return e.getKey();
            }
        }
        return null;
    }
    
    public TunnelManager(DataOwner dataOwner, UUID nodeId, AltingFCServer<LocalMessage,Boolean> localHandlerServer, AltingFCServer<Pair<CRToken,Object>,Boolean> handlerServer, ChannelOutput<OutgoingTransferState> txOtsOut, FCClient<String, Boolean> confirmationClient) {
        this.dataOwner = dataOwner;
        this.nodeId = nodeId;
        this.localHandlerServer = localHandlerServer;
        this.handlerServer = handlerServer;
        this.txOtsOut = txOtsOut;
        this.confirmationClient = confirmationClient;
                
        Any2OneChannel<Endpoint2ManagerMessage> internalRxDataChannel = Channel.<Endpoint2ManagerMessage> any2one(new InfiniteBuffer<>());
        this.internalRxDataIn = internalRxDataChannel.in();
        this.internalRxDataOut = JcspUtils.logDeadlock(internalRxDataChannel.out());
    }
    
    /**
     * Not thread safe.
     * @param msg
     * @return 
     */
    private boolean handleMessage(Pair<NodeManager.CRToken, Object> msg) {
        /*
        nodes.get(id).txMsgOut.write(dataOwner.serialize(logSend(aadClient.call(dataOwner.ID))));
        nm.txMsgOut.shouldWrite()
        */
        CRToken token = msg.a;
        Object m0 = msg.b;
        if (m0 instanceof TunnelRequestMessage) {
            TunnelRequestMessage m = (TunnelRequestMessage)m0;
            if (endpointToLocal.containsKey(m.initiatorTunnelId)) {
                txOtsOut.write(new OTSSingle(UUID.randomUUID(), token.nodeId, new TunnelErrorMessage(m.initiatorTunnelId, "ID duplicate")));
                return false;
            } else {
                if (confirmationClient.call("Request from "+token.nodeId+" to create tunnel "+m+".  Permit?")) {
                    //THINK correlationId?
                    UUID responseTunnelId = UUID.randomUUID();
                    endpointToLocal.put(m.initiatorTunnelId, responseTunnelId);
                    endpointToLocal.put(responseTunnelId, responseTunnelId);

                    Endpoint ep;
                    // Other is source, we are target
                    switch (m.tunnelDirection) {
                        case LISTEN_AT_TARGET: {
                            // Listen here
                            Any2OneChannel<Pair<UUID, byte[]>> internalTxDataChannel = Channel.<Pair<UUID, byte[]>> any2one(new InfiniteBuffer<>());
                            AltingChannelInput<Pair<UUID, byte[]>> internalTxDataIn = internalTxDataChannel.in();
                            internalTxDataOuts.put(responseTunnelId, JcspUtils.logDeadlock(internalTxDataChannel.out()));
                            ep = new ListenerEndpoint(dataOwner, responseTunnelId, m.initiatorTunnelId, m.incomingPort, internalTxDataIn, internalRxDataOut);
                            localEndpoints.put(responseTunnelId, ep);
                            break;
                        }
                        case TALK_FROM_TARGET: {
                            // Talk here
                            Any2OneChannel<Pair<UUID, byte[]>> internalTxDataChannel = Channel.<Pair<UUID, byte[]>> any2one(new InfiniteBuffer<>());
                            AltingChannelInput<Pair<UUID, byte[]>> internalTxDataIn = internalTxDataChannel.in();
                            internalTxDataOuts.put(responseTunnelId, JcspUtils.logDeadlock(internalTxDataChannel.out()));
                            ep = new TalkerEndpoint(dataOwner, responseTunnelId, m.initiatorTunnelId, m.outgoingAddress, m.outgoingPort, internalTxDataIn, internalRxDataOut);
                            localEndpoints.put(responseTunnelId, ep);
                            break;
                        }
                        default: {
                            throw new RuntimeException("Unhandled tunnelDirection");
                        }
                    }
                    new ProcessManager(ep).start();
                    // Map both IDs to our response endpoint id
                    endpointToLocal.put(m.initiatorTunnelId, responseTunnelId);
                    endpointToLocal.put(responseTunnelId, responseTunnelId);

                    txOtsOut.write(new OTSSingle(UUID.randomUUID(), token.nodeId, new TunnelSuccessMessage(m.initiatorTunnelId, responseTunnelId)));
                    return true;
                } else {
                    txOtsOut.write(new OTSSingle(UUID.randomUUID(), token.nodeId, new TunnelErrorMessage(m.initiatorTunnelId, "Denied")));
                    return true;
                }
            }
        } else if (m0 instanceof TunnelSuccessMessage) {
            TunnelSuccessMessage m = (TunnelSuccessMessage)m0;
            TunnelRequestMessage r = pendingTunnels.get(m.initiatorTunnelId);
            if (r != null) {
                Endpoint ep;
                // Other is target, we are source
                switch (r.tunnelDirection) {
                    case LISTEN_AT_TARGET: {
                        // Talk here
                        Any2OneChannel<Pair<UUID, byte[]>> internalTxDataChannel = Channel.<Pair<UUID, byte[]>> any2one(new InfiniteBuffer<>());
                        AltingChannelInput<Pair<UUID, byte[]>> internalTxDataIn = internalTxDataChannel.in();
                        internalTxDataOuts.put(r.initiatorTunnelId, JcspUtils.logDeadlock(internalTxDataChannel.out()));
                        ep = new TalkerEndpoint(dataOwner, r.initiatorTunnelId, m.responseTunnelId, r.outgoingAddress, r.outgoingPort, internalTxDataIn, internalRxDataOut);
                        localEndpoints.put(r.initiatorTunnelId, ep);
                        break;
                    }
                    case TALK_FROM_TARGET: {
                        // Listen here
                        Any2OneChannel<Pair<UUID, byte[]>> internalTxDataChannel = Channel.<Pair<UUID, byte[]>> any2one(new InfiniteBuffer<>());
                        AltingChannelInput<Pair<UUID, byte[]>> internalTxDataIn = internalTxDataChannel.in();
                        internalTxDataOuts.put(r.initiatorTunnelId, JcspUtils.logDeadlock(internalTxDataChannel.out()));
                        ep = new ListenerEndpoint(dataOwner, r.initiatorTunnelId, m.responseTunnelId, r.incomingPort, internalTxDataIn, internalRxDataOut);
                        localEndpoints.put(r.initiatorTunnelId, ep);
                        break;
                    }
                    default: {
                        throw new RuntimeException("Unhandled tunnelDirection");
                    }
                }
                new ProcessManager(ep).start();
                //NOTE Whatever we add here, also add in the TunnelRequestMessage handler, above
                // Map both IDs to our initial endpoint id
                endpointToLocal.put(m.responseTunnelId, r.initiatorTunnelId);
                endpointToLocal.put(r.initiatorTunnelId, r.initiatorTunnelId);
                //asdf;
            } else {
                //THINK Not clear which endpoint ID to use
                txOtsOut.write(new OTSSingle(UUID.randomUUID(), token.nodeId, new TunnelErrorMessage(m.initiatorTunnelId, "No such request present")));
            }
            return true;
        } else if (m0 instanceof TunnelErrorMessage) {
            TunnelErrorMessage m = (TunnelErrorMessage)m0;
            System.err.println("Tunnel error: "+m); //THINK Maybe not syserr
            pendingTunnels.remove(((TunnelErrorMessage) m0).tunnelId);
            UUID l = endpointToLocal.get(((TunnelErrorMessage) m0).tunnelId);
            UUID r = endpointToRemote(((TunnelErrorMessage) m0).tunnelId);
            Endpoint ep = localEndpoints.remove(l);
            endpointToLocal.remove(l);
            endpointToLocal.remove(r);
            //ep.close(); //RAINY close
            //cleanUpConnection();
            //RAINY Remove all connections from connectionsToEndpoints, and other cleanup
            return true;
        } else if (m0 instanceof TunnelConnectionRequestMessage) {
            TunnelConnectionRequestMessage m = (TunnelConnectionRequestMessage)m0;
            try {
                System.out.println("Tunnel connection request: " + m);
                Endpoint ep = localEndpoints.get(endpointToLocal.get(m.targetTunnelId));
                if (ep == null) {
                    txOtsOut.write(new OTSSingle(UUID.randomUUID(), token.nodeId, new TunnelConnectionErrorMessage(m.connectionId, "No such endpoint present")));
                    return true;
                }
                if (connectionsToEndpoints.get(m.connectionId) != null) {
                    //THINK If the connection ID already exists, should we error or should we report success?
                    //THINK If we error, should we close this side of the connection?  We're liable to end up desynchronized.
                    txOtsOut.write(new OTSSingle(UUID.randomUUID(), token.nodeId, new TunnelConnectionErrorMessage(m.connectionId, "Connection ID already exists")));
                    return true;
                }
                if (!(ep instanceof TalkerEndpoint)) {
                    txOtsOut.write(new OTSSingle(UUID.randomUUID(), token.nodeId, new TunnelConnectionErrorMessage(m.connectionId, "Endpoint is not a talker (presumably a listener)")));
                    return true;
                }
                TalkerEndpoint tep = (TalkerEndpoint)ep;
                tep.openConnection(m.connectionId);
                connectionsToEndpoints.put(m.connectionId, ep);
                txOtsOut.write(new OTSSingle(UUID.randomUUID(), token.nodeId, new TunnelConnectionSuccessMessage(m.connectionId)));
            } catch (Exception e) {
                e.printStackTrace();
                txOtsOut.write(new OTSSingle(UUID.randomUUID(), token.nodeId, new TunnelConnectionErrorMessage(m.connectionId, "Error opening connection")));
            }
            return true;
        } else if (m0 instanceof TunnelConnectionSuccessMessage) {
            TunnelConnectionSuccessMessage m = (TunnelConnectionSuccessMessage)m0;
            //THINK Should this even be a thing?  Or should a connection be assumed until proven otherwise?
            //        Is there anything that actually needs done here?
            return true;
        } else if (m0 instanceof TunnelConnectionErrorMessage) {
            TunnelConnectionErrorMessage m = (TunnelConnectionErrorMessage)m0;
            cleanUpConnection(m.connectionId);
            return true;
        } else if (m0 instanceof TunnelConnectionDataMessage) {
            TunnelConnectionDataMessage m = (TunnelConnectionDataMessage)m0;
            System.out.println("TM rx TCDM " + m);
            try {
                UUID epid = connectionsToEndpoints.get(m.connectionId).thisId;
                //THINK Maybe check index or something?
                internalTxDataOuts.get(epid).write(Pair.gen(m.connectionId, m.data));
            } catch (Exception e) {
                //RAINY Send error or something, close channels
                throw e;
            }
            //THINK Anything else, here?
            return true;
        } else {
            // Unhandled
            return false;
        }
    }
    
    private void cleanUpConnection(UUID connectionId) {
        //RAINY Should probably do something, here.
    }
    
    private boolean handleLocalMessage(Object msg) {
        if (msg instanceof LocalMessage) {
            msg = ((LocalMessage)msg).payload;
        }
        if (msg instanceof TunnelRequestMessage) {
            TunnelRequestMessage m = (TunnelRequestMessage)msg;
            pendingTunnels.put(m.initiatorTunnelId, m);
            initiateTunnelRequest(m);
            return true;
        } else {
            return false;
        }
    }
    
    private void initiateTunnelRequest(TunnelRequestMessage r) {
        pendingTunnels.put(nodeId, r);
        txOtsOut.write(new OTSSingle(UUID.randomUUID(), nodeId, r));
    }

    private int idx = 0;
    
    @Override
    public void run() {
        Alternative alt = new Alternative(new Guard[]{localHandlerServer, handlerServer, internalRxDataIn});
        while (true) {
            try {
                switch (alt.priSelect()) {
                    case 0: { // localHandlerServer
                        Object msg = localHandlerServer.startRead();
                        boolean result = false;
                        try {
                            result = handleLocalMessage(msg);
                        } finally {
                            localHandlerServer.endRead(result);
                        }
                        //RAINY Handle shutdown?
                        break;
                    }
                    case 1: { // handlerServer
                        Pair<CRToken, Object> msg = handlerServer.startRead();
                        boolean result = false;
                        try {
                            result = handleMessage(msg);
                        } finally {
                            handlerServer.endRead(result);
                        }
                        break;
                    }
                    case 2: { // internalRxDataIn
                        Endpoint2ManagerMessage msg0 = internalRxDataIn.read();
                        if (msg0 instanceof E2MConnectMessage) {
                            E2MConnectMessage msg = (E2MConnectMessage) msg0;
                            connectionsToEndpoints.put(msg.connectionId, localEndpoints.get(endpointToLocal.get(msg.endpointId)));
                            txOtsOut.write(new OTSSingle(UUID.randomUUID(), this.nodeId, new TunnelConnectionRequestMessage(msg.connectionId, msg.endpointId)));
                        } else if (msg0 instanceof E2MDataMessage) {
                            E2MDataMessage msg = (E2MDataMessage) msg0;
                            //RAINY index
                            txOtsOut.write(new OTSSingle(UUID.randomUUID(), this.nodeId, new TunnelConnectionDataMessage(idx++, msg.data, msg.connectionId)));
                        } else if (msg0 instanceof E2MErrorMessage) {
                            E2MErrorMessage msg = (E2MErrorMessage) msg0;
                            if (msg.connectionId == null) {
                                System.err.println("Got general endpoint error: "+msg);
                                //RAINY //THINK What to do here?  Clean things up?  Restart?
                            } else {
                                txOtsOut.write(new OTSSingle(UUID.randomUUID(), this.nodeId, new TunnelConnectionErrorMessage(msg.connectionId, "Connection error (closed?)")));
                                cleanUpConnection(msg.connectionId);
                            }
                        } else {
                            System.err.println("Got unhandled E2M message: "+msg0);
                        }
                        break;
                    }
                }
            } catch (Throwable t) {
                System.err.println("TunnelManager got error; continuing");
                t.printStackTrace();
                //RAINY Use and deal with poison?
            }
        }
    }
}
