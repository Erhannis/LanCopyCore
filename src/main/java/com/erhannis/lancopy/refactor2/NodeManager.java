/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.lancopy.refactor2.messages.IdentificationMessage;
import com.erhannis.lancopy.refactor2.tls.TlsWrapper;
import com.erhannis.mathnstuff.Pair;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
import jcsp.lang.ProcessManager;

/**
 * This represents a Node, in the view of the CommsManager.
 * @author erhannis
 */
public class NodeManager implements CSProcess {
    public static class ChannelReader implements CSProcess {
        // Token used to identify this CR without passing around the CR itself
        public final Object token = new Object();
        final CommChannel cc;
        final AltingChannelInput<byte[]> rxMsgIn; // Use externally
        private final ChannelOutput<byte[]> rxMsgOut;

        public ChannelReader(CommChannel cc) {
            this.cc = cc;
            Any2OneChannel<byte[]> rxMsgChannel = Channel.<byte[]> any2one();
            this.rxMsgIn = rxMsgChannel.in();
            this.rxMsgOut = rxMsgChannel.out();
        }
        
        @Override
        public void run() {
            while (true) {
                try {
                    ByteBuffer bbLen = ByteBuffer.allocateDirect(4);
                    while (bbLen.remaining() > 0) {
                        if (cc.read(bbLen) < 0) {
                            rxMsgOut.write(null);
                        }
                    }
                    int len = Ints.fromByteArray(bbLen.array());
                    ByteBuffer bbMsg = ByteBuffer.allocateDirect(len);
                    while (bbMsg.remaining() > 0) {
                        if (cc.read(bbMsg) < 0) {
                            rxMsgOut.write(null);
                        }
                    }
                    rxMsgOut.write(bbMsg.array());
                } catch (IOException ex) {
                    Logger.getLogger(NodeManager.class.getName()).log(Level.SEVERE, null, ex);
                    System.err.println("NM.ChannelReader error, exiting - " + cc.comm);
                    rxMsgOut.write(null);
                    return;
                }
            }
        }
    }
    
    private final DataOwner dataOwner;
    private final UUID nodeId;
    //DO Don't create duplicate of any open cc
    /**
     * Open connections to node.
     * The first should be the "best" - fastest, most reliable, whatever.
     * The first will be used for outgoing messages, but all will be monitored for incoming messages.
     * 
     * A bit hacky, a list of ChannelReaders, but they contain all the stuff we need.
     */
    private ArrayList<ChannelReader> connections = new ArrayList<>();
    

    private final AltingChannelInput<byte[]> txMsgIn;
    private final ChannelOutput<byte[]> rxMsgOut;
    private final AltingChannelInput<CommChannel> incomingConnectionIn;
    private final AltingChannelInput<List<Comm>> subscribeIn;
    private final ChannelOutput<Pair<Comm,Boolean>> commStatusOut;

    /**
     * txMsgIn should be buffered, imo - NM should not block CM.
     * Do not modify incomingConnectionIns after passing it; doing so will probably break something.
     * 
     * @param dataOwner
     * @param nodeId
     * @param txMsgIn
     * @param rxMsgOut
     * @param incomingConnectionIns
     * @param subscribeIn
     * @param commStatusOut 
     */
    public NodeManager(DataOwner dataOwner, UUID nodeId, AltingChannelInput<byte[]> txMsgIn, ChannelOutput<byte[]> rxMsgOut, AltingChannelInput<CommChannel> incomingConnectionIn, AltingChannelInput<List<Comm>> subscribeIn, ChannelOutput<Pair<Comm,Boolean>> commStatusOut) {
        this.dataOwner = dataOwner;
        this.nodeId = nodeId;
        this.txMsgIn = txMsgIn;
        this.rxMsgOut = rxMsgOut;
        this.incomingConnectionIn = incomingConnectionIn;
        this.subscribeIn = subscribeIn;
        this.commStatusOut = commStatusOut;
    }

    private static final int N = 3; // Number of fixed Guards
    
    private Alternative regenAlt() {
        Guard[] guards = new Guard[N + connections.size()];
        guards[0] = incomingConnectionIn;
        guards[1] = subscribeIn;
        guards[2] = txMsgIn;
        for (int i = 0; i < connections.size(); i++) {
            guards[i+N] = connections.get(i).rxMsgIn;
        }
        return new Alternative(guards);
    }
    
