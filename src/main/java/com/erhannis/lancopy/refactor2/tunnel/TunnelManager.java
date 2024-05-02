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
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelConnectionErrorMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelConnectionRequestMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelConnectionSuccessMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelErrorMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelRequestMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelSuccessMessage;
import com.erhannis.mathnstuff.Pair;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.UUID;
import jcsp.helpers.FCClient;
import jcsp.helpers.JcspUtils;
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInput;
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
Each end of a tunnel has a UUID
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
 *
 * @author erhannis
 */
public class TunnelManager implements MessageHandler {
    //DUMMY I'm really not sure these shut down properly, or at all
    public static abstract class Endpoint implements CSProcess {
        public final ChannelOutput<Pair<UUID, byte[]>> rxDataOut;
        public final AltingChannelInput<Pair<UUID, byte[]>> txDataIn;

        public Endpoint(ChannelOutput<Pair<UUID, byte[]>> rxDataOut, AltingChannelInput<Pair<UUID, byte[]>> txDataIn) {
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
                                    //THINK Maybe I should just generate the id here....
                                    System.err.println("ERR Request for new internal channel with ID that already exists");
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
                                System.err.println("ERR Attempt to send on nonexistent connection");
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

        public ListenerEndpoint(int listeningPort, AltingChannelInput<Pair<UUID, byte[]>> txDataIn, ChannelOutput<Pair<UUID, byte[]>> rxDataOut) {
            super(rxDataOut, txDataIn);
            this.listeningPort = listeningPort;
        }
        
        @Override
        public void run() {
            super.run();
            try (ServerSocket serverSocket = new ServerSocket(listeningPort)) {
                System.out.println("TM.LE Listening on port " + listeningPort);
                
                FCClient<UUID, AltingChannelInput<byte[]>> getTxDataInChannelClient = getTxDataInChannelCall.getClient();
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    UUID conId = UUID.randomUUID();
                    System.out.println("TM.LE RX connection "+conId+" on port " + listeningPort + " ; " + clientSocket);
                    AltingChannelInput<byte[]> internalTxDataIn = getTxDataInChannelClient.call(conId);
                    new ProcessManager(() -> handleConnection(conId, clientSocket, internalTxDataIn)).start();
                }
            } catch (Throwable t) {
                asdf;
            }
        }
    }
    
    public static class TalkerEndpoint extends Endpoint {
        public final String talkingAddress;
        public final int talkingPort;

        public TalkerEndpoint(String talkingAddress, int talkingPort, AltingChannelInput<Pair<UUID, byte[]>> txDataIn, ChannelOutput<Pair<UUID, byte[]>> rxDataOut) {
            super(rxDataOut, txDataIn);
            this.talkingAddress = talkingAddress;
            this.talkingPort = talkingPort;
        }
        
        @Override
        public void run() {
            super.run();
            try (Socket targetSocket = new Socket(talkingAddress, talkingPort)) {
                System.out.println("TM.LE Connected to " + talkingAddress + ":" + talkingPort);
                
                FCClient<UUID, AltingChannelInput<byte[]>> getTxDataInChannelClient = getTxDataInChannelCall.getClient();
                
                //DUMMY //CHECK Wait, this connection ID maybe needs to be the same as at the other endpoint
                UUID conId = UUID.randomUUID();
                System.out.println("TM.LE RX connection "+conId+" on port " + listeningPort + " ; " + clientSocket);
                AltingChannelInput<byte[]> internalTxDataIn = getTxDataInChannelClient.call(conId);
                new ProcessManager(() -> handleConnection(conId, targetSocket, internalTxDataIn)).start();
            } catch (Throwable t) {
                asdf;
            }
        }
    }
    
    public ChannelOutput<OutgoingTransferState> txOtsOut;
    public FCClient<String, Boolean> confirmationClient;

    public HashMap<UUID, UUID> tunnelEnds = new HashMap<>();

    public TunnelManager(ChannelOutput<OutgoingTransferState> txOtsOut, FCClient<String, Boolean> confirmationClient) {
        this.txOtsOut = txOtsOut;
        this.confirmationClient = confirmationClient;
    }
    
    @Override
    public boolean handleMessage(Pair<NodeManager.CRToken, Object> msg) {
        /*
        nodes.get(id).txMsgOut.write(dataOwner.serialize(logSend(aadClient.call(dataOwner.ID))));
        nm.txMsgOut.shouldWrite()
        */
        CRToken token = msg.a;
        Object m0 = msg.b;
        if (m0 instanceof TunnelReqestMessage) {
            TunnelRequestMessage m = (TunnelRequestMessage)m0;
            if (tunnelEnds.containsKey(m.initiatorTunnelId)) {
                txOtsOut.write(new OTSSingle(UUID.randomUUID(), token.nodeId, new TunnelErrorMessage(m.initiatorTunnelId, "ID duplicate")));
                return false;
            } else {
                if (confirmationClient.call("Request from "+token.nodeId+" to create tunnel "+m+".  Permit?")) {
                    //THINK correlationId?
                    UUID responseTunnelId = UUID.randomUUID();
                    tunnelEnds.put(m.initiatorTunnelId, responseTunnelId);
                    //NEXT //DUMMY Make tunnel
                    txOtsOut.write(new OTSSingle(UUID.randomUUID(), token.nodeId, new TunnelSuccessMessage(m.initiatorTunnelId, responseTunnelId)));
                    return true;
                } else {
                    txOtsOut.write(new OTSSingle(UUID.randomUUID(), token.nodeId, new TunnelErrorMessage(m.initiatorTunnelId, "Denied")));
                    return true;
                }
            }
        } else if (m0 instanceof TunnelSuccessMessage) {
            TunnelSuccessMessage m = (TunnelSuccessMessage)m0;
            //NEXT //DUMMY Make tunnel
            asdf;
            return true;
        } else if (m0 instanceof TunnelErrorMessage) {
            TunnelErrorMessage m = (TunnelErrorMessage)m0;
            System.err.println("ERR Failed to create tunnel: "+m);
            return true;
        } else if (m0 instanceof TunnelConnectionRequestMessage) {
            TunnelConnectionRequestMessage m = (TunnelConnectionRequestMessage)m0;
            asdf;
            return true;
        } else if (m0 instanceof TunnelConnectionSuccessMessage) {
            TunnelConnectionSuccessMessage m = (TunnelConnectionSuccessMessage)m0;
            //NEXT //DUMMY Make tunnel
            asdf;
            return true;
        } else if (m0 instanceof TunnelConnectionErrorMessage) {
            TunnelConnectionErrorMessage m = (TunnelConnectionErrorMessage)m0;
            //DUMMY Check source
            //NEXT //DUMMY Close any connection
            asdf;
            return true;
        } else if (m0 instanceof TunnelConnectionRequestMessage) {
            TunnelConnectionRequestMessage m = (TunnelConnectionRequestMessage)m0;
            asdf;
            return true;
        } else {
            // Unhandled
            return false;
        }
    }
}
