/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.data.Data;
import com.erhannis.lancopy.data.TextData;
import com.erhannis.lancopy.refactor2.CommChannel;
import com.erhannis.lancopy.refactor2.CommsManager;
import com.erhannis.lancopy.refactor2.NodeManager;
import com.erhannis.lancopy.refactor2.OutgoingTransferState;
import com.erhannis.lancopy.refactor2.messages.local.LocalMessage;
import com.erhannis.lancopy.refactor2.messages.local.NodeKeyedLocalMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelRequestMessage;
import com.erhannis.lancopy.refactor2.tunnel.TunnelManager;
import com.erhannis.mathnstuff.FactoryHashMap;
import com.erhannis.mathnstuff.Pair;
import com.erhannis.mathnstuff.utils.Factory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import jcsp.helpers.FCClient;
import jcsp.helpers.JcspUtils;
import jcsp.helpers.NameParallel;
import jcsp.helpers.SynchronousSplitter;
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.AltingChannelInputInt;
import jcsp.lang.AltingFCServer;
import jcsp.lang.AltingFunctionChannel;
import jcsp.lang.AltingTaskChannel;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.Any2OneChannelInt;
import jcsp.lang.CSProcess;
import jcsp.lang.CSTimer;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;
import jcsp.lang.ChannelOutputInt;
import jcsp.lang.Guard;
import jcsp.lang.Parallel;
import jcsp.lang.ProcessManager;
import jcsp.util.InfiniteBuffer;
import jcsp.util.ints.OverWriteOldestBufferInt;

/**
 *
 * @author erhannis
 */
