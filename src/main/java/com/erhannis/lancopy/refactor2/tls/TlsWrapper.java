/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2.tls;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.refactor.tcp.TcpComm;
import com.erhannis.lancopy.refactor2.CommChannel;
import com.erhannis.lancopy.refactor2.Interrupt;
import com.erhannis.lancopy.refactor2.tls.ContextFactory.Context;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import tlschannel.ClientTlsChannel;
import tlschannel.ServerTlsChannel;
import tlschannel.TlsChannel;

/**
 *
 * @author erhannis
 */
public class TlsWrapper extends CommChannel {
    public final CommChannel wrappedChannel;
    public final TlsChannel tlsChannel;
    
    public TlsWrapper(DataOwner dataOwner, boolean clientMode, Function<Interrupt, Boolean> interruptCallback, CommChannel subchannel) throws IOException {
        super(interruptCallback); //TODO Where is this comm used?  Should null, or wrapped, or passthrough?
        
        this.wrappedChannel = subchannel;
        
        //TODO Interrupts
        System.out.println("TlsWrapper (" + (clientMode ? "client" : "server" + ")"));
        System.out.println("Local hash:");
        System.out.println(dataOwner.tlsContext.sha256Fingerprint);
        
        if (clientMode) {
            System.out.println("Connection outbound...");

            SSLEngine engine = dataOwner.tlsContext.sslContext.createSSLEngine();
            engine.setUseClientMode(true);
            // Since we control both client and server, I'm restricting protocols to TLSv1.3 alone
            //TODO Optionize?
            engine.setEnabledProtocols(new String[]{"TLSv1.3"});
            ClientTlsChannel.Builder builder = ClientTlsChannel.newBuilder(subchannel, engine);

            try (TlsChannel tlsChannel = builder.build()) {
                this.tlsChannel = tlsChannel;
            }
        } else {
            System.out.println("Connection inbound...");

            // create TlsChannel builder, combining the raw channel and the SSLEngine, using minimal options
            ServerTlsChannel.Builder builder = ServerTlsChannel.newBuilder(subchannel, dataOwner.tlsContext.sslContext)
                    .withEngineFactory(sc -> {
                        SSLEngine se = dataOwner.tlsContext.sslContext.createSSLEngine();
                        se.setUseClientMode(false);
                        se.setNeedClientAuth(true);
                        // Since we control both client and server, I'm restricting protocols to TLSv1.3 alone
                        //TODO Optionize?
                        se.setEnabledProtocols(new String[] {"TLSv1.3"});
                        return se;
                    });

            try (TlsChannel tlsChannel = builder.build()) {
                this.tlsChannel = tlsChannel;
            }
        }
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return this.tlsChannel.read(dst);
    }

    @Override
    public boolean isOpen() {
        return this.tlsChannel.isOpen();
    }

    @Override
    public void close() throws IOException {
        this.tlsChannel.close();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return this.tlsChannel.write(src);
    }

    @Override
    public String toString() {
        return "TlsWrapper{" + wrappedChannel + "}";
    }
}
