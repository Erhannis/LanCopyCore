/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.mathnstuff.MeUtils;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import jcsp.helpers.JcspUtils;
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.CSProcess;
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
public class BroadcastAdvertiser implements CSProcess {

    public static class BroadcastReceiver extends Thread {

        private static final boolean TRUE = "".isEmpty(); // Ugh

        protected DatagramSocket socket = null;
        protected byte[] buf = new byte[65507];

        public final int port;
        
        public final AltingChannelInput<byte[]> msgIn; //TODO byte[]?
        private final ChannelOutput<byte[]> msgOut;

        public BroadcastReceiver(int port) {
            this(port, Channel.<byte[]>any2one(new InfiniteBuffer<byte[]>(), 1));
        }

        private BroadcastReceiver(int port, Any2OneChannel<byte[]> msgChannel) {
            this(port, msgChannel.in(), JcspUtils.logDeadlock(msgChannel.out()));
        }

        public BroadcastReceiver(int port, ChannelOutput<byte[]> msgOut) {
            this(port, null, msgOut);
        }

        private BroadcastReceiver(int port, AltingChannelInput<byte[]> msgIn, ChannelOutput<byte[]> msgOut) {
            this.port = port;
            this.msgIn = msgIn;
            this.msgOut = msgOut;
        }
        