public class LanCopyNet {
    public static UiInterface startNet(DataOwner dataOwner, ChannelOutputInt showLocalFingerprintOut) throws InterruptedException, IOException {
        Any2OneChannel<Advertisement> rxAdChannel = Channel.<Advertisement>any2one(new InfiniteBuffer<>());
        AltingChannelInput<Advertisement> rxAdIn = rxAdChannel.in();
        ChannelOutput<Advertisement> rxAdOut = JcspUtils.logDeadlock(rxAdChannel.out());
        
        Any2OneChannel<Summary> summaryToTrackerChannel = Channel.<Summary> any2one(new InfiniteBuffer<>());
        AltingChannelInput<Summary> summaryToTrackerIn = summaryToTrackerChannel.in();
        ChannelOutput<Summary> summaryToTrackerOut = JcspUtils.logDeadlock(summaryToTrackerChannel.out());
        
        Any2OneChannel<Data> newDataChannel = Channel.<Data> any2one();
        AltingChannelInput<Data> newDataIn = newDataChannel.in();
        ChannelOutput<Data> newDataOut = JcspUtils.logDeadlock(newDataChannel.out());

        Any2OneChannel<List<Comm>> subscribeChannel = Channel.<List<Comm>> any2one(new InfiniteBuffer<>());
        AltingChannelInput<List<Comm>> subscribeIn = subscribeChannel.in();
        ChannelOutput<List<Comm>> subscribeOut = JcspUtils.logDeadlock(subscribeChannel.out());

        Any2OneChannel<Pair<Comm,Boolean>> commStatusChannel = Channel.<Pair<Comm,Boolean>> any2one();
        AltingChannelInput<Pair<Comm,Boolean>> commStatusIn = commStatusChannel.in();
        ChannelOutput<Pair<Comm,Boolean>> commStatusOut = JcspUtils.logDeadlock(commStatusChannel.out());
        
        Any2OneChannel<List<Comm>> lcommsChannel = Channel.<List<Comm>> any2one();
        AltingChannelInput<List<Comm>> lcommsIn = lcommsChannel.in();
        ChannelOutput<List<Comm>> lcommsOut = JcspUtils.logDeadlock(lcommsChannel.out());

        Any2OneChannel<CommChannel> enrollCommChannelChannel = Channel.<CommChannel> any2one(new InfiniteBuffer<>());
        AltingChannelInput<CommChannel> enrollCommChannelIn = enrollCommChannelChannel.in();
        ChannelOutput<CommChannel> enrollCommChannelOut = JcspUtils.logDeadlock(enrollCommChannelChannel.out());

        Any2OneChannel<OutgoingTransferState> txOtsChannel = Channel.<OutgoingTransferState> any2one(new InfiniteBuffer<>());
        AltingChannelInput<OutgoingTransferState> txOtsIn = txOtsChannel.in();
        ChannelOutput<OutgoingTransferState> txOtsOut = JcspUtils.logDeadlock(txOtsChannel.out()); //NEXT

        Any2OneChannel<LocalMessage> localMessageChannel = Channel.<LocalMessage> any2one(new InfiniteBuffer<>());
        AltingChannelInput<LocalMessage> localMessageIn = localMessageChannel.in();
        ChannelOutput<LocalMessage> localMessageOut = JcspUtils.logDeadlock(localMessageChannel.out());

        Any2OneChannel<Pair<NodeManager.CRToken, Object>> rxUnhandledMessageChannel = Channel.<Pair<NodeManager.CRToken, Object>> any2one(new InfiniteBuffer<>());
        AltingChannelInput<Pair<NodeManager.CRToken, Object>> rxUnhandledMessageIn = rxUnhandledMessageChannel.in(); //NEXT
        ChannelOutput<Pair<NodeManager.CRToken, Object>> rxUnhandledMessageOut = JcspUtils.logDeadlock(rxUnhandledMessageChannel.out()); //NEXT
        
        
        SynchronousSplitter<Advertisement> adUpdatedSplitter = new SynchronousSplitter<>();
        SynchronousSplitter<Summary> summaryUpdatedSplitter = new SynchronousSplitter<>();
        
        AltingFunctionChannel<UUID, Advertisement> adCall = new AltingFunctionChannel<>(true);
        //TODO I'm torn whether this ought to return Data, or Pair<String, InputStream>
        AltingTaskChannel<UUID, Pair<String, InputStream>> dataCall = new AltingTaskChannel<>(true);
        AltingFunctionChannel<UUID, Summary> summaryCall = new AltingFunctionChannel<>(true);
        AltingFunctionChannel<Void, List<Advertisement>> rosterCall = new AltingFunctionChannel<>(true);
        AltingFunctionChannel<Void, Data> localDataCall = new AltingFunctionChannel<>(true);
        AltingFunctionChannel<String, Boolean> confirmationCall = new AltingFunctionChannel<>(true);
        
        //TODO Maybe put the summary Call in the NodeTracker
        
        //TODO Allow specified broadcast addresses?
        
        new ProcessManager(new NameParallel(new CSProcess[]{
            adUpdatedSplitter,
            summaryUpdatedSplitter,
            new NodeTracker(JcspUtils.logDeadlock(adUpdatedSplitter), JcspUtils.logDeadlock(summaryUpdatedSplitter), rxAdIn, summaryToTrackerIn, adCall.getServer(), summaryCall.getServer(), rosterCall.getServer()),
            new LocalData(dataOwner, localDataCall.getServer(), summaryToTrackerOut, newDataIn),
            new AdGenerator(dataOwner, rxAdOut, lcommsIn),
            new CommsManager(dataOwner, lcommsOut, rxAdOut, adUpdatedSplitter.register(new InfiniteBuffer<>()), summaryToTrackerOut, summaryUpdatedSplitter.register(new InfiniteBuffer<>()), commStatusOut, subscribeIn, showLocalFingerprintOut, enrollCommChannelIn, txOtsIn, rxUnhandledMessageOut, summaryCall.getClient(), adCall.getClient(), rosterCall.getClient(), localDataCall.getClient(), dataCall.getServer(), confirmationCall.getClient()),
            () -> { // Extra message handlers
                // Like, as in, message objects that come in from other nodes and don't match anything in the CommsManager's gauntlet

                //RAINY Move to separate class probably
                //RAINY Shutdown?
                //RAINY This whole subsystem has become a little stupid
                
                // This is indented weird to line up the parameters
                              FactoryHashMap<UUID, ArrayList<Pair<FCClient<LocalMessage,Boolean>, FCClient<Pair<NodeManager.CRToken,Object>, Boolean>>>> perNodeMessageHandlers
                        = new FactoryHashMap<UUID, ArrayList<Pair<FCClient<LocalMessage,Boolean>, FCClient<Pair<NodeManager.CRToken,Object>, Boolean>>>>(
                                 new Factory<UUID, ArrayList<Pair<FCClient<LocalMessage,Boolean>, FCClient<Pair<NodeManager.CRToken,Object>, Boolean>>>>() {
                                  @Override public ArrayList<Pair<FCClient<LocalMessage,Boolean>, FCClient<Pair<NodeManager.CRToken,Object>, Boolean>>> construct(UUID nodeId) {
                                                   ArrayList<Pair<FCClient<LocalMessage,Boolean>, FCClient<Pair<NodeManager.CRToken,Object>, Boolean>>> mhs = new ArrayList<>();
                        boolean tunnelsEnabled = (Boolean) dataOwner.options.getOrDefault("Comms.tunnels.enabled", true); //THINK Maybe default to false
                        if (tunnelsEnabled) {
                            AltingFunctionChannel<LocalMessage, Boolean> localHandlerCall = new AltingFunctionChannel<>(true);
                            AltingFunctionChannel<Pair<NodeManager.CRToken,Object>, Boolean> handlerCall = new AltingFunctionChannel<>(true);
                            new ProcessManager(new TunnelManager(dataOwner, nodeId, localHandlerCall.getServer(), handlerCall.getServer(), txOtsOut, confirmationCall.getClient())).start();
                            mhs.add(Pair.gen(localHandlerCall.getClient(), handlerCall.getClient()));
                        }
                        //PERIODIC Extra message handlers go here, or in globalMessageHandlers
                        return mhs;
                    }
                });
                ArrayList<Pair<FCClient<Object,Boolean>, FCClient<Pair<NodeManager.CRToken,Object>, Boolean>>> globalMessageHandlers = new ArrayList<>();
                
                Alternative alt = new Alternative(new Guard[]{localMessageIn, rxUnhandledMessageIn});
                while (true) {
                    switch (alt.priSelect()) {
                        case 0: { // localMessageIn
                            LocalMessage msg = localMessageIn.read();
                            handleMessage: {
                                if (msg instanceof NodeKeyedLocalMessage) {
                                    for (Pair<FCClient<LocalMessage,Boolean>, FCClient<Pair<NodeManager.CRToken,Object>, Boolean>> handlers : perNodeMessageHandlers.get(((NodeKeyedLocalMessage) msg).routing)) {
                                        try {
                                            if (handlers.a.call(msg)) {
                                                break handleMessage;
                                            }
                                        } catch (Throwable t) {
                                            t.printStackTrace();
                                        }
                                    }
                                }
                                for (Pair<FCClient<Object,Boolean>, FCClient<Pair<NodeManager.CRToken,Object>, Boolean>> handlers : globalMessageHandlers) {
                                    try {
                                        if (handlers.a.call(msg)) {
                                            break handleMessage;
                                        }
                                    } catch (Throwable t) {
                                        t.printStackTrace();
                                    }
                                }
                                System.err.println("ERR Got unhandled local msg: " + msg);
                            }
                            break;
                        }
                        case 1: { // rxUnhandledMessageIn
                            Pair<NodeManager.CRToken, Object> dmsg = rxUnhandledMessageIn.read();
                            handleMessage: {
                                for (Pair<FCClient<LocalMessage,Boolean>, FCClient<Pair<NodeManager.CRToken,Object>, Boolean>> handlers : perNodeMessageHandlers.get(dmsg.a.nodeId)) {
                                    try {
                                        if (handlers.b.call(dmsg)) {
                                            break handleMessage;
                                        }
                                    } catch (Throwable t) {
                                        t.printStackTrace();
                                    }
                                }
                                for (Pair<FCClient<Object,Boolean>, FCClient<Pair<NodeManager.CRToken,Object>, Boolean>> handlers : globalMessageHandlers) {
                                    try {
                                        if (handlers.b.call(dmsg)) {
                                            break handleMessage;
                                        }
                                    } catch (Throwable t) {
                                        t.printStackTrace();
                                    }
                                }
                                //RAINY We're getting e.g. "P{com.erhannis.lancopy.refactor2.NodeManager$CRToken@62cac7f8,null}" here; what's sending them and why?  Is it just some kind of ping, perhaps?
                                System.err.println("ERR Got unhandled msg(2): " + dmsg);
                            }
                            break;
                        }
                    }
                }
            }
        })).start();
        return new UiInterface(dataOwner, adUpdatedSplitter.register(new InfiniteBuffer<>()), summaryUpdatedSplitter.register(new InfiniteBuffer<>()), commStatusIn, newDataOut, subscribeOut, enrollCommChannelOut, localMessageOut, dataCall.getClient(), rosterCall.getClient(), adCall.getClient(), confirmationCall.getServer());
    }