    @Override
    public void run() {
        Alternative alt = regenAlt();
        while (true) {
            //TODO Should we be able to rx messages while blocked trying to send a message?  ...Not until it becomes important, I think.
            //  Like, it might be more efficient, but only under certain circumstances, and it feels confusing to me.
            int idx = alt.fairSelect();
            switch (idx) {
                case 0: { // incomingConnectionIn
                    CommChannel cc = incomingConnectionIn.read();
                    
                    try {
                        if (dataOwner.encrypted) {
                            //TODO Are interrupts still a thing?
                            //TODO Actually, since both layers of cc have an interrupt callback, handling that's a bit weird
                            //TODO Verify cert matches id
                            cc = new TlsWrapper(dataOwner, true, null, cc);
                        }
                        ChannelReader cr = new ChannelReader(cc);
                        new ProcessManager(cr).start();
                        connections.add(cr);
                        dataOwner.errOnce("//TODO Figure out how to show incame connections status");
                        //commStatusOut.write(Pair.gen(comm, true));
                    } catch (IOException ex) {
                        Logger.getLogger(NodeManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    
                    connections.sort((a,b) -> {
                        Comm ca = a.cc.comm;
                        Comm cb = b.cc.comm;
                        double sa = Comm.DEFAULT_SCORE;
                        double sb = Comm.DEFAULT_SCORE;
                        if (ca != null) {
                            sa = ca.score;
                        }
                        if (cb != null) {
                            sb = cb.score;
                        }
                        return Double.compare(sa, sb);
                    });
                    alt = regenAlt();                    
                }
                case 1: { // subscribeIn
                    List<Comm> comms = subscribeIn.read();
                    //DO Optionally parallel
                    for (Comm comm : comms) {
                        try {
                            CommChannel cc = comm.connect();
                            if (dataOwner.encrypted) {
                                //TODO Are interrupts still a thing?
                                //TODO Verify cert matches id
                                cc = new TlsWrapper(dataOwner, true, null, cc);
                            }
                            ChannelReader cr = new ChannelReader(cc);
                            new ProcessManager(cr).start();
                            
                            // Send self-identification
                            try {
                                //TODO It's a little wrong for NM to serialize a message...but, it seems like things would get a lot more complicated, otherwise.
                                byte[] msg = dataOwner.serialize(new IdentificationMessage(dataOwner.ID));
                                cr.cc.write(ByteBuffer.wrap(Ints.toByteArray(msg.length)));
                                cr.cc.write(ByteBuffer.wrap(msg));

                                // If the above lines threw, we don't want to add this connection, anyway
                                connections.add(cr);
                                commStatusOut.write(Pair.gen(comm, true));
                            } catch (IOException ex) {
                                Logger.getLogger(NodeManager.class.getName()).log(Level.SEVERE, null, ex);
                                System.err.println("NodeManager error subscribing; closed comm: " + cr.cc.comm);
                                try {
                                    cr.cc.close();
                                } catch (IOException ex1) {
                                    Logger.getLogger(NodeManager.class.getName()).log(Level.SEVERE, null, ex1);
                                }
                                commStatusOut.write(Pair.gen(comm, false));
                                alt = regenAlt();
                                if (cr.cc.comm != null) {
                                    commStatusOut.write(Pair.gen(cr.cc.comm, false));
                                } else {
                                    dataOwner.errOnce("//TODO Figure out how to show incame connections status");
                                }
                            }
                        } catch (Exception ex) {
                            Logger.getLogger(NodeManager.class.getName()).log(Level.SEVERE, null, ex);
                            commStatusOut.write(Pair.gen(comm, false));
                        }
                    }
                    connections.sort((a,b) -> {
                        Comm ca = a.cc.comm;
                        Comm cb = b.cc.comm;
                        double sa = Comm.DEFAULT_SCORE;
                        double sb = Comm.DEFAULT_SCORE;
                        if (ca != null) {
                            sa = ca.score;
                        }
                        if (cb != null) {
                            sb = cb.score;
                        }
                        return Double.compare(sa, sb);
                    });
                    alt = regenAlt();
                    break;
                }
                case 2: { // txMsgIn
                    byte[] msg = txMsgIn.read();
                    for (Iterator<ChannelReader> cri = connections.iterator(); cri.hasNext();) {
                        ChannelReader cr = cri.next();
                        try {
                            cr.cc.write(ByteBuffer.wrap(Ints.toByteArray(msg.length)));
                            cr.cc.write(ByteBuffer.wrap(msg));
                            break; // We succeeded
                        } catch (IOException ex) {
                            Logger.getLogger(NodeManager.class.getName()).log(Level.SEVERE, null, ex);
                            System.err.println("NodeManager error tx; removing closed comm: " + cr.cc.comm);
                            try {
                                cr.cc.close();
                            } catch (IOException ex1) {
                                Logger.getLogger(NodeManager.class.getName()).log(Level.SEVERE, null, ex1);
                            }
                            cri.remove();
                            alt = regenAlt();
                            if (cr.cc.comm != null) {
                                commStatusOut.write(Pair.gen(cr.cc.comm, false));
                            } else {
                                dataOwner.errOnce("//TODO Figure out how to show incame connections status");
                            }
                        }
                    }
                    break;
                }
                default: { // rxMsgIn
                    ChannelReader cr = connections.get(idx-N);
                    byte[] msg = cr.rxMsgIn.read();
                    if (msg != null) {
                        // Pass msg on to CM
                        rxMsgOut.write(msg);
                    } else {
                        // Reader had a problem; close channel
                        try {
                            System.err.println("NodeManager rx null; closing cc: " + cr.cc.comm);
                            cr.cc.close();
                        } catch (IOException ex) {
                            Logger.getLogger(NodeManager.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        connections.remove(idx-N);
                        alt = regenAlt();
                        if (cr.cc.comm != null) {
                            commStatusOut.write(Pair.gen(cr.cc.comm, false));
                        } else {
                            dataOwner.errOnce("//TODO Figure out how to show incame connections status");
                        }
                    }
                    break;
                }
            }
        }
    }
}