        private static byte[] copyData(DatagramPacket packet) {
            byte[] buf = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), packet.getOffset(), buf, 0, buf.length);
            return buf;
        }
        
        public void run() {
            System.out.println(">>BroadcastAdvertiser " + port);
            try {
                socket = new DatagramSocket(port);
                while (TRUE) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        socket.receive(packet);
                        //String received = new String(packet.getData(), 0, packet.getLength());
                        //System.out.println("BA rx " + received);
                        msgOut.write(copyData(packet));
                    } catch (SocketException | PoisonException ex) {
                        System.out.println("<<BroadcastAdvertiser");
                        break;
                    } catch (IOException ex) {
                        Logger.getLogger(BroadcastAdvertiser.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                socket.close();
            } catch (IOException ex) {
                Logger.getLogger(BroadcastAdvertiser.class.getName()).log(Level.SEVERE, null, ex);
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

    public static class BroadcastPublisher {

        private final DatagramSocket socket;
        private final InetAddress group;
        private final int port;

        public BroadcastPublisher(int port, String address) throws IOException {
            System.out.println("BroadcastPublisher start, " + address + " " + port);
            this.socket = new DatagramSocket();
            this.socket.setBroadcast(true);
            this.group = InetAddress.getByName(address);
            this.port = port;
        }

        public void broadcast(byte[] broadcastMessage) throws IOException {
            DatagramPacket packet = new DatagramPacket(broadcastMessage, broadcastMessage.length, group, port);
            socket.send(packet);
            //socket.close(); //TODO ??
        }
    }

    private final DataOwner dataOwner;

    private final String[] addresses;
    private final int port;
    private final ChannelOutput<Advertisement> rxAdOut;
    private final AltingChannelInput<Advertisement> txAdIn;

    /**
     * `addresses` can be null, in which case BroadcastAdvertiser will enumerate
     * the interfaces (at run) and try to figure out the broadcast addresses.
     * @param addresses
     * @param port
     * @param dataOwner
     * @param rxAdOut
     * @param txAdIn
     * @throws IOException 
     */
    public BroadcastAdvertiser(String[] addresses, int port, DataOwner dataOwner, ChannelOutput<Advertisement> rxAdOut, AltingChannelInput<Advertisement> txAdIn) throws IOException {
        this.addresses = addresses;
        this.port = port;
        this.dataOwner = dataOwner;
        this.rxAdOut = rxAdOut;
        this.txAdIn = txAdIn;
    }

    @Override
    public void run() {
        final String[] addresses0;
        if (addresses != null) {
            addresses0 = addresses;
        } else {
            ArrayList<String> broadcastList = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces;
            try {
                interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    try {
                        NetworkInterface networkInterface = interfaces.nextElement();

                        if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                            continue;
                        }

                        networkInterface.getInterfaceAddresses().stream()
                                .map(a -> a.getBroadcast())
                                .filter(Objects::nonNull)
                                .map(a -> a.getHostAddress())
                                .forEach(a -> {
                                    System.out.println("BroadcastAdvertiser found broadcast address " + a);
                                    broadcastList.add(a);
                                });
                    } catch (Throwable t) {
                        Logger.getLogger(BroadcastAdvertiser.class.getName()).log(Level.FINE, null, t);
                    }
                }
            } catch (SocketException ex) {
                Logger.getLogger(BroadcastAdvertiser.class.getName()).log(Level.SEVERE, null, ex);
            }
            addresses0 = broadcastList.toArray(new String[0]);
        }
        
        BroadcastReceiver br = new BroadcastReceiver(port);
        ArrayList<BroadcastPublisher> bps = new ArrayList<BroadcastPublisher>();
        for (int i = 0; i < addresses0.length; i++) {
            String address = addresses0[i];
            try {
                bps.add(new BroadcastPublisher(port, address));
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        Consumer<byte[]> broadcast = msg -> {
            for (BroadcastPublisher bp : bps) {
                try {
                    bp.broadcast(msg);
                } catch (IOException ex) {
                    Logger.getLogger(BroadcastAdvertiser.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };

        AltingChannelInput<byte[]> broadcastIn = br.msgIn;
        DisableableTimer rebroadcastTimer = new DisableableTimer();
        long rebroadcastInterval = (long) dataOwner.options.getOrDefault("Broadcast.rebroadcast_interval", dataOwner.options.getOrDefault("Advertisers.rebroadcast_interval", 30000L), false);
        if (rebroadcastInterval < 0) {
            rebroadcastTimer.turnOff();
        } else {
            rebroadcastTimer.setAlarm(rebroadcastTimer.read() + rebroadcastInterval);
        }
        Alternative alt = new Alternative(new Guard[]{txAdIn, broadcastIn, rebroadcastTimer});
        try {
            br.start();
            Advertisement lastAd = null;
            while (true) {
                switch (alt.priSelect()) {
                    case 0: // txAdIn
                    {
                        Advertisement ad = txAdIn.read();
                        if (Objects.equals(ad.id, dataOwner.ID)) {
                            System.out.println("BroadcastAdvertiser  tx " + ad);
                            byte[] msg = dataOwner.serialize(ad);
                            //System.out.println("BroadcastAdvertiser  txb " + MeUtils.cleanTextContent(new String(msg), "�"));
                            broadcast.accept(msg);
                            lastAd = ad; //TODO Should move to before?
                        }
                        break;
                    }
                    case 1: // broadcastIn
                    {
                        byte[] msg = broadcastIn.read();
                        Object o = null;
                        try {
                            o = dataOwner.deserialize(msg);
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.out.println("BroadcastAdvertiser  rx  " + MeUtils.cleanTextContent(new String(msg), "�"));
                            break;
                        }
                        if (o instanceof Advertisement) {
                            System.out.println("BroadcastAdvertiser  rx Advertisement : " + o);
                            rxAdOut.write((Advertisement)o);
                        } else {
                            System.err.println("BroadcastAdvertiser  rx non-Advertisement!");
                        }
                        break;
                    }
                    case 2: // rebroadcastTimer
                    {
                        rebroadcastInterval = (long) dataOwner.options.getOrDefault("Broadcast.rebroadcast_interval", dataOwner.options.getOrDefault("Advertisers.rebroadcast_interval", 30000L), false);
                        rebroadcastTimer.setAlarm(rebroadcastTimer.read() + rebroadcastInterval);
                        if (lastAd != null) {
                            System.out.println("BroadcastAdvertiser rtx " + lastAd);
                            byte[] msg = dataOwner.serialize(lastAd);
                            //System.out.println("BroadcastAdvertiser rtxb " + MeUtils.cleanTextContent(new String(msg), "�"));
                            broadcast.accept(msg);
                        }
                        break;
                    }
                }
            }
        } finally {
            br.shutdown();
        }
    }
}
