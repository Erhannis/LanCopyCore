/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.refactor.Advertisement;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.lancopy.refactor.Summary;
import com.erhannis.lancopy.refactor2.tcp.TcpCommChannel;
import com.erhannis.mathnstuff.FactoryHashMap;
import com.erhannis.mathnstuff.Pair;
import java.io.IOException;
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
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.CSProcess;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;
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
    
    public CommsManager(DataOwner dataOwner) {
        this.dataOwner = dataOwner;
    }

    private final AltingChannelInput<Advertisement> aadIn;
    private final AltingChannelInput<List<Comm>> subscribeIn;

    private HashMap<UUID, NodeManager.NMInterface> nodes = new HashMap<>();
    
    @Override
    public void run() {
        Any2OneChannel<CommChannel> internalCommChannelChannel = Channel.<CommChannel> any2one();
        AltingChannelInput<CommChannel> internalCommChannelIn = internalCommChannelChannel.in();
        ChannelOutput<CommChannel> internalCommChannelOut = internalCommChannelChannel.out();
        
        //DO Add Blank NM
        
        //TODO Move these somewhere else?  Abstract?
        new ProcessManager(new Parallel(new CSProcess[] {
            () -> { // TCP server listener
                //TODO Fallback to "Comms.tcp.enabled"?
                boolean tcpEnabled = (Boolean) dataOwner.options.getOrDefault("Comms.tcp.server_enabled", true);

                if (tcpEnabled) {
                    int port = (int) dataOwner.options.getOrDefault("Comms.tcp.server_port", 0);
                    while (true) {
                        try {
                            TcpCommChannel.serverThread(internalCommChannelOut, port);
                        } catch (IOException ex) {
                            Logger.getLogger(CommsManager.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }, () -> {
                
            }
        })).start();
        
        //TODO Add verification to make sure nodes' claims match their TLS credentials
        //DO Read Identification message first off, transfer channel
        //DO Find deadlock cycles
        
        Alternative alt = new Alternative(new Guard[]{aadIn, lsumIn, subscribeIn, internalMsgRxIn, internalCommChannelIn});
        while (true) {
            switch (alt.priSelect()) {
                case 0: { // aadIn
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
                }
                case 1: { // lsumIn
                    //DO Cache, and autosend to new connections?
                    Summary summary = lsumIn.read();
                    byte[] msg = dataOwner.serialize(summary);
                    for (Entry<UUID, NodeManager.NMInterface> e : nodes.entrySet()) {
                        if (e.getKey() != null) {
                            e.getValue().txMsgOut.write(msg);
                        }
                    }
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
                        if (nodes.containsKey(id)) {
                            nodes.get(id).subscribeOut.write(separated.get(id));
                        } else {
                            // Blehhhhh, this is boilerplatey
                            Any2OneChannel<byte[]> txMsgChannel = Channel.<byte[]> any2one();
                            AltingChannelInput<byte[]> txMsgIn = txMsgChannel.in();
                            ChannelOutput<byte[]> txMsgOut = txMsgChannel.out();

                            Any2OneChannel<Pair<Object,byte[]>> rxMsgChannel = Channel.<Pair<Object,byte[]>> any2one();
                            AltingChannelInput<Pair<Object,byte[]>> rxMsgIn = rxMsgChannel.in();
                            ChannelOutput<Pair<Object,byte[]>> rxMsgOut = rxMsgChannel.out();

                            Any2OneChannel<Object> shuffleChannelChannel = Channel.<Object> any2one();
                            AltingChannelInput<Object> shuffleChannelIn = shuffleChannelChannel.in();
                            ChannelOutput<Object> shuffleChannelOut = shuffleChannelChannel.out();

                            Any2OneChannel<NodeManager.ChannelReader> channelReaderShuffleChannel = Channel.<NodeManager.ChannelReader> any2one();
                            AltingChannelInput<NodeManager.ChannelReader> channelReaderShuffleIn = channelReaderShuffleChannel.in();
                            ChannelOutput<NodeManager.ChannelReader> channelReaderShuffleOut = channelReaderShuffleChannel.out();

                            Any2OneChannel<CommChannel> incomingConnectionChannel = Channel.<CommChannel> any2one();
                            AltingChannelInput<CommChannel> incomingConnectionIn = incomingConnectionChannel.in();
                            ChannelOutput<CommChannel> incomingConnectionOut = incomingConnectionChannel.out();

                            Any2OneChannel<List<Comm>> subscribeChannel = Channel.<List<Comm>> any2one();
                            AltingChannelInput<List<Comm>> subscribeIn = subscribeChannel.in();
                            ChannelOutput<List<Comm>> subscribeOut = subscribeChannel.out();

                            Any2OneChannel<Pair<Comm,Boolean>> commStatusChannel = Channel.<Pair<Comm,Boolean>> any2one();
                            AltingChannelInput<Pair<Comm,Boolean>> commStatusIn = commStatusChannel.in();
                            ChannelOutput<Pair<Comm,Boolean>> commStatusOut = commStatusChannel.out();

                            new ProcessManager(new NodeManager(dataOwner, id, txMsgIn, rxMsgOut, shuffleChannelIn, channelReaderShuffleOut, incomingConnectionIn, subscribeIn, commStatusOut)).start();
                            nodes.put(id, new NodeManager.NMInterface(txMsgOut, rxMsgIn, shuffleChannelOut, channelReaderShuffleIn, incomingConnectionOut, subscribeOut, commStatusIn));
                            nodes.get(id).subscribeOut.write(separated.get(id));
                        }
                    }
                    break;
                }
                case 3: { // internalMsgRxIn
                    //DO WRONG; read from the NMIs instead
                    DO;
                    break;
                }
                case 4: { // internalCommChannelIn
                    CommChannel cc = internalCommChannelIn.read();
                    // We don't know which node connected to us, yet, so it goes to the blank NM
                    nodes.get(null).incomingConnectionOut.write(cc);
                    break;
                }
            }
        }
    }
}
