/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.data.Data;
import com.erhannis.lancopy.data.TextData;
import com.erhannis.lancopy.refactor2.CommsManager;
import com.erhannis.mathnstuff.Pair;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import jcsp.helpers.FCClient;
import jcsp.helpers.JcspUtils;
import jcsp.helpers.SynchronousSplitter;
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.AltingFunctionChannel;
import jcsp.lang.AltingTaskChannel;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.CSProcess;
import jcsp.lang.CSTimer;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;
import jcsp.lang.Guard;
import jcsp.lang.Parallel;
import jcsp.lang.ProcessManager;
import jcsp.util.InfiniteBuffer;

/**
 *
 * @author erhannis
 */
public class LanCopyNet {
    public static UiInterface startNet(Function<String, Boolean> trustCallback) throws InterruptedException, IOException {
        DataOwner dataOwner = new DataOwner(trustCallback);
        
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

        //DO Deal with this
        Any2OneChannel<String> getRosterChannel = Channel.<String> any2one(new InfiniteBuffer<>());
        AltingChannelInput<String> getRosterIn = getRosterChannel.in();
        ChannelOutput<String> getRosterOut = JcspUtils.logDeadlock(getRosterChannel.out());
        
        
        SynchronousSplitter<Advertisement> adUpdatedSplitter = new SynchronousSplitter<>();
        SynchronousSplitter<Summary> summaryUpdatedSplitter = new SynchronousSplitter<>();
        
        AltingFunctionChannel<UUID, Advertisement> adCall = new AltingFunctionChannel<>(true);
        //TODO I'm torn whether this ought to return Data, or Pair<String, InputStream>
        AltingTaskChannel<UUID, Pair<String, InputStream>> dataCall = new AltingTaskChannel<>(true);
        AltingFunctionChannel<UUID, Summary> summaryCall = new AltingFunctionChannel<>(true);
        AltingFunctionChannel<Void, List<Advertisement>> rosterCall = new AltingFunctionChannel<>(true);
        AltingFunctionChannel<Void, Data> localDataCall = new AltingFunctionChannel<>(true);
        
        //TODO Maybe put the summary Call in the NodeTracker
        
        //TODO Allow specified broadcast addresses?
        
        new ProcessManager(new Parallel(new CSProcess[]{
            adUpdatedSplitter,
            summaryUpdatedSplitter,
            new NodeTracker(JcspUtils.logDeadlock(adUpdatedSplitter), JcspUtils.logDeadlock(summaryUpdatedSplitter), rxAdIn, summaryToTrackerIn, adCall.getServer(), summaryCall.getServer(), rosterCall.getServer()),
            new LocalData(dataOwner, localDataCall.getServer(), summaryToTrackerOut, newDataIn),
            new AdGenerator(dataOwner, rxAdOut, lcommsIn),
            new CommsManager(dataOwner, lcommsOut, rxAdOut, adUpdatedSplitter.register(new InfiniteBuffer<>()), summaryToTrackerOut, summaryUpdatedSplitter.register(new InfiniteBuffer<>()), commStatusOut, subscribeIn, summaryCall.getClient(), adCall.getClient(), rosterCall.getClient(), localDataCall.getClient(), dataCall.getServer())
        })).start();
        return new UiInterface(dataOwner, adUpdatedSplitter.register(new InfiniteBuffer<>()), summaryUpdatedSplitter.register(new InfiniteBuffer<>()), commStatusIn, newDataOut, subscribeOut, getRosterOut, dataCall.getClient(), rosterCall.getClient(), adCall.getClient());
    }

    public static class UiInterface {
        public final DataOwner dataOwner;
        public final AltingChannelInput<Advertisement> adIn;
        public final AltingChannelInput<Summary> summaryIn;
        public final AltingChannelInput<Pair<Comm,Boolean>> commStatusIn;
        public final ChannelOutput<Data> newDataOut;
        public final ChannelOutput<List<Comm>> subscribeOut;
        public final ChannelOutput<String> getRosterOut;
        public final FCClient<UUID, Pair<String, InputStream>> dataCall;
        public final FCClient<Void, List<Advertisement>> rosterCall;
        public final FCClient<UUID, Advertisement> adCall;
        
        public UiInterface(DataOwner dataOwner, AltingChannelInput<Advertisement> adIn, AltingChannelInput<Summary> summaryIn, AltingChannelInput<Pair<Comm, Boolean>> commStatusIn, ChannelOutput<Data> newDataOut, ChannelOutput<List<Comm>> subscribeOut, ChannelOutput<String> getRosterOut, FCClient<UUID, Pair<String, InputStream>> dataCall, FCClient<Void, List<Advertisement>> rosterCall, FCClient<UUID, Advertisement> adCall) {
            this.dataOwner = dataOwner;
            this.adIn = adIn;
            this.summaryIn = summaryIn;
            this.commStatusIn = commStatusIn;
            this.newDataOut = newDataOut;
            this.subscribeOut = subscribeOut;
            this.getRosterOut = getRosterOut;
            this.dataCall = dataCall;
            this.rosterCall = rosterCall;
            this.adCall = adCall;
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
