/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.lancopy.refactor2.messages.IdentificationMessage;
import com.erhannis.lancopy.refactor2.tls.TlsWrapper;
import com.erhannis.lancopy.refactor2.tls.TlsWrapper.ClientServerMode;
import com.erhannis.mathnstuff.Pair;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import jcsp.helpers.BackpressureRegulator;
import jcsp.helpers.JcspUtils;
import jcsp.helpers.JcspUtils.DeadlockLoggingChannelOutput;
import jcsp.lang.Alternative;
import jcsp.lang.AltingBarrier;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.AltingChannelInputInt;
import jcsp.lang.AltingChannelOutput;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.Any2OneChannelInt;
import jcsp.lang.Barrier;
import jcsp.lang.CSProcess;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;
import jcsp.lang.ChannelOutputInt;
import jcsp.lang.Guard;
import jcsp.lang.JoinFork;
import jcsp.lang.Parallel;
import jcsp.lang.PoisonException;
import jcsp.lang.ProcessManager;
import jcsp.lang.Sequence;
import jcsp.util.InfiniteBuffer;
import jcsp.util.ints.InfiniteBufferInt;

/**
 * This represents a Node, in the view of the CommsManager.
 * @author erhannis
 */
public class NodeManager implements CSProcess {
    public static class NMInterface {
        public final BackpressureRegulator<byte[]> txMsgOut;
        public final AltingChannelInput<Pair<NodeManager.CRToken,byte[]>> rxMsgIn;
        public final ChannelOutput<NodeManager.CRToken> demandShuffleChannelOut;
        public final AltingChannelInput<ChannelReader> channelReaderShuffleAIn;
        public final ChannelOutput<ChannelReader> channelReaderShuffleBOut;
        public final ChannelOutput<CommChannel> incomingConnectionOut;
        public final ChannelOutput<List<Comm>> subscribeOut;
        public final AltingChannelInput<Pair<Comm,Boolean>> commStatusIn;

        public NMInterface(BackpressureRegulator<byte[]> txMsgOut, AltingChannelInput<Pair<NodeManager.CRToken, byte[]>> rxMsgIn, ChannelOutput<NodeManager.CRToken> demandShuffleChannelOut, AltingChannelInput<ChannelReader> channelReaderShuffleAIn, ChannelOutput<ChannelReader> channelReaderShuffleBOut, ChannelOutput<CommChannel> incomingConnectionOut, ChannelOutput<List<Comm>> subscribeOut, AltingChannelInput<Pair<Comm, Boolean>> commStatusIn) {
            this.txMsgOut = txMsgOut;
            this.rxMsgIn = rxMsgIn;
            this.demandShuffleChannelOut = demandShuffleChannelOut;
            this.channelReaderShuffleAIn = channelReaderShuffleAIn;
            this.channelReaderShuffleBOut = channelReaderShuffleBOut;
            this.incomingConnectionOut = incomingConnectionOut;
            this.subscribeOut = subscribeOut;
            this.commStatusIn = commStatusIn;
        }
    }
    
    public static class CRToken {
        public UUID nodeId;

        public CRToken(UUID nodeId) {
            this.nodeId = nodeId;
        }
    }
    
    public static class ChannelReader implements CSProcess {
        public static final Comparator<? super ChannelReader> COMPARATOR = (a, b) -> {
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
        };
        
        // Token used to identify this CR without passing around the CR itself
        public final CRToken token;
        final CommChannel cc;
        final AltingChannelInput<byte[]> rxMsgIn; // Use externally
        private final DeadlockLoggingChannelOutput<byte[]> rxMsgOut;

        public ChannelReader(CommChannel cc, UUID nodeId) {
            this.cc = cc;
            Any2OneChannel<byte[]> rxMsgChannel = Channel.<byte[]> any2one(5);
            this.rxMsgIn = rxMsgChannel.in();
            this.rxMsgOut = JcspUtils.logDeadlock(rxMsgChannel.out());
            this.token = new CRToken(nodeId);
        }
        
