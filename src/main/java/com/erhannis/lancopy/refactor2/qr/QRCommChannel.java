package com.erhannis.lancopy.refactor2.qr;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.lancopy.refactor2.CommChannel;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.ChannelOutput;
import jcsp.lang.PoisonException;

//TODO Advertise such comms?
public class QRCommChannel extends CommChannel {
    private final DataOwner dataOwner;
    private final ChannelOutput<byte[]> txBytesOut;
    private final AltingChannelInput<byte[]> rxBytesIn;

    public QRCommChannel(DataOwner dataOwner, ChannelOutput<byte[]> txBytesOut, AltingChannelInput<byte[]> rxBytesIn, Comm comm) {
        super(comm);
        this.dataOwner = dataOwner;
        this.txBytesOut = txBytesOut;
        this.rxBytesIn = rxBytesIn;
    }
    
    private volatile boolean closed = false;
    
    private int offset = 0;
    private byte[] rxd = null;
    
    @Override
    public int read(ByteBuffer dst) throws IOException {
        try {
            while (rxd == null) {
                rxd = rxBytesIn.read();
                offset = 0;
            }

            int n = Math.min(rxd.length - offset, dst.remaining());
            dst.put(rxd, offset, n);
            offset += n;
            if (offset >= rxd.length) {
                rxd = null;
                offset = 0;
            }
            return n;
        } catch (PoisonException pe) {
            close();
            throw new IOException("QRCommChannel closed", pe);
        }
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        txBytesOut.poison(10);
        rxBytesIn.poison(10);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        try {
            int total = 0;
            while (src.hasRemaining()) {
                int MAX = (Integer) dataOwner.options.getOrDefault("Comms.qr.MAX_BYTES", 512); // Excluding header
                int n = Math.min(src.remaining(), MAX);
                byte[] chunk = new byte[n];
                src.get(chunk);
                txBytesOut.write(chunk);
                total += n;
            }
            return total;
        } catch (PoisonException pe) {
            close();
            throw new IOException("QRCommChannel closed", pe);
        }
    }
}
