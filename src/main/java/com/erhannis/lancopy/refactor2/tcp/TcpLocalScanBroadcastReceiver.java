/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2.tcp;

import com.erhannis.lancopy.refactor2.BroadcastReceiver;
import com.erhannis.mathnstuff.MeUtils;
import com.erhannis.mathnstuff.utils.DThread;
import com.erhannis.mathnstuff.utils.Options.LiveOption;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import jcsp.helpers.JcspUtils;
import jcsp.helpers.NameParallel;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.CSProcess;
import jcsp.lang.CSTimer;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;
import jcsp.lang.PoisonException;
import jcsp.lang.ProcessManager;
import jcsp.util.InfiniteBuffer;

/**
 * 
 * @author erhannis
 */
public class TcpLocalScanBroadcastReceiver extends BroadcastReceiver {

    private static final boolean TRUE = "".isEmpty(); // Ugh

    protected ServerSocketChannel ssc = null;
    private byte[] cachedTxMsg = new byte[0];

    private final LiveOption<Boolean> enabled;
    public final int port;
    public final LiveOption<Integer> maxAdSize;

    private final AltingChannelInput<byte[]> txMsgIn;
    public final ChannelOutput<byte[]> txMsgOut;
    public final AltingChannelInput<byte[]> rxMsgIn;
    private final ChannelOutput<byte[]> rxMsgOut;

    //TODO Should this exist?  Or should we require pass the channel?
    public TcpLocalScanBroadcastReceiver(LiveOption<Boolean> enabled, int port, LiveOption<Integer> maxAdSize) {
        this(enabled, port, maxAdSize, Channel.<byte[]>any2one(new InfiniteBuffer<byte[]>(), 1), Channel.<byte[]>any2one(new InfiniteBuffer<byte[]>(), 1));
    }

    private TcpLocalScanBroadcastReceiver(LiveOption<Boolean> enabled, int port, LiveOption<Integer> maxAdSize, Any2OneChannel<byte[]> txMsgChannel, Any2OneChannel<byte[]> rxMsgChannel) {
        this(enabled, port, maxAdSize, txMsgChannel.in(), JcspUtils.logDeadlock(txMsgChannel.out()), rxMsgChannel.in(), JcspUtils.logDeadlock(rxMsgChannel.out()));
    }

    public TcpLocalScanBroadcastReceiver(LiveOption<Boolean> enabled, int port, LiveOption<Integer> maxAdSize, AltingChannelInput<byte[]> txMsgIn, ChannelOutput<byte[]> rxMsgOut) {
        this(enabled, port, maxAdSize, txMsgIn, null, null, rxMsgOut);
    }

    private TcpLocalScanBroadcastReceiver(LiveOption<Boolean> enabled, int port, LiveOption<Integer> maxAdSize, AltingChannelInput<byte[]> txMsgIn, ChannelOutput<byte[]> txMsgOut, AltingChannelInput<byte[]> rxMsgIn, ChannelOutput<byte[]> rxMsgOut) {
        this.enabled = enabled;
        this.port = port;
        this.maxAdSize = maxAdSize;
        this.txMsgIn = txMsgIn;
        this.txMsgOut = txMsgOut;
        this.rxMsgIn = rxMsgIn;
        this.rxMsgOut = rxMsgOut;
    }

    @Override
    public void run() {
        System.out.println("-->TcpLocalScanBroadcastReceiver " + port);
        Thread.currentThread().setName("TcpLocalScanBroadcastReceiver");
        new NameParallel(new CSProcess[] {
            () -> {
                Thread.currentThread().setName("TcpLocalScanBroadcastReceiver cacher");
                while (true) {
                    cachedTxMsg = txMsgIn.read();
                }
            }, () -> {
                Thread.currentThread().setName("TcpLocalScanBroadcastReceiver server");
                CSTimer timer = new CSTimer();
                while (TRUE) {
                    while (!enabled.fetch()) {
                        timer.sleep(1000);
                    }
                    try {
                        ssc = ServerSocketChannel.open();
                        ssc.socket().bind(new InetSocketAddress(port));
                        while (TRUE) {
                            try {
                                SocketChannel sc = ssc.accept();
                                if (!enabled.fetch()) {
                                    sc.close();
                                    ssc.close();
                                    break;
                                }
                                new ProcessManager(() -> {
                                    System.out.println("-->TcpLSBR connection");
                                    try {
                                        Thread.currentThread().setName("TcpLSBR connection");
                                        ByteBuffer incoming = ByteBuffer.allocate(maxAdSize.fetch()); //TODO Might be heavy, allocating this, frequently
                                        sc.read(incoming); //TODO Not sure if guaranteed to get all bytes sent, in one go
                                        byte[] rxMsg = Arrays.copyOf(incoming.array(), incoming.position());
                                        String received = MeUtils.cleanTextContent(new String(rxMsg), "�");
                                        System.out.println("TcpLSBR rx " + received);

                                        rxMsgOut.write(rxMsg);
                                        sc.write(ByteBuffer.wrap(cachedTxMsg));
                                    } catch (IOException ex) {
                                        Logger.getLogger(TcpLocalScanBroadcastReceiver.class.getName()).log(Level.SEVERE, null, ex);
                                    } finally {
                                        System.out.println("<--TcpLSBR connection");
                                    }
                                }).start();
                            } catch (IOException ex) {
                                Logger.getLogger(TcpLocalScanBroadcastReceiver.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(TcpLocalScanBroadcastReceiver.class.getName()).log(Level.SEVERE, null, ex);
                        timer.sleep(1000);
                    }
                }
                System.out.println("<--TcpLocalScanBroadcastReceiver");
            }
        }).run();
    }

    //TODO Different?
    public void shutdown() {
        try {
            this.ssc.close();
            this.txMsgIn.poison(10);
            this.rxMsgOut.poison(10);
        } catch (Exception e) {
        }
    }
}
