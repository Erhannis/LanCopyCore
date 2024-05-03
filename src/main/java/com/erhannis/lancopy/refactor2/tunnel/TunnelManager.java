/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2.tunnel;

import com.erhannis.lancopy.refactor2.MessageHandler;
import com.erhannis.lancopy.refactor2.NodeManager;
import com.erhannis.lancopy.refactor2.NodeManager.CRToken;
import com.erhannis.lancopy.refactor2.OTSSingle;
import com.erhannis.lancopy.refactor2.OutgoingTransferState;
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
Between two nodes, there can exist a tunnel.
Each tunnel has two endpoints: one listener, and one talker.
For a tunnel T, between nodes X and Y, X has one endpoint and Y has the other.
Each tunnel can host multiple connections.
Each endpoint has a UUID.
//THINK Should they, though?  Should the tunnel as a whole have its own id?

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
    //DUMMY I'm really not sure these shut down properly, or at all
    public static abstract class Endpoint implements CSProcess {
        public final UUID thisId;
        public final UUID pairId;
        public final ChannelOutput<Pair<UUID, byte[]>> rxDataOut; // Connection id, data
        public final AltingChannelInput<Pair<UUID, byte[]>> txDataIn; // Connection id, data

        public Endpoint(UUID thisId, UUID pairId, ChannelOutput<Pair<UUID, byte[]>> rxDataOut, AltingChannelInput<Pair<UUID, byte[]>> txDataIn) {
            this.thisId = thisId;
            this.pairId = pairId;
            this.rxDataOut = rxDataOut;
            this.txDataIn = txDataIn;
        }
        
        void handleConnection(UUID conId, Socket clientSocket, AltingChannelInput<byte[]> internalTxDataIn) {
            //RAINY Do something with socket info?
            try {
                ProcessManager talker = new ProcessManager(() -> {
                    try (OutputStream out = clientSocket.getOutputStream()) {

                        while (true) {
                            byte[] data = internalTxDataIn.read();
                            out.write(data);
                            out.flush();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        //CHECK Should I close the connection or something?
                    }
                });
                
                ProcessManager listener = new ProcessManager(() -> {
                    //rxDataOut.write(Pair.gen(conId, new byte[0])); //THINK
                    try (InputStream in = clientSocket.getInputStream()) {

                       byte[] buffer = new byte[256];
                       int bytesRead;
                       while ((bytesRead = in.read(buffer)) != -1) {
                            //System.out.println(prefix+new String(buffer, 0, bytesRead, Charset.forName("ISO-8859-1")));
                            byte[] bout = new byte[bytesRead];
                            System.arraycopy(buffer, 0, bout, 0, bytesRead);
                            rxDataOut.write(Pair.gen(conId, bout));
                        }
                    } catch (IOException e) {
                       e.printStackTrace();
                    }
                });

                talker.start();
                listener.start();
                                
                talker.join(); //MISC Not sure if this is where this should go
                listener.join();
                
                clientSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            rxDataOut.write(null); //DUMMY Handle
        }

        AltingFunctionChannel<UUID, AltingChannelInput<byte[]>> getTxDataInChannelCall = new AltingFunctionChannel<>(true);

        @Override
        public void run() {
            new ProcessManager(() -> {
                HashMap<UUID, ChannelOutput<byte[]>> internalTxChannels = new HashMap<>();

                AltingFCServer<UUID, AltingChannelInput<byte[]>> getTxDataInChannelServer = getTxDataInChannelCall.getServer();
                Alternative alt = new Alternative(new Guard[]{getTxDataInChannelServer, txDataIn});
                while (true) {
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
                    //DUMMY Error handling
                }
            }).start();
        }
    }
    
    public static class ListenerEndpoint extends Endpoint {
        public final int listeningPort;

        public ListenerEndpoint(UUID thisId, UUID pairId, int listeningPort, AltingChannelInput<Pair<UUID, byte[]>> txDataIn, ChannelOutput<Pair<UUID, byte[]>> rxDataOut) {
            super(thisId, pairId, rxDataOut, txDataIn);
            this.listeningPort = listeningPort;
        }
        
        @Override
        public void run() {
            super.run();
            try (ServerSocket serverSocket = new ServerSocket(listeningPort)) {
                System.out.println("TM.LE "+thisId+" Listening on port " + listeningPort);
                
                FCClient<UUID, AltingChannelInput<byte[]>> getTxDataInChannelClient = getTxDataInChannelCall.getClient();
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    UUID conId = UUID.randomUUID();
                    System.out.println("TM.LE "+thisId+" RX connection "+conId+" on port " + listeningPort + " ; " + clientSocket);
                    AltingChannelInput<byte[]> internalTxDataIn = getTxDataInChannelClient.call(conId);
                    new ProcessManager(() -> handleConnection(conId, clientSocket, internalTxDataIn)).start();
                }
            } catch (Throwable t) {
                //CHECK Should I restart the endpoint or st?
                throw new RuntimeException(t);
            }
        }
    }
    
    public static class TalkerEndpoint extends Endpoint {
        public final String talkingAddress;
        public final int talkingPort;

        public TalkerEndpoint(UUID thisId, UUID pairId, String talkingAddress, int talkingPort, AltingChannelInput<Pair<UUID, byte[]>> txDataIn, ChannelOutput<Pair<UUID, byte[]>> rxDataOut) {
            super(thisId, pairId, rxDataOut, txDataIn);
            this.talkingAddress = talkingAddress;
            this.talkingPort = talkingPort;
        }
        
        public void openConnection(UUID conId) throws IOException {
            try (Socket targetSocket = new Socket(talkingAddress, talkingPort)) {
                System.out.println("TM.TE "+thisId+" Connected to " + talkingAddress + ":" + talkingPort);

                FCClient<UUID, AltingChannelInput<byte[]>> getTxDataInChannelClient = getTxDataInChannelCall.getClient();

                System.out.println("TM.TE "+thisId+" TX connection "+conId+" on address "+talkingAddress+":"+talkingPort+" ; "+targetSocket);
                AltingChannelInput<byte[]> internalTxDataIn = getTxDataInChannelClient.call(conId);
                if (internalTxDataIn == null) {
                    throw new IOException("Duplicate connection ID");
                }
                new ProcessManager(() -> handleConnection(conId, targetSocket, internalTxDataIn)).start();
            }
        }
        
        @Override
        public void run() {
            super.run();
        }
    }
    
    // Class doesn't use this for checking, only for e.g. logging
    public final UUID nodeId;
    
    // private final AltingChannelInput<Pair<UUID, byte[]>> internalTxDataIn; // ...give to endpoints?
    private HashMap<UUID, ChannelOutput<Pair<UUID, byte[]>>> internalTxDataOuts; // EndpointId -> channel(connection, data). In loop, send data to an endpoint
    private final AltingChannelInput<Pair<UUID, byte[]>> internalRxDataIn; // (connection, data) put on loop //NEXT Send connection request on first message, I guess
    private final ChannelOutput<Pair<UUID, byte[]>> internalRxDataOut; // (connection, data) give to endpoints
    
    public AltingChannelInput<Pair<NodeManager.CRToken, Object>> messageIn;
    public ChannelOutput<OutgoingTransferState> txOtsOut;
    public FCClient<String, Boolean> confirmationClient;

    public HashMap<UUID, TunnelRequestMessage> pendingTunnels = new HashMap<>();
    public HashMap<UUID, UUID> endpointToLocal = new HashMap<>(); // any endpoint id -> associated local endpoint id //NEXT Normalize all endpoint uses
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
    
    public TunnelManager(UUID nodeId, AltingChannelInput<Pair<NodeManager.CRToken, Object>> messageIn, ChannelOutput<OutgoingTransferState> txOtsOut, FCClient<String, Boolean> confirmationClient) {
        this.nodeId = nodeId;
        this.messageIn = messageIn;
        this.txOtsOut = txOtsOut;
        this.confirmationClient = confirmationClient;

        Any2OneChannel<Pair<UUID, byte[]>> internalRxDataChannel = Channel.<Pair<UUID, byte[]>> any2one(new InfiniteBuffer<>());
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
                            ep = new ListenerEndpoint(m.initiatorTunnelId, responseTunnelId, m.incomingPort, internalTxDataIn, internalRxDataOut);
                            localEndpoints.put(responseTunnelId, ep);
                            break;
                        }
                        case TALK_FROM_TARGET: {
                            // Talk here
                            Any2OneChannel<Pair<UUID, byte[]>> internalTxDataChannel = Channel.<Pair<UUID, byte[]>> any2one(new InfiniteBuffer<>());
                            AltingChannelInput<Pair<UUID, byte[]>> internalTxDataIn = internalTxDataChannel.in();
                            internalTxDataOuts.put(responseTunnelId, JcspUtils.logDeadlock(internalTxDataChannel.out()));
                            ep = new TalkerEndpoint(m.initiatorTunnelId, responseTunnelId, m.outgoingAddress, m.outgoingPort, internalTxDataIn, internalRxDataOut);
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
                        ep = new TalkerEndpoint(r.initiatorTunnelId, m.responseTunnelId, r.outgoingAddress, r.outgoingPort, internalTxDataIn, internalRxDataOut);
                        localEndpoints.put(r.initiatorTunnelId, ep);
                        break;
                    }
                    case TALK_FROM_TARGET: {
                        // Listen here
                        Any2OneChannel<Pair<UUID, byte[]>> internalTxDataChannel = Channel.<Pair<UUID, byte[]>> any2one(new InfiniteBuffer<>());
                        AltingChannelInput<Pair<UUID, byte[]>> internalTxDataIn = internalTxDataChannel.in();
                        internalTxDataOuts.put(r.initiatorTunnelId, JcspUtils.logDeadlock(internalTxDataChannel.out()));
                        ep = new ListenerEndpoint(r.initiatorTunnelId, m.responseTunnelId, r.incomingPort, internalTxDataIn, internalRxDataOut);
                        localEndpoints.put(r.initiatorTunnelId, ep);
                        break;
                    }
                    default: {
                        throw new RuntimeException("Unhandled tunnelDirection");
                    }
                }
                new ProcessManager(ep).start();
                //NEXT Whatever we add here, also add in the TunnelRequestMessage handler, above
                // Map both IDs to our initial endpoint id
                endpointToLocal.put(m.responseTunnelId, r.initiatorTunnelId);
                endpointToLocal.put(r.initiatorTunnelId, r.initiatorTunnelId);
                asdf;
            } else {
                //THINK Not clear which endpoint ID to use
                txOtsOut.write(new OTSSingle(UUID.randomUUID(), token.nodeId, new TunnelErrorMessage(m.initiatorTunnelId, "No such request present")));
            }
            return true;
        } else if (m0 instanceof TunnelErrorMessage) {
            TunnelErrorMessage m = (TunnelErrorMessage)m0;
            System.err.println("Tunnel error: "+m); //THINK Maybe not syserr
            //DUMMY //THINK
            pendingTunnels.remove(((TunnelErrorMessage) m0).tunnelId);
            UUID l = endpointToLocal.get(((TunnelErrorMessage) m0).tunnelId);
            UUID r = endpointToRemote(((TunnelErrorMessage) m0).tunnelId);
            Endpoint ep = localEndpoints.remove(l);
            endpointToLocal.remove(l);
            endpointToLocal.remove(r);
            ep.close(); //NEXT close
            //NEXT Remove all connections from connectionsToEndpoints
            return true;
        } else if (m0 instanceof TunnelConnectionRequestMessage) {
            TunnelConnectionRequestMessage m = (TunnelConnectionRequestMessage)m0;
            System.out.println("Tunnel connection request: " + m);
            Endpoint ep = localEndpoints.get(endpointToLocal.get(m.targetTunnelId));
            if (ep == null) {
                txOtsOut.write(new OTSSingle(UUID.randomUUID(), token.nodeId, new TunnelConnectionErrorMessage(m.connectionId, "No such endpoint present")));
                return true;
            }
            //NEXT Check existing connection?
            asdf;
            return true;
        } else if (m0 instanceof TunnelConnectionSuccessMessage) {
            TunnelConnectionSuccessMessage m = (TunnelConnectionSuccessMessage)m0;
            //THINK Should this even be a thing?  Or should a connection be assumed until proven otherwise?
            //NEXT //DUMMY Make connection or st
            asdf;
            return true;
        } else if (m0 instanceof TunnelConnectionErrorMessage) {
            TunnelConnectionErrorMessage m = (TunnelConnectionErrorMessage)m0;
            //NEXT //DUMMY Close any connection
            asdf;
            return true;
        } else if (m0 instanceof TunnelConnectionDataMessage) {
            TunnelConnectionDataMessage m = (TunnelConnectionDataMessage)m0;
            try {
                UUID epid = connectionsToEndpoints.get(m.connectionId).thisId;
                //THINK Maybe check index or something?
                internalTxDataOuts.get(epid).write(Pair.gen(m.connectionId, m.data));
            } catch (Exception e) {
                //NEXT Send error or something, close channels
                throw e;
            }
            asdf;
            return true;
        } else {
            // Unhandled
            return false;
        }
    }

    @Override
    public void run() {
        Alternative alt = new Alternative(new Guard[]{messageIn, internalRxDataIn});
        while (true) {
            try {
                switch (alt.priSelect()) {
                    case 0: { // messageIn
                        Pair<CRToken, Object> msg = messageIn.read();
                        handleMessage(msg);
                        //DUMMY Handle shutdown?
                        break;
                    }
                    case 1: { // internalRxDataIn
                        // connection, data
                        Pair<UUID, byte[]> msg = internalRxDataIn.read();
                        // Pass back out
                        //RAINY index
                        txOtsOut.write(new OTSSingle(UUID.randomUUID(), this.nodeId, new TunnelConnectionDataMessage(-1, msg.b, msg.a)));
                        break;
                    }
                }
            } catch (Throwable t) {
                System.err.println("TunnelManager got error; continuing");
                t.printStackTrace();
            }
        }
    }
}
