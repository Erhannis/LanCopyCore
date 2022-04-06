/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2.udp;

import com.erhannis.lancopy.refactor2.BroadcastTransmitter;
import com.erhannis.mathnstuff.MeUtils;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jcsp.lang.AltingChannelInput;

/**
 *
 * @author erhannis
 */
public class UdpMulticastBroadcastTransmitter extends BroadcastTransmitter {
    private final String address;
    private final int port;
    
    private final AltingChannelInput<byte[]> txMsgIn;
    
    public UdpMulticastBroadcastTransmitter(String address, int port, AltingChannelInput<byte[]> txMsgIn) {
        this.address = address;
        this.port = port;
        this.txMsgIn = txMsgIn;
    }
    
    @Override
    public void run() {
        System.out.println("UdpMulticastBroadcastTransmitter start, " + address + " " + port);
        Thread.currentThread().setName("UdpMulticastBroadcastTransmitter");
        MulticastSocket socket = null;
        InetAddress group = null;
        
        // Kinda horrible initialization
        while (socket == null) {
            MulticastSocket socket0 = null;
            try {
                socket0 = new MulticastSocket();
                socket = socket0;
            } catch (IOException ex) {
                Logger.getLogger(UdpMulticastBroadcastTransmitter.class.getName()).log(Level.SEVERE, null, ex);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex1) {
                    Logger.getLogger(UdpMulticastBroadcastTransmitter.class.getName()).log(Level.SEVERE, null, ex1);
                }
            }
        }
        while (group == null) {
            try {
                group = InetAddress.getByName(address);
            } catch (UnknownHostException ex) {
                Logger.getLogger(UdpMulticastBroadcastTransmitter.class.getName()).log(Level.SEVERE, null, ex);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex1) {
                    Logger.getLogger(UdpMulticastBroadcastTransmitter.class.getName()).log(Level.SEVERE, null, ex1);
                }
            }
        }
        
        // Loop transmit
        while (true) {
            try {
                byte[] msg = txMsgIn.read();
                String transmitted = MeUtils.cleanTextContent(new String(msg), "�");
                System.out.println("UdpMBT tx " + transmitted);
                DatagramPacket packet = new DatagramPacket(msg, msg.length, group, port);
                socket.send(packet);
            } catch (IOException ex) {
                Logger.getLogger(UdpMulticastBroadcastTransmitter.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
