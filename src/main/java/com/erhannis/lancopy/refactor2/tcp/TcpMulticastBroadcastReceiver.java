/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2.tcp;

import com.erhannis.lancopy.refactor2.BroadcastReceiver;
import com.erhannis.mathnstuff.MeUtils;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
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
public class TcpMulticastBroadcastReceiver extends BroadcastReceiver {

    private static final boolean TRUE = "".isEmpty(); // Ugh

    protected MulticastSocket socket = null;
    protected byte[] buf = new byte[65507];

    public final int port;
    public final String address;

    public final AltingChannelInput<byte[]> msgIn; //TODO byte[]?
    private final ChannelOutput<byte[]> msgOut;

    //TODO Should this exist?  Or should we require pass the channel?
    public TcpMulticastBroadcastReceiver(int port, String address) {
        this(port, address, Channel.<byte[]>any2one(new InfiniteBuffer<byte[]>(), 1));
    }

    private TcpMulticastBroadcastReceiver(int port, String address, Any2OneChannel<byte[]> msgChannel) {
        this(port, address, msgChannel.in(), JcspUtils.logDeadlock(msgChannel.out()));
    }

    public TcpMulticastBroadcastReceiver(int port, String address, ChannelOutput<byte[]> msgOut) {
        this(port, address, null, msgOut);
    }

    private TcpMulticastBroadcastReceiver(int port, String address, AltingChannelInput<byte[]> msgIn, ChannelOutput<byte[]> msgOut) {
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

    @Override
    public void run() {
        System.out.println(">>TcpMulticastBroadcastReceiver " + address + " " + port);
        InetAddress group;
        try {
            socket = new MulticastSocket(port);
            group = InetAddress.getByName(address);
            socket.joinGroup(group);
            while (TRUE) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String received = MeUtils.cleanTextContent(new String(packet.getData(), 0, packet.getLength()), "�");
                    System.out.println("TcpMBR rx " + received);
                    msgOut.write(copyData(packet));
                } catch (SocketException | PoisonException ex) {
                    System.out.println("<<TcpMulticastBroadcastReceiver");
                    break;
                } catch (IOException ex) {
                    Logger.getLogger(TcpMulticastBroadcastReceiver.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            socket.leaveGroup(group);
            socket.close();
        } catch (IOException ex) {
            Logger.getLogger(TcpMulticastBroadcastReceiver.class.getName()).log(Level.SEVERE, null, ex);
            return; //TODO ??
        }
    }

    //TODO Different?
    public void shutdown() {
        try {
            this.socket.close();
            this.msgOut.poison(10);
        } catch (Exception e) {
        }
    }
}
