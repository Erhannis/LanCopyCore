/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor;

import com.erhannis.lancopy.DataOwner;
import com.esotericsoftware.kryo.Kryo;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import jcsp.helpers.JcspUtils;
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.CSProcess;
import jcsp.lang.CSTimer;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;
import jcsp.lang.DisableableTimer;
import jcsp.lang.Guard;
import jcsp.lang.PoisonException;
import jcsp.util.InfiniteBuffer;

/**
 *
 * @author erhannis
 */
public class MulticastAdvertiser implements CSProcess {

    public static class MulticastReceiver extends Thread {

        private static final boolean TRUE = "".isEmpty(); // Ugh

        protected MulticastSocket socket = null;
        protected byte[] buf = new byte[65507];

        public final int port;
        public final String address;
        
        public final AltingChannelInput<byte[]> msgIn; //TODO byte[]?
        private final ChannelOutput<byte[]> msgOut;

        public MulticastReceiver(int port, String address) {
            this(port, address, Channel.<byte[]>any2one(new InfiniteBuffer<byte[]>(), 1));
        }

        private MulticastReceiver(int port, String address, Any2OneChannel<byte[]> msgChannel) {
            this(port, address, msgChannel.in(), JcspUtils.logDeadlock(msgChannel.out()));
        }

        public MulticastReceiver(int port, String address, ChannelOutput<byte[]> msgOut) {
            this(port, address, null, msgOut);
        }

        private MulticastReceiver(int port, String address, AltingChannelInput<byte[]> msgIn, ChannelOutput<byte[]> msgOut) {
            this.port = port;
            this.address = address;
            this.msgIn = msgIn;
            this.msgOut = msgOut;
        }
        
        private static byte[] copyData(DatagramPacket packet) {
            byte[] buf = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), packet.getOffset(), buf, 0, buf.length);
            return buf;
        }
        
        public void run() {
            System.out.println(">>MulticastAdvertiser");
            InetAddress group;
            try {
                socket = new MulticastSocket(port);
                group = InetAddress.getByName(address);
                socket.joinGroup(group);
                while (TRUE) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        socket.receive(packet);
                        //String received = new String(packet.getData(), 0, packet.getLength());
                        //System.out.println("MA rx " + received);
                        msgOut.write(copyData(packet));
                    } catch (SocketException | PoisonException ex) {
                        System.out.println("<<MulticastAdvertiser");
                        break;
                    } catch (IOException ex) {
                        Logger.getLogger(MulticastAdvertiser.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                socket.leaveGroup(group);
                socket.close();
            } catch (IOException ex) {
                Logger.getLogger(MulticastAdvertiser.class.getName()).log(Level.SEVERE, null, ex);
                return; //TODO ??
            }
        }

        public void shutdown() {
            try {
                this.socket.close();
                this.msgOut.poison(10);
            } catch (Exception e) {
            }
        }
    }

    public static class MulticastPublisher {

        private final MulticastSocket socket;
        private final InetAddress group;
        private final int port;

        public MulticastPublisher(int port, String address) throws IOException {
            this.socket = new MulticastSocket();
            this.group = InetAddress.getByName(address);
            this.port = port;
        }

        public void multicast(byte[] multicastMessage) throws IOException {
            DatagramPacket packet = new DatagramPacket(multicastMessage, multicastMessage.length, group, port);
            socket.send(packet);
            //socket.close(); //TODO ??
        }
    }

    private final DataOwner dataOwner;
    private final long rebroadcastInterval;

    private final MulticastReceiver mr;
    private final MulticastPublisher mp;

    private final ChannelOutput<Advertisement> rxAdOut;
    private final AltingChannelInput<Advertisement> txAdIn;

    public MulticastAdvertiser(DataOwner dataOwner, ChannelOutput<Advertisement> rxAdOut, AltingChannelInput<Advertisement> txAdIn) throws IOException {
        this.dataOwner = dataOwner;
        this.rxAdOut = rxAdOut;
        this.txAdIn = txAdIn;
        int port = (int) dataOwner.options.getOrDefault("Multicast.port", 12113);
        String address = (String) dataOwner.options.getOrDefault("Multicast.address", "234.119.187.64");
        this.rebroadcastInterval = (long) dataOwner.options.getOrDefault("Multicast.rebroadcast_interval", dataOwner.options.getOrDefault("Advertisers.rebroadcast_interval", 30000L), false);
        dataOwner.errOnce("MulticastAdvertiser //TODO Deal with multiple interfaces?");
        this.mr = new MulticastReceiver(port, address);
        this.mp = new MulticastPublisher(port, address);
    }

    @Override
    public void run() {
        AltingChannelInput<byte[]> multicastIn = mr.msgIn;
        DisableableTimer rebroadcastTimer = new DisableableTimer();
        if (rebroadcastInterval < 0) {
            rebroadcastTimer.turnOff();
        } else {
            rebroadcastTimer.setAlarm(rebroadcastTimer.read() + rebroadcastInterval);
        }
        rebroadcastTimer.sleep(1000);
        Alternative alt = new Alternative(new Guard[]{txAdIn, multicastIn, rebroadcastTimer});
        try {
            this.mr.start();
            Advertisement lastAd = null;
            while (true) {
                switch (alt.priSelect()) {
                    case 0: // txAdIn
                    {
                        Advertisement ad = txAdIn.read();
                        if (Objects.equals(ad.id, dataOwner.ID)) {
                            try {
                                System.out.println("MulticastAdvertiser txa " + ad);
                                byte[] msg = dataOwner.serialize(ad);
                                System.out.println("MulticastAdvertiser txb " + new String(msg));
                                mp.multicast(msg);
                                lastAd = ad; //TODO Should move to before?
                            } catch (IOException ex) {
                                Logger.getLogger(MulticastAdvertiser.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                        break;
                    }
                    case 1: // multicastIn
                    {
                        byte[] msg = multicastIn.read();
                        System.out.println("MulticastAdvertiser rx " + new String(msg));
                        Object o = null;
                        try {
                            o = dataOwner.deserialize(msg);
                        } catch (Exception e) {
                            e.printStackTrace();
                            break;
                        }
                        if (o instanceof Advertisement) {
                            System.out.println("MA rx Advertisement : " + o);
                            rxAdOut.write((Advertisement)o);
                        } else {
                            System.err.println("MA rx non-Advertisement!");
                        }
                        break;
                    }
                    case 2: // rebroadcastTimer
                    {
                        rebroadcastTimer.setAlarm(rebroadcastTimer.read() + rebroadcastInterval);
                        if (lastAd != null) {
                            try {
                                System.out.println("MulticastAdvertiser rtxa " + lastAd);
                                byte[] msg = dataOwner.serialize(lastAd);
                                System.out.println("MulticastAdvertiser rtxb " + new String(msg));
                                mp.multicast(msg);
                            } catch (IOException ex) {
                                Logger.getLogger(MulticastAdvertiser.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                        break;
                    }
                }
            }
        } finally {
            mr.shutdown();
        }
    }
}