        @Override
        public void run() {
            Thread.currentThread().setName("NM.CR " + token.nodeId + " " + token + " " + cc);
            try {
                while (true) {
                    try {
                        ByteBuffer bbLen = ByteBuffer.allocate(4);
                        while (bbLen.remaining() > 0) {
                            if (cc.read(bbLen) < 0) {
                                rxMsgOut.write(null, this.token+"");
                            }
                        }
                        int len = Ints.fromByteArray(bbLen.array());
                        ByteBuffer bbMsg = ByteBuffer.allocate(len);
                        while (bbMsg.remaining() > 0) {
                            if (cc.read(bbMsg) < 0) {
                                rxMsgOut.write(null, this.token+"");
                            }
                        }
                        rxMsgOut.write(bbMsg.array(), this.token+"");
                    } catch (IOException ex) {
                        Logger.getLogger(NodeManager.class.getName()).log(Level.SEVERE, null, ex);
                        System.err.println("NM.ChannelReader error, exiting - " + cc.comm + " : " + this.token);
                        rxMsgOut.write(null, this.token+"");
                        return;
                    }
                }
            } catch (PoisonException pe) {
                System.err.println("NM.CR poisoned, " + this.token);
            }
        }
    }
    
    private final DataOwner dataOwner;
    private final UUID nodeId;
    //TODO Don't create duplicate of any open cc?
    /**
     * Open connections to node.
     * The first should be the "best" - fastest, most reliable, whatever.
     * The first will be used for outgoing messages, but all will be monitored for incoming messages.
     * 
     * A bit hacky, a list of ChannelReaders, but they contain all the stuff we need.
     */
    private ArrayList<ChannelReader> connections = new ArrayList<>();
    

    private final AltingChannelInput<byte[]> txMsgIn;
    private final ChannelOutput<Pair<CRToken,byte[]>> rxMsgOut;
    private final AltingChannelInput<CRToken> demandShuffleChannelIn;
    private final ChannelOutput<ChannelReader> channelReaderShuffleAOut;
    private final AltingChannelInput<ChannelReader> channelReaderShuffleBIn;
    private final AltingChannelInput<CommChannel> incomingConnectionIn;
    private final AltingChannelInput<List<Comm>> subscribeIn;
    private final ChannelOutput<Pair<Comm,Boolean>> commStatusOut;
    private final ChannelOutputInt showLocalFingerprintOut;

    private final JoinFork internalJF = new JoinFork();

    /**
     * txMsgIn should be buffered, imo - NM should not block CM.
     * Do not modify incomingConnectionIns after passing it; doing so will probably break something.
     * 
     * @param dataOwner
     * @param nodeId
     * @param txMsgIn
     * @param rxMsgOut
     * @param shuffleChannelIn
     * @param channelReaderShuffleOut
     * @param incomingConnectionIn
     * @param subscribeIn
     * @param commStatusOut 
     * @param showLocalFingerprintOut
     */
    public NodeManager(DataOwner dataOwner, UUID nodeId, AltingChannelInput<byte[]> txMsgIn, ChannelOutput<Pair<CRToken, byte[]>> rxMsgOut, AltingChannelInput<CRToken> demandShuffleChannelIn, ChannelOutput<ChannelReader> channelReaderShuffleAOut, AltingChannelInput<ChannelReader> channelReaderShuffleBIn, AltingChannelInput<CommChannel> incomingConnectionIn, AltingChannelInput<List<Comm>> subscribeIn, ChannelOutput<Pair<Comm,Boolean>> commStatusOut, ChannelOutputInt showLocalFingerprintOut) {
        this.dataOwner = dataOwner;
        this.nodeId = nodeId;
        this.txMsgIn = txMsgIn;
        this.rxMsgOut = rxMsgOut;
        this.demandShuffleChannelIn = demandShuffleChannelIn;
        this.channelReaderShuffleAOut = channelReaderShuffleAOut;
        this.channelReaderShuffleBIn = channelReaderShuffleBIn;
        this.incomingConnectionIn = incomingConnectionIn;
        this.subscribeIn = subscribeIn;
        this.commStatusOut = commStatusOut;
        this.showLocalFingerprintOut = showLocalFingerprintOut;
    }