    public static class UiInterface {
        public final DataOwner dataOwner;
        public final AltingChannelInput<Advertisement> adIn;
        public final AltingChannelInput<Summary> summaryIn;
        public final AltingChannelInput<Pair<Comm,Boolean>> commStatusIn;
        public final ChannelOutput<Data> newDataOut;
        public final ChannelOutput<List<Comm>> subscribeOut;
        public final ChannelOutput<CommChannel> enrollCommChannelOut;
        public final ChannelOutput<LocalMessage> localMessageOut;
        public final FCClient<UUID, Pair<String, InputStream>> dataCall;
        public final FCClient<Void, List<Advertisement>> rosterCall;
        public final FCClient<UUID, Advertisement> adCall;
        public final AltingFCServer<String, Boolean> confirmationServer;
        
        public UiInterface(DataOwner dataOwner, AltingChannelInput<Advertisement> adIn, AltingChannelInput<Summary> summaryIn, AltingChannelInput<Pair<Comm, Boolean>> commStatusIn, ChannelOutput<Data> newDataOut, ChannelOutput<List<Comm>> subscribeOut, ChannelOutput<CommChannel> enrollCommChannelOut, ChannelOutput<LocalMessage> localMessageOut, FCClient<UUID, Pair<String, InputStream>> dataCall, FCClient<Void, List<Advertisement>> rosterCall, FCClient<UUID, Advertisement> adCall, AltingFCServer<String, Boolean> confirmationServer) {
            this.dataOwner = dataOwner;
            this.adIn = adIn;
            this.summaryIn = summaryIn;
            this.commStatusIn = commStatusIn;
            this.newDataOut = newDataOut;
            this.subscribeOut = subscribeOut;
            this.enrollCommChannelOut = enrollCommChannelOut;
            this.localMessageOut = localMessageOut;
            this.dataCall = dataCall;
            this.rosterCall = rosterCall;
            this.adCall = adCall;
            this.confirmationServer = confirmationServer;
        }
    }
    
