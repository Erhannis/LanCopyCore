/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2.tcp;

import com.erhannis.lancopy.refactor.tcp.TcpComm;
import com.erhannis.lancopy.refactor2.CommChannel;
import com.erhannis.lancopy.refactor2.Interrupt;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 *
 * @author erhannis
 */
public class TcpCommChannel extends CommChannel {
    private final SocketChannel rawChannel;
    private final TcpComm comm;

    //TODO Move some of this into CommChannel, or e.g. BasicCommChannel?
    
    /**
     * Create a client TcpCommChannel connection.  If the constructor returns, a TCP connection has been established.<br/>
     * <br/>
     * Implementers: this constructor should likely be present on all 
     * @param interruptCallback
     * @param comm
     * @throws IOException 
     */
    public TcpCommChannel(Function<Interrupt, Boolean> interruptCallback, TcpComm comm) throws IOException {
        super(interruptCallback);
        System.out.println("TcpCommChannel connecting " + comm);
        this.comm = comm;
        try (SocketChannel rawChannel = SocketChannel.open()) {
            rawChannel.connect(new InetSocketAddress(comm.host, comm.port));
            System.out.println("TcpCommChannel connected " + comm);
            this.rawChannel = rawChannel;
        }
    }

    /**
     * Creates a TcpCommChannel from a SocketChannel.  Presumably this is a client
     * connection, but TcpCommChannel doesn't care.
     * @param interruptCallback
     * @param channel
     * @throws IOException 
     */
    public TcpCommChannel(Function<Interrupt, Boolean> interruptCallback, SocketChannel channel) {
        super(interruptCallback);
        this.rawChannel = channel;
        this.comm = null;
    }
    
    /**
     * Run this in a thread.  It will listen for incoming connections
     * and pass each SocketChannel back on `incomingConnectionCallback`.
     * 
     * @param incomingConnectionCallback 
     */
    //TODO Do we need to separately handle interfaces, or ipv6, or anything?  Bind to separate addresses or anything?
    public static void serverThread(Consumer<SocketChannel> incomingConnectionCallback, int port) throws IOException {
        System.out.println("TcpCommChannel.serverThread, waiting for incoming connections on " + port);
        ServerSocketChannel ss = ServerSocketChannel.open();
        ss.socket().bind(new InetSocketAddress(port));
        while (true) {
            SocketChannel sc = ss.accept();
            incomingConnectionCallback.accept(sc);
        }
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
        return "TcpChan{" + isOpen() + ", " + comm + "}";
    }
}