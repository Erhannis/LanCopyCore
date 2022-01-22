/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2.tcp;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.refactor.tcp.TcpComm;
import com.erhannis.lancopy.refactor2.CommChannel;
import com.erhannis.lancopy.refactor2.Interrupt;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;
import java.util.function.Function;
import jcsp.helpers.JcspUtils;
import jcsp.lang.CSProcess;
import jcsp.lang.ChannelOutput;

/**
 *
 * @author erhannis
 */
public class TcpCommChannel extends CommChannel {
    private final SocketChannel rawChannel;

    //TODO Move some of this into CommChannel, or e.g. BasicCommChannel?
    
    /**
     * Create a client TcpCommChannel connection.  If the constructor returns, a TCP connection has been established.<br/>
     * <br/>
     * Implementers: this constructor should likely be present on all ... something.
     * @param comm
     * @throws IOException 
     */
    public TcpCommChannel(DataOwner dataOwner, TcpComm comm) throws IOException {
        super(comm);
        System.out.println("TcpCommChannel connecting " + comm);
        SocketChannel rawChannel = SocketChannel.open();
        rawChannel.socket().connect(new InetSocketAddress(comm.host, comm.port), (Integer) dataOwner.options.getOrDefault("Comms.tcp.CONNECT_TIMEOUT", 35000));
        System.out.println("TcpCommChannel connected " + comm);
        this.rawChannel = rawChannel;
    }

    /**
     * Creates a TcpCommChannel from a SocketChannel.  Presumably this is a client
     * connection, but TcpCommChannel doesn't care.
     * @param interruptCallback
     * @param channel
     * @throws IOException 
     */
    public TcpCommChannel(SocketChannel channel) {
        super(null);
        this.rawChannel = channel;
    }
    
    public static abstract class ServerThread {
        public final int boundPort;

        public ServerThread(int boundPort) {
            this.boundPort = boundPort;
        }
        
        public abstract void run() throws IOException;
    }
    
    /**
     * Run this, use the port, then ServerThread in a thread.  It will listen for incoming connections
     * and pass each SocketChannel back on `incomingConnectionCallback`.
     * 
     * @param incomingConnectionCallback 
     */
    //TODO Do we need to separately handle interfaces, or ipv6, or anything?  Bind to separate addresses or anything?
    public static ServerThread serverThread(ChannelOutput<CommChannel> incomingConnectionOut, int port) throws IOException {
        System.out.println("TcpCommChannel.serverThread, waiting for incoming connections on " + port);
        ServerSocketChannel ss = ServerSocketChannel.open();
        ss.socket().bind(new InetSocketAddress(port));
        int boundPort = ss.socket().getLocalPort();
        return new ServerThread(boundPort) {
            @Override
            public void run() throws IOException {
                while (true) {
                    SocketChannel sc = ss.accept();
                    incomingConnectionOut.write(new TcpCommChannel(sc));
                }
            }            
        };
    }
    
    @Override
    public int read(ByteBuffer dst) throws IOException {
        return rawChannel.read(dst);
    }

    @Override
    public boolean isOpen() {
        return rawChannel.isOpen();
    }

    @Override
    public void close() throws IOException {
        rawChannel.close();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return rawChannel.write(src);
    }

    @Override
    public String toString() {
        return "TcpChan{" + isOpen() + "," + super.toString() + "}";
    }
}