    private static final int N = 6; // Number of fixed Guards
    
    private Alternative regenAlt() {
        Guard[] guards = new Guard[N + connections.size()];
        guards[0] = internalJF;
        guards[1] = incomingConnectionIn;
        guards[2] = subscribeIn;
        guards[3] = demandShuffleChannelIn;
        guards[4] = channelReaderShuffleBIn;
        guards[5] = txMsgIn;
        for (int i = 0; i < connections.size(); i++) {
            guards[i+N] = connections.get(i).rxMsgIn;
        }
        return new Alternative(guards);
    }
    
    @Override
    public void run() {
        //TODO Allow unidirectional comms?  Except, TLS requires an exchange...  TLS on [Comm], rather than [TLS on Comm] ?
        
        System.out.println("NodeManager starting up: " + nodeId);
        Thread.currentThread().setName("NodeManager " + nodeId);
        Alternative[] alt = new Alternative[]{regenAlt()};
        while (true) {
            try {
                //TODO Should we be able to rx messages while blocked trying to send a message?  ...Not until it becomes important, I think.
                //  Like, it might be more efficient, but only under certain circumstances, and it feels confusing to me.
                // I've switched this from fairSelect to priSelect, because I want to ensure channel shuffling occurs before transmission requests.
                //  It'd be nice if you could nest selection, and like, some of it's priselect and some of it's fairselect...
                int idx = alt[0].priSelect();
                switch (idx) {
                    case 0: { // internalJF
                        internalJF.accept(null);
                        break;
                    }
                    case 1: { // incomingConnectionIn
                        CommChannel cc = incomingConnectionIn.read();

                        try {
                            if (dataOwner.encrypted) {
                                //TODO Are interrupts still a thing?
                                //TODO Actually, since both layers of cc have an interrupt callback, handling that's a bit weird
                                //TODO Verify cert matches id
                                // Note, I'm defying convention and skipping the intermediation, because this case can block waiting for the connection to go through, defeating the purpose of this notification
                                cc = new TlsWrapper(dataOwner, ClientServerMode.HAGGLE, true, cc, showLocalFingerprintOut);
                            }
                            ChannelReader cr = new ChannelReader(cc, nodeId);
                            new ProcessManager(cr).start();
                            System.out.println("NM wrapped CR: " + cr.token);

                            // Send self-identification
                            try {
                                //TODO It's a little wrong for NM to serialize a message...but, it seems like things would get a lot more complicated, otherwise.
                                byte[] msg = dataOwner.serialize(logSend(new IdentificationMessage(dataOwner.ID)));
                                cr.cc.write(ByteBuffer.wrap(Ints.toByteArray(msg.length)));
                                cr.cc.write(ByteBuffer.wrap(msg));

                                // If the above lines threw, we don't want to add this connection, anyway
                                connections.add(cr);
                                dataOwner.errOnce("//TODO Figure out how to show incame connections status");
                                //commStatusOut.write(Pair.gen(comm, true));
                            } catch (IOException ex) {
                                Logger.getLogger(NodeManager.class.getName()).log(Level.SEVERE, null, ex);
                                System.err.println("NodeManager error ; closed comm: " + cr.cc.comm);
                                System.err.println("NM poisoning " + cr.token);
                                cr.rxMsgIn.poison(10);
                                try {
                                    cr.cc.close();
                                } catch (IOException ex1) {
                                    Logger.getLogger(NodeManager.class.getName()).log(Level.SEVERE, null, ex1);
                                }
                                dataOwner.errOnce("//TODO Figure out how to show incame connections status");
                                //commStatusOut.write(Pair.gen(comm, false));
                            }
                        } catch (IOException ex) {
                            Logger.getLogger(NodeManager.class.getName()).log(Level.SEVERE, null, ex);
                        }

                        connections.sort(ChannelReader.COMPARATOR);
                        alt[0] = regenAlt();   
                        break;
                    }
                    case 2: { // subscribeIn
                        List<Comm> comms = subscribeIn.read();

                        final int count = comms.size();
                        Any2OneChannelInt successChannel = Channel.any2oneInt(new InfiniteBufferInt());
                        AltingChannelInputInt successIn = successChannel.in();
                        ChannelOutputInt successOut = JcspUtils.logDeadlock(successChannel.out());
                        
                        //CountDownLatch cdl = new CountDownLatch(count);
                        ArrayList<CSProcess> processes = new ArrayList<>();
                        for (Comm comm : comms) {
                            processes.add(() -> {
                                Thread.currentThread().setName("NM.sub " + nodeId + " " + comm);
                                try {
                                    CommChannel cc = comm.connect(dataOwner);
                                    if (dataOwner.encrypted) {
                                        //TODO Are interrupts still a thing?
                                        //TODO Verify cert matches id
                                        // Note, I'm defying convention and skipping the intermediation, because this case can block waiting for the connection to go through, defeating the purpose of this notification
                                        cc = new TlsWrapper(dataOwner, ClientServerMode.HAGGLE, true, cc, showLocalFingerprintOut);
                                    }
                                    ChannelReader cr = new ChannelReader(cc, nodeId);
                                    new ProcessManager(cr).start();
                                    System.out.println("NM created CR: " + cr.token);

                                    // Send self-identification
                                    try {
                                        //TODO It's a little wrong for NM to serialize a message...but, it seems like things would get a lot more complicated, otherwise.
                                        byte[] msg = dataOwner.serialize(logSend(new IdentificationMessage(dataOwner.ID)));
                                        cr.cc.write(ByteBuffer.wrap(Ints.toByteArray(msg.length)));
                                        cr.cc.write(ByteBuffer.wrap(msg));

                                        // If the above lines threw, we don't want to add this connection, anyway
                                        internalJF.join(() -> {
                                            connections.add(cr);
                                            commStatusOut.write(Pair.gen(comm, true));
                                            connections.sort(ChannelReader.COMPARATOR);
                                            alt[0] = regenAlt();
                                            successOut.write(count);
                                            return null;
                                        });
                                    } catch (IOException ex) {
                                        Logger.getLogger(NodeManager.class.getName()).log(Level.SEVERE, null, ex);
                                        System.err.println("NodeManager error subscribing; closed comm: " + cr.cc.comm);
                                        System.err.println("NM poisoning " + cr.token);
                                        cr.rxMsgIn.poison(10);
                                        try {
                                            cr.cc.close();
                                        } catch (IOException ex1) {
                                            Logger.getLogger(NodeManager.class.getName()).log(Level.SEVERE, null, ex1);
                                        }
                                        internalJF.join(() -> {
                                            commStatusOut.write(Pair.gen(comm, false));
                                            return null;
                                        });
                                    }
                                } catch (Exception ex) {
                                    Logger.getLogger(NodeManager.class.getName()).log(Level.SEVERE, null, ex);
                                    internalJF.join(() -> {
                                        commStatusOut.write(Pair.gen(comm, false));
                                        return null;
                                    });
                                } finally {
                                    successOut.write(1);
                                }
                            });
                        }
                        
                        boolean parallel = (Boolean) dataOwner.options.getOrDefault("NodeManager.PARALLEL_CONNECTION_ATTEMPTS", true);

                        if (parallel) {
                            new ProcessManager(new Parallel(processes.toArray(new CSProcess[0]))).start();
                        } else {
                            new ProcessManager(new Sequence(processes.toArray(new CSProcess[0]))).start();
                        }
                        
                        int remaining = count;
                        Alternative waitingAlt = new Alternative(new Guard[]{successIn, internalJF});
                        while (remaining > 0) {
                            switch (waitingAlt.priSelect()) {
                                case 0: { // successIn
                                    remaining -= successIn.read();
                                    System.out.println("NM remaining: " + remaining);
                                    break;
                                }
                                case 1: { // internalJF
                                    internalJF.accept(null);
                                    break;
                                }
                            }
                        }
                        System.out.println("NM stop waiting");
                        break;
                    }
                    case 3: { // demandShuffleChannelIn
                        CRToken token = demandShuffleChannelIn.read();
                        for (Iterator<ChannelReader> cri = connections.iterator(); cri.hasNext();) {
                            ChannelReader cr = cri.next();
                            if (cr.token == token) {
                                System.out.println("NodeManager giving up channel reader: " + cr.cc.comm);
                                channelReaderShuffleAOut.write(cr);
                                cri.remove();
                                alt[0] = regenAlt();
                                break;
                            }
                        }
                        break;
                    }
                    case 4: { // channelReaderShuffleBIn
                        ChannelReader cr = channelReaderShuffleBIn.read();
                        System.out.println("NM adopted CR: " + cr.token);
                        connections.add(cr);
                        connections.sort(ChannelReader.COMPARATOR);
                        alt[0] = regenAlt();
                        break;
                    }
                    case 5: { // txMsgIn
                        byte[] msg = txMsgIn.read();
                        System.out.println("NM tx... " + this.nodeId + " " + msg);
                        boolean success = false;
                        if (msg.length == 1) {
                            System.out.println("weird tx; " + Arrays.toString(msg));
                        }
                        for (Iterator<ChannelReader> cri = connections.iterator(); cri.hasNext();) {
                            ChannelReader cr = cri.next();
                            try {
                                cr.cc.write(ByteBuffer.wrap(Ints.toByteArray(msg.length)));
                                cr.cc.write(ByteBuffer.wrap(msg));
                                success = true;
                                break; // We succeeded
                            } catch (IOException ex) {
                                Logger.getLogger(NodeManager.class.getName()).log(Level.SEVERE, null, ex);
                                System.err.println("NodeManager error tx; removing closed comm: " + cr.cc.comm);
                                System.err.println("NM poisoning " + cr.token);
                                cr.rxMsgIn.poison(10);
                                try {
                                    cr.cc.close();
                                } catch (IOException ex1) {
                                    Logger.getLogger(NodeManager.class.getName()).log(Level.SEVERE, null, ex1);
                                }
                                cri.remove();
                                alt[0] = regenAlt();
                                if (cr.cc.comm != null) {
                                    commStatusOut.write(Pair.gen(cr.cc.comm, false));
                                } else {
                                    dataOwner.errOnce("//TODO Figure out how to show incame connections status");
                                }
                            }
                        }
                        if (success) {
                            System.out.println("NM ...tx");
                        } else {
                            System.err.println("NM failed to tx");
                        }
                        break;
                    }
                    default: { // rxMsgIn
                        ChannelReader cr = connections.get(idx-N);
                        byte[] msg = cr.rxMsgIn.read();
                        System.out.println("NM rx " + this.nodeId + " " + cr.cc.comm + " " + msg);
                        if (msg != null) {
                            // Pass msg on to CM
                            rxMsgOut.write(Pair.gen(cr.token, msg));
                        } else {
                            // Reader had a problem; close channel
                            System.err.println("NodeManager rx null; closing cc: " + cr.cc.comm);
                            System.err.println("NM poisoning " + cr.token);
                            cr.rxMsgIn.poison(10);
                            try {
                                cr.cc.close();
                            } catch (IOException ex) {
                                Logger.getLogger(NodeManager.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            connections.remove(idx-N);
                            alt[0] = regenAlt();
                            if (cr.cc.comm != null) {
                                commStatusOut.write(Pair.gen(cr.cc.comm, false));
                            } else {
                                dataOwner.errOnce("//TODO Figure out how to show incame connections status");
                            }
                        }
                        break;
                    }
                }
            } catch (Throwable t) {
                System.err.println("NodeManager got error; continuing");
                t.printStackTrace();
            }
        }
    }
    
    private static <T> T logSend(T o) {
        System.out.println("NM sending " + o);
        return o;
    }
}
