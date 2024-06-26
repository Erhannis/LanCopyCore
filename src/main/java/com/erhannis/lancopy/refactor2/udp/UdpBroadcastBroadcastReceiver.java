/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2.udp;

import com.erhannis.lancopy.refactor2.BroadcastReceiver;
import com.erhannis.mathnstuff.MeUtils;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jcsp.helpers.JcspUtils;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;
import jcsp.lang.PoisonException;
import jcsp.util.InfiniteBuffer;

/**
 * 
 * @author erhannis
 */
public class UdpBroadcastBroadcastReceiver extends BroadcastReceiver {

    private static final boolean TRUE = "".isEmpty(); // Ugh

    protected DatagramSocket socket = null;
    protected byte[] buf = new byte[65507];

    public final int port;

    public final AltingChannelInput<byte[]> rxMsgIn;
    private final ChannelOutput<byte[]> rxMsgOut;

    //TODO Should this exist?  Or should we require pass the channel?
    public UdpBroadcastBroadcastReceiver(int port) {
        this(port, Channel.<byte[]>any2one(new InfiniteBuffer<byte[]>(), 1));
    }

    private UdpBroadcastBroadcastReceiver(int port, Any2OneChannel<byte[]> msgChannel) {
        this(port, msgChannel.in(), JcspUtils.logDeadlock(msgChannel.out()));
    }

    public UdpBroadcastBroadcastReceiver(int port, ChannelOutput<byte[]> msgOut) {
        this(port, null, msgOut);
    }

    private UdpBroadcastBroadcastReceiver(int port, AltingChannelInput<byte[]> rxMsgIn, ChannelOutput<byte[]> rxMsgOut) {
        this.port = port;
        this.rxMsgIn = rxMsgIn;
        this.rxMsgOut = rxMsgOut;
    }

    private static byte[] copyData(DatagramPacket packet) {
        byte[] buf = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), packet.getOffset(), buf, 0, buf.length);
        return buf;
    }

    @Override
    public void run() {
        System.out.println(">>UdpBroadcastBroadcastReceiver " + port);
        Thread.currentThread().setName("UdpBroadcastBroadcastReceiver");
        try {
            socket = new DatagramSocket(port);
            while (TRUE) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String received = MeUtils.cleanTextContent(new String(packet.getData(), 0, packet.getLength()), "�");
                    System.out.println("UdpBBR rx " + received);
                    rxMsgOut.write(copyData(packet));
                } catch (SocketException | PoisonException ex) {
                    System.out.println("<<UdpBroadcastBroadcastReceiver");
                    break;
                } catch (IOException ex) {
                    Logger.getLogger(UdpBroadcastBroadcastReceiver.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            socket.close();
        } catch (IOException ex) {
            Logger.getLogger(UdpBroadcastBroadcastReceiver.class.getName()).log(Level.SEVERE, null, ex);
            return; //TODO ??
        } finally {
            System.out.println("<<UdpBroadcastBroadcastReceiver");
        }
    }

    //TODO Different?
    public void shutdown() {
        try {
            this.socket.close();
            this.rxMsgOut.poison(10);
        } catch (Exception e) {
        }
    }
}
