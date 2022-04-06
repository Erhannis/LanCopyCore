/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2.udp;

import com.erhannis.lancopy.refactor2.BroadcastTransmitter;
import com.erhannis.mathnstuff.MeUtils;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import jcsp.lang.AltingChannelInput;

/**
 *
 * @author erhannis
 */
public class UdpBroadcastBroadcastTransmitter extends BroadcastTransmitter {
    private final String address;
    private final int port;
    
    private final AltingChannelInput<byte[]> txMsgIn;

    public UdpBroadcastBroadcastTransmitter(String address, int port, AltingChannelInput<byte[]> txMsgIn) {
        this.address = address;
        this.port = port;
        this.txMsgIn = txMsgIn;
    }

    public static String[] enumerateBroadcastAddresses() {
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
                                System.out.println("UdpBroadcastBroadcastTransmitter found broadcast address " + a);
                                broadcastList.add(a);
                            });
                } catch (Throwable t) {
                    Logger.getLogger(UdpBroadcastBroadcastTransmitter.class.getName()).log(Level.FINE, null, t);
                }
            }
        } catch (SocketException ex) {
            Logger.getLogger(UdpBroadcastBroadcastTransmitter.class.getName()).log(Level.SEVERE, null, ex);
        }
        return broadcastList.toArray(new String[0]);
    }
    
    @Override
    public void run() {
        System.out.println("UdpBroadcastBroadcastTransmitter start, " + address + " " + port);
        DatagramSocket socket = null;
        InetAddress group = null;
        
        // Kinda horrible initialization
        while (socket == null) {
            DatagramSocket socket0 = null;
            try {
                socket0 = new DatagramSocket();
                socket0.setBroadcast(true);
                socket = socket0;
            } catch (SocketException ex) {
                Logger.getLogger(UdpBroadcastBroadcastTransmitter.class.getName()).log(Level.SEVERE, null, ex);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex1) {
                    Logger.getLogger(UdpBroadcastBroadcastTransmitter.class.getName()).log(Level.SEVERE, null, ex1);
                }
            }
        }
        while (group == null) {
            try {
                group = InetAddress.getByName(address);
            } catch (UnknownHostException ex) {
                Logger.getLogger(UdpBroadcastBroadcastTransmitter.class.getName()).log(Level.SEVERE, null, ex);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex1) {
                    Logger.getLogger(UdpBroadcastBroadcastTransmitter.class.getName()).log(Level.SEVERE, null, ex1);
                }
            }
        }
        
        // Loop transmit
        while (true) {
            try {
                byte[] msg = txMsgIn.read();
                String transmitted = MeUtils.cleanTextContent(new String(msg), "�");
                System.out.println("UdpBBT tx " + transmitted);
                DatagramPacket packet = new DatagramPacket(msg, msg.length, group, port);
                socket.send(packet);
            } catch (IOException ex) {
                Logger.getLogger(UdpBroadcastBroadcastTransmitter.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
