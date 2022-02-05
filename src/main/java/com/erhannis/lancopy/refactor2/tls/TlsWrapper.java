/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2.tls;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.refactor2.CommChannel;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import jcsp.helpers.JcspUtils;
import jcsp.helpers.NameParallel;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.AltingChannelInputInt;
import jcsp.lang.Any2OneChannelInt;
import jcsp.lang.CSProcess;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutputInt;
import jcsp.lang.ProcessManager;
import jcsp.util.ints.InfiniteBufferInt;
import tlschannel.ClientTlsChannel;
import tlschannel.ServerTlsChannel;
import tlschannel.TlsChannel;

/**
 *
 * @author erhannis
 */
public class TlsWrapper extends CommChannel {
    public static enum ClientServerMode {
        /** If X is in CLIENT mode, Y must be in SERVER mode. */
        CLIENT,
        /** If X is in SERVER mode, Y must be in CLIENT mode. */
        SERVER,
        /** If X is in HAGGLE mode, Y must be in HAGGLE mode.
         * X and Y will pick either CLIENT or SERVER at random until their choices
         * differ (one gets client and the other gets server).
         * Note that a channel in HAGGLE mode will not communicate correctly with a
         * channel in plain CLIENT or SERVER mode.
         */
        HAGGLE
    }
    
    private final DataOwner dataOwner;
    private final boolean requireClientCert;
    private final ChannelOutputInt showLocalFingerprintOut;
    public final CommChannel wrappedChannel;
    public volatile TlsChannel tlsChannel;
    private volatile boolean closed = false;
    
    public TlsWrapper(DataOwner dataOwner, ClientServerMode clientMode, boolean requireClientCert, CommChannel subchannel, ChannelOutputInt showLocalFingerprintOut) throws IOException {
        super(subchannel.comm); //TODO Where is this comm used?  Should null, or wrapped, or passthrough?  Passthrough, for now....
        
        this.dataOwner = dataOwner;
        this.requireClientCert = requireClientCert;
        this.showLocalFingerprintOut = showLocalFingerprintOut;
        this.wrappedChannel = subchannel;
        
        //TODO Interrupts
        System.out.println("TlsWrapper (" + clientMode + ")");
        System.out.println("Local hash:");
        System.out.println(dataOwner.tlsContext.sha256Fingerprint);

        //TODO Store cert ID
        
        switch (clientMode) {
            case CLIENT: {
                becomeClient();
                break;
            }
            case SERVER: {
                becomeServer();
                break;
            }
        }
    }

    private void becomeClient() {
        System.out.println("Connection outbound...");

        SSLEngine engine = dataOwner.tlsContext.sslContext.createSSLEngine();
        engine.setUseClientMode(true);
        // Since we control both client and server, I'm restricting protocols to TLSv1.3 alone
        //TODO Optionize?
        engine.setEnabledProtocols(new String[]{"TLSv1.3"});
        ClientTlsChannel.Builder builder = ClientTlsChannel.newBuilder(wrappedChannel, engine);

        TlsChannel tlsChannel = builder.build();
        this.tlsChannel = tlsChannel;
    }
    
