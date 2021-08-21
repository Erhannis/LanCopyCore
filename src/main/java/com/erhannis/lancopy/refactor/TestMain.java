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
        DataOwner dataOwner = new DataOwner();

        Any2OneChannel<Advertisement> rxAdChannel = Channel.<Advertisement> any2one(new InfiniteBuffer<Advertisement>());
        AltingChannelInput<Advertisement> rxAdIn = rxAdChannel.in();
        ChannelOutput<Advertisement> rxAdOut = rxAdChannel.out();

        Any2OneChannel<Advertisement> txAdChannel = Channel.<Advertisement> any2one();
        AltingChannelInput<Advertisement> txAdIn = txAdChannel.in();
        ChannelOutput<Advertisement> txAdOut = txAdChannel.out();
        
        NodeRoster roster = new NodeRoster(rxAdIn);
        
        new ProcessManager(new Parallel(new CSProcess[] {
            roster,
            new MulticastAdvertiser(dataOwner, rxAdOut, txAdIn),
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
                    System.out.println("loop send");
                    txAdOut.write(ad);
                    timer.sleep(1000);
                }
            },
            () -> {
                
            }
        })).start();
        Thread.sleep(100000);
    }
}
