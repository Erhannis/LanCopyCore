/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.data.Data;
import com.erhannis.lancopy.refactor.tcp.TcpGetComm;
import com.erhannis.lancopy.refactor.tcp.TcpPutComm;
import com.erhannis.mathnstuff.Pair;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jcsp.helpers.CacheProcess;
import jcsp.helpers.FCClient;
import jcsp.helpers.JcspUtils;
import jcsp.helpers.SynchronousSplitter;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.AltingFunctionChannel;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.CSProcess;
import jcsp.lang.CSTimer;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;
import jcsp.lang.Parallel;
import jcsp.lang.ProcessManager;
import jcsp.util.InfiniteBuffer;

/**
 *
 * @author erhannis
 */
public class TestMain {

    public static void main(String[] args) throws InterruptedException, IOException {
        if (1==0) {
            CSTimer t = new CSTimer();
            System.out.println(">>sleep 1000");
            JcspUtils.logDeadlock(() -> {
                t.sleep(1000);
            });
            System.out.println("<<sleep 1000");
            System.out.println(">>sleep 5000");
            JcspUtils.logDeadlock(() -> {
                t.sleep(5000);
            });
            System.out.println("<<sleep 5000");
            System.out.println(">>sleep 15000");
            String r = JcspUtils.logDeadlock(() -> {
                t.sleep(15000);
                return "adsf";
            });
            System.out.println(r);
            System.out.println("<<sleep 15000");
            if (1==1) {
                return;
            }
        }
        if (1==0) {
            CSTimer timer = new CSTimer();
            CacheProcess<String> cp = new CacheProcess<String>(10);
            new ProcessManager(new Parallel(new CSProcess[]{
                cp,
                () -> {
                    Thread.currentThread().setName("writer process");
                    for (int i = 0; i < 10; i++) {
                        cp.write(i + " bananas");
                        System.out.println("wp " + i + " bananas");
                        timer.sleep(1000);
                    }
                    cp.poison(10);
                },
                () -> {
                    Thread.currentThread().setName("reader process 1");
                    AltingChannelInput<String> aci = cp.register();
                    while (true) {
                        System.out.println("rp1 " + aci.read());
                    }
                },
                () -> {
                    Thread.currentThread().setName("reader process 2");
                    AltingChannelInput<String> aci = cp.register();
                    while (true) {
                        System.out.println("rp2 " + aci.read());
                    }
                },
                () -> {
                    Thread.currentThread().setName("getreader process");
                    FCClient<Void, String> cpg = cp.getFC;
                    while (true) {
                        System.out.println("grp " + cpg.call(null));
                        timer.sleep(314);
                    }
                }
            })).run();
            if (1 == 1) {
                return;
            }
        }
        DataOwner dataOwner = new DataOwner();
        
        Any2OneChannel<Advertisement> rxAdChannel = Channel.<Advertisement>any2one(new InfiniteBuffer<>());
        AltingChannelInput<Advertisement> rxAdIn = rxAdChannel.in();
        ChannelOutput<Advertisement> rxAdOut = JcspUtils.logDeadlock(rxAdChannel.out());
        
        Any2OneChannel<Summary> summaryToTrackerChannel = Channel.<Summary> any2one(new InfiniteBuffer<>());
        AltingChannelInput<Summary> summaryToTrackerIn = summaryToTrackerChannel.in();
        ChannelOutput<Summary> summaryToTrackerOut = JcspUtils.logDeadlock(summaryToTrackerChannel.out());
        
        Any2OneChannel<Data> newDataChannel = Channel.<Data> any2one();
        AltingChannelInput<Data> newDataIn = newDataChannel.in();
        ChannelOutput<Data> newDataOut = JcspUtils.logDeadlock(newDataChannel.out());

        Any2OneChannel<Advertisement> subscribeChannel = Channel.<Advertisement> any2one();
        AltingChannelInput<Advertisement> subscribeIn = subscribeChannel.in();
        ChannelOutput<Advertisement> subscribeOut = JcspUtils.logDeadlock(subscribeChannel.out());

        Any2OneChannel<Pair<Comm,Boolean>> tcpStatusChannel = Channel.<Pair<Comm,Boolean>> any2one();
        AltingChannelInput<Pair<Comm,Boolean>> tcpStatusIn = tcpStatusChannel.in();
        ChannelOutput<Pair<Comm,Boolean>> tcpStatusOut = JcspUtils.logDeadlock(tcpStatusChannel.out());
        
        Any2OneChannel<List<Comm>> commsChannel = Channel.<List<Comm>> any2one();
        AltingChannelInput<List<Comm>> commsIn = commsChannel.in();
        ChannelOutput<List<Comm>> commsOut = JcspUtils.logDeadlock(commsChannel.out());

        
        SynchronousSplitter<Advertisement> adUpdatedSplitter = new SynchronousSplitter<>();
        SynchronousSplitter<Summary> summaryUpdatedSplitter = new SynchronousSplitter<>();
        
        AltingFunctionChannel<String, Advertisement> adCall = new AltingFunctionChannel<>();
        AltingFunctionChannel<List<Comm>, Pair<String, InputStream>> dataCall = new AltingFunctionChannel<>();
        AltingFunctionChannel<String, Summary> summaryCall = new AltingFunctionChannel<>();
        AltingFunctionChannel<Void, List<Advertisement>> rosterCall = new AltingFunctionChannel<>();
        AltingFunctionChannel<Void, Data> localDataCall = new AltingFunctionChannel<>();
        
        //TODO Maybe put the summary Call in the NodeTracker
        
        new ProcessManager(new Parallel(new CSProcess[]{
            adUpdatedSplitter,
            summaryUpdatedSplitter,
            new NodeTracker(adUpdatedSplitter, summaryUpdatedSplitter, rxAdIn, summaryToTrackerIn, adCall.getServer(), summaryCall.getServer(), rosterCall.getServer()),
            new LocalData(dataOwner, localDataCall.getServer(), summaryToTrackerOut, newDataIn),
            new MulticastAdvertiser(dataOwner, rxAdOut, adUpdatedSplitter.register()),
            new TcpGetComm(dataOwner, subscribeIn, summaryToTrackerOut, rxAdOut, dataCall.getServer(), adCall.getClient(), tcpStatusOut),
            new TcpPutComm(dataOwner, commsOut, rxAdOut, adUpdatedSplitter.register(), summaryUpdatedSplitter.register(), localDataCall.getClient(), summaryCall.getClient(), rosterCall.getClient()),
            mockUI(adUpdatedSplitter.register(), summaryUpdatedSplitter.register(), newDataOut, subscribeOut, dataCall.getClient())
        })).start();
        Thread.sleep(100000);
    }

    private static CSProcess mockUI(Object... o) {
        return new CSProcess() {
            @Override
            public void run() {
            }
        };
    }
}