    private static CSProcess mockUI(DataOwner dataOwner, AltingChannelInput<Advertisement> adIn, AltingChannelInput<Summary> summaryIn, AltingChannelInput<Pair<Comm,Boolean>> commStatusIn, ChannelOutput<Data> newDataOut, ChannelOutput<List<Comm>> subscribeOut, FCClient<List<Comm>, Pair<String, InputStream>> dataCall) {
        return new CSProcess() {
            @Override
            public void run() {
                CSTimer timer = new CSTimer();
                timer.setAlarm(timer.read() + 10000);
                Alternative alt = new Alternative(new Guard[]{adIn, summaryIn, commStatusIn, timer});
                while (true) {
                    switch (alt.priSelect()) {
                        case 0: // adIn
                            Advertisement ad = adIn.read();
                            System.out.println("UI rx ad: " + ad);
                            if (!dataOwner.ID.equals(ad.id)) {
                                //TODO Only subscribe to new or unconnected or whatever, things, somehow
                                subscribeOut.write(ad.comms);
                            }
                            break;
                        case 1: // summaryIn
                            System.out.println("UI rx summary: " + summaryIn.read());
                            break;
                        case 2: // commStatusIn
                            System.out.println("UI rx comm status: " + commStatusIn.read());
                            break;
                        case 3: // timer
                            timer.setAlarm(timer.read() + 10000);
                            newDataOut.write(new TextData("current time at " + dataOwner.ID + " : " + System.currentTimeMillis()));
                            break;
                    }
                }
            }
        };
    }
}
