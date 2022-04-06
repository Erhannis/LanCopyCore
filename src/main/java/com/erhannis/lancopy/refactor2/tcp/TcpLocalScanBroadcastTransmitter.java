/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2.tcp;

import com.erhannis.lancopy.refactor2.udp.*;
import com.erhannis.lancopy.refactor2.BroadcastTransmitter;
import com.erhannis.mathnstuff.MeUtils;
import com.erhannis.mathnstuff.utils.Options.LiveOption;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import jcsp.helpers.NameParallel;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.CSProcess;
import jcsp.lang.ChannelOutput;
import jcsp.lang.ProcessManager;

/**
 *
 * @author erhannis
 */
public class TcpLocalScanBroadcastTransmitter extends BroadcastTransmitter {
    private final LiveOption<Boolean> enabled;
    private final int port;
    private final LiveOption<Integer> maxAdSize;
    
    private final ChannelOutput<byte[]> rxMsgOut;
    private final AltingChannelInput<byte[]> txMsgIn;

    public TcpLocalScanBroadcastTransmitter(LiveOption<Boolean> enabled, int port, LiveOption<Integer> maxAdSize, ChannelOutput<byte[]> rxMsgOut, AltingChannelInput<byte[]> txMsgIn) {
        this.enabled = enabled;
        this.port = port;
        this.maxAdSize = maxAdSize;
        this.rxMsgOut = rxMsgOut;
        this.txMsgIn = txMsgIn;
    }

    public static String[] enumerateTargets() {
        ArrayList<String> broadcastList = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces;
        try {
            interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                try {
                    NetworkInterface ni = interfaces.nextElement();

                    if (ni.isLoopback() || !ni.isUp()) {
                        continue;
                    }

                    ni.getInterfaceAddresses().stream()
                            .map(a -> a.getAddress())
                            .filter(Objects::nonNull)
                            .map(a -> a.getHostAddress())
                            .filter(a -> a.matches("192\\.168\\..*"))
                            .forEach(a -> {
                                String base = a.replaceFirst("\\.[0-9]+$", ".");
                                System.out.println("TcpLocalScanBroadcastTransmitter adding addresses " + (base + "*"));
                                for (int i = 0; i <= 255; i++) {
                                    broadcastList.add(base + i);
                                }
                            });
                } catch (Throwable t) {
                    Logger.getLogger(TcpLocalScanBroadcastTransmitter.class.getName()).log(Level.FINE, null, t);
                }
            }
        } catch (SocketException ex) {
            Logger.getLogger(TcpLocalScanBroadcastTransmitter.class.getName()).log(Level.SEVERE, null, ex);
        }
        return broadcastList.toArray(new String[0]);
    }
    
    @Override
    public void run() {
        System.out.println("TcpLocalScanBroadcastTransmitter start, " + port);
        Thread.currentThread().setName("TcpLocalScanBroadcastTransmitter");

        // Loop transmit
        while (true) {
            try {
                byte[] txMsg = txMsgIn.read();
                if (!enabled.fetch()) {
                    continue;
                }
                String transmitted = MeUtils.cleanTextContent(new String(txMsg), "�");
                System.out.println("TcpLSBT tx " + transmitted);
                String[] targets = enumerateTargets();
                System.out.println("TcpLSBT trying " + targets.length + " addresses");
                for (String target : targets) {
                    new ProcessManager(() -> {
                        try {
                            Thread.currentThread().setName("TcpLSBT out " + target + " : " + port);
                            SocketChannel sc = SocketChannel.open(new InetSocketAddress(target, port));
                            System.out.println("-->TcpLSBT connection");
                            try {
                                sc.write(ByteBuffer.wrap(txMsg));
                                
                                ByteBuffer incoming = ByteBuffer.allocate(maxAdSize.fetch()); //TODO Might be heavy, allocating this, frequently
                                sc.read(incoming); //TODO Not sure if guaranteed to get all bytes sent, in one go
                                byte[] rxMsg = Arrays.copyOf(incoming.array(), incoming.position());
                                String received = MeUtils.cleanTextContent(new String(rxMsg), "�");
                                System.out.println("TcpLSBT rx " + received);
                                rxMsgOut.write(rxMsg);
                            } catch (IOException ex) {
                                Logger.getLogger(TcpLocalScanBroadcastTransmitter.class.getName()).log(Level.SEVERE, null, ex);
                            } finally {
                                System.out.println("<--TcpLSBT connection");
                            }
                        } catch (IOException ex) {
                        }
                    }).start();
                }
            } catch (Throwable t) {
                Logger.getLogger(TcpLocalScanBroadcastTransmitter.class.getName()).log(Level.SEVERE, null, t);
            }
        }
    }
}