    private void becomeServer() {
        System.out.println("Connection inbound...");


        // If the connection isn't made quickly enough, assume the other side is asking the user about certs, and show our own

        Any2OneChannelInt madeConnectionChannel = Channel.any2oneInt(new InfiniteBufferInt());
        AltingChannelInputInt madeConnectionIn = madeConnectionChannel.in();
        ChannelOutputInt madeConnectionOut = JcspUtils.logDeadlock(madeConnectionChannel.out());

        final long fingerprintPromptDelay = (long) dataOwner.options.getOrDefault("TlsWrapper.fingerprint_prompt_delay", 500L);
        new ProcessManager(() -> {
            try {
                Thread.sleep(fingerprintPromptDelay);
                if (!madeConnectionIn.pending()) {
                    showLocalFingerprintOut.write(1);
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(TlsWrapper.class.getName()).log(Level.SEVERE, null, ex);
            }
        }).start();

        // create TlsChannel builder, combining the raw channel and the SSLEngine, using minimal options
        ServerTlsChannel.Builder builder = ServerTlsChannel.newBuilder(wrappedChannel, dataOwner.tlsContext.sslContext)
                .withEngineFactory(sc -> {
                    SSLEngine se = dataOwner.tlsContext.sslContext.createSSLEngine();
                    se.setUseClientMode(false);
                    if (requireClientCert) {
                        se.setNeedClientAuth(true);
                    } else {
                        se.setWantClientAuth(true);
                    }
                    // Since we control both client and server, I'm restricting protocols to TLSv1.3 alone
                    //TODO Optionize?
                    se.setEnabledProtocols(new String[] {"TLSv1.3"});
                    return se;
                }).withSessionInitCallback(session -> {
                    System.out.println("TlsWrapper session init");
                    madeConnectionOut.write(1);
                });

        TlsChannel tlsChannel = builder.build();
        this.tlsChannel = tlsChannel;
    }

    private byte exchange(byte tx) throws IOException {
        AltingChannelInput<IOException> errIn = JcspUtils.spawn(out -> {
            try {
                wrappedChannel.write(ByteBuffer.wrap(new byte[]{tx}));
                out.write(null);
            } catch (IOException ex) {
                Logger.getLogger(TlsWrapper.class.getName()).log(Level.SEVERE, null, ex);
                out.write(ex);
            }
        });
        byte[] rx = new byte[1];
        wrappedChannel.read(ByteBuffer.wrap(rx));
        IOException ex = errIn.read();
        if (ex != null) {
            throw ex;
        }
        return rx[0];
    }
    
    private TlsChannel negotiateMode() throws IOException {
        if (closed) {
            throw new IOException("Channel closed!");
        }
        boolean done = false;
        int mode = -1;
        while (!done) {
            mode = dataOwner.rand.nextInt(2);
            switch (mode) {
                case 0: { // Try client mode
                    byte remote = exchange((byte)0);
                    if (remote == 1) {
                        done = true;
                    }
                    break;
                }
                case 1: { // Try server mode
                    byte remote = exchange((byte)1);
                    if (remote == 0) {
                        done = true;
                    }
                    break;
                }
            }
        }
        switch (mode) {
            case 0: { // Client mode
                becomeClient();
                break;
            }
            case 1: { // Server mode
                becomeServer();
                break;
            }
            default: { // ?!?
                throw new RuntimeException("TlsWrapper IMPOSSIBLE MODE?!?");
            }
        }
        return tlsChannel;
    }
    
    // This is probably too complicated, and I've likely messed something up, and the difference is probably not even noticeable.
    private TlsChannel getChannel(boolean peek) throws IOException {
        // https://en.wikipedia.org/wiki/Double-checked_locking#Usage_in_Java
        TlsChannel localRef = tlsChannel;
        if (localRef == null) {
            synchronized (negotiationLock) {
                localRef = tlsChannel;
                if (peek) {
                    return localRef;
                }
                if (localRef == null) {
                    tlsChannel = localRef = negotiateMode();
                }
            }
        }
        return localRef;
    }
    
    private final Object negotiationLock = new Object();
    
    @Override
    public int read(ByteBuffer dst) throws IOException {
        return getChannel(false).read(dst);
    }

    @Override
    public boolean isOpen() {
        try {
            TlsChannel localRef = getChannel(true);
            if (localRef == null) {
                return true; //TODO What to return if the channel hasn't been connected yet???
            }
            return localRef.isOpen();
        } catch (IOException ex) {
            Logger.getLogger(TlsWrapper.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        TlsChannel localRef = getChannel(true);
        if (localRef != null) {
            localRef.close();
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return getChannel(false).write(src);
    }

    @Override
    public String toString() {
        return "TlsWrapper{" + wrappedChannel + "}";
    }
}
