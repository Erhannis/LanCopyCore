/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.refactor.Advertisement;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.lancopy.refactor.tcp.TcpComm;
import com.erhannis.lancopy.refactor2.NodeManager.ChannelReader;
import com.erhannis.mathnstuff.Pair;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.CSProcess;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;
import jcsp.lang.Parallel;
import jcsp.lang.ProcessManager;

/**
 *
 * @author erhannis
 */
public class MainTest {
    public static void main(String[] args) {
        DataOwner dataOwner = new DataOwner("options1.dat");
        
        Any2OneChannel<byte[]> txMsgChannel = Channel.<byte[]> any2one();
        AltingChannelInput<byte[]> txMsgIn = txMsgChannel.in();
        ChannelOutput<byte[]> txMsgOut = txMsgChannel.out();

        Any2OneChannel<Pair<Object,byte[]>> rxMsgChannel = Channel.<Pair<Object,byte[]>> any2one();
        AltingChannelInput<Pair<Object,byte[]>> rxMsgIn = rxMsgChannel.in();
        ChannelOutput<Pair<Object,byte[]>> rxMsgOut = rxMsgChannel.out();
        
        Any2OneChannel<Object> shuffleChannelChannel = Channel.<Object> any2one();
        AltingChannelInput<Object> shuffleChannelIn = shuffleChannelChannel.in();
        ChannelOutput<Object> shuffleChannelOut = shuffleChannelChannel.out();
        
        Any2OneChannel<ChannelReader> channelReaderShuffleChannel = Channel.<ChannelReader> any2one();
        AltingChannelInput<ChannelReader> channelReaderShuffleIn = channelReaderShuffleChannel.in();
        ChannelOutput<ChannelReader> channelReaderShuffleOut = channelReaderShuffleChannel.out();
        
        Any2OneChannel<CommChannel> incomingConnectionChannel = Channel.<CommChannel> any2one();
        AltingChannelInput<CommChannel> incomingConnectionIn = incomingConnectionChannel.in();
        ChannelOutput<CommChannel> incomingConnectionOut = incomingConnectionChannel.out();
        
        Any2OneChannel<List<Comm>> subscribeChannel = Channel.<List<Comm>> any2one();
        AltingChannelInput<List<Comm>> subscribeIn = subscribeChannel.in();
        ChannelOutput<List<Comm>> subscribeOut = subscribeChannel.out();

        Any2OneChannel<Pair<Comm,Boolean>> commStatusChannel = Channel.<Pair<Comm,Boolean>> any2one();
        AltingChannelInput<Pair<Comm,Boolean>> commStatusIn = commStatusChannel.in();
        ChannelOutput<Pair<Comm,Boolean>> commStatusOut = commStatusChannel.out();
        
        int localPort = 10001;
        UUID remote = UUID.randomUUID();
        int remotePort = 10002;
        
        NodeManager nm = new NodeManager(dataOwner, remote, txMsgIn, rxMsgOut, shuffleChannelIn, channelReaderShuffleOut, incomingConnectionIn, subscribeIn, commStatusOut);
        
        new ProcessManager(new Parallel(new CSProcess[]{
            nm,
            () -> {
                while (true) {
                    System.out.println("rx msg: " + dataOwner.deserialize(rxMsgIn.read().b));
                }
            }, () -> {
                while (true) {
                    System.out.println("rx status: " + commStatusIn.read());
                }
            }
        })).start();
        
        List<Comm> comms = Lists.newArrayList(new TcpComm(null, "localhost", remotePort));
        Advertisement rad = new Advertisement(remote, System.currentTimeMillis(), comms, true, null);
        subscribeOut.write(rad.comms);
        
        while (true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ex) {
                Logger.getLogger(MainTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
