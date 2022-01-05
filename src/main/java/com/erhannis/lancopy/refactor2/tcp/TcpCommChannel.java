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
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.util.function.Function;

/**
 *
 * @author erhannis
 */
public class TcpCommChannel extends CommChannel {
    private final SocketChannel rawChannel;
    private final TcpComm comm;
    
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
