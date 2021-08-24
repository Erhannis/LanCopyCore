/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor;

import com.erhannis.lancopy.DataOwner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import jcsp.helpers.CacheProcess;
import jcsp.helpers.FCClient;
import jcsp.helpers.JcspUtils;
import jcsp.lang.AltingChannelInput;
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
        
        Any2OneChannel<Advertisement> rxAdChannel = Channel.<Advertisement>any2one(new InfiniteBuffer<Advertisement>());
        AltingChannelInput<Advertisement> rxAdIn = rxAdChannel.in();
        ChannelOutput<Advertisement> rxAdOut = rxAdChannel.out();
        
        //TODO Maybe put the summary Call in the NodeRoster
        //NodeRoster roster = new NodeRoster(rxAdIn, );
        NodeRoster roster = null;
        
        new ProcessManager(new Parallel(new CSProcess[]{
            mockUI(roster.adUpdatedOut.register()),
            roster,
            new MulticastAdvertiser(dataOwner, rxAdOut, roster.adUpdatedOut.register()),
            () -> {
                AltingChannelInput<Advertisement> rosterIn = roster.adUpdatedOut.register();
                while (true) {
                    System.out.println("loop read");
                    System.out.println("loop got roster " + rosterIn.read());
                }
            },
            () -> {
                CSTimer timer = new CSTimer();
                String id = UUID.randomUUID().toString();
                while (true) {
                    Advertisement ad = new Advertisement(id, System.currentTimeMillis(), new ArrayList<Comm>());
                    System.out.println("loop send BROKEN");
                    //txAdOut.write(ad);
                    timer.sleep(1000);
                }
            },
            () -> {
                
            }
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
