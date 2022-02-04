package com.erhannis.lancopy.refactor2.qr;

import com.erhannis.mathnstuff.MeUtils;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.UUID;
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.CSProcess;
import jcsp.lang.ChannelOutput;
import jcsp.lang.Guard;
import jcsp.lang.PoisonException;

public class QRProcess implements CSProcess {
    private static final UUID ZERO = new UUID(0, 0);
    private static final byte[] EMPTY = new byte[0];

    private final AltingChannelInput<byte[]> txBytesIn;
    private final ChannelOutput<byte[]> txQrOut;
    private final ChannelOutput<byte[]> rxBytesOut;
    private final AltingChannelInput<byte[]> rxQrIn;
    private final ChannelOutput<String> statusOut;

    public QRProcess(AltingChannelInput<byte[]> txBytesIn, ChannelOutput<byte[]> txQrOut, ChannelOutput<byte[]> rxBytesOut, AltingChannelInput<byte[]> rxQrIn, ChannelOutput<String> statusOut) {
        this.txBytesIn = txBytesIn;
        this.txQrOut = txQrOut;
        this.rxBytesOut = rxBytesOut;
        this.rxQrIn = rxQrIn;
        this.statusOut = statusOut;
    }

    @Override
    public void run() {
        UUID localId = ZERO;
        UUID remoteId = ZERO;
        byte[] currentOutgoingChunk = EMPTY;
        /*
            rx qr
              if ack, advance tx
            tx msg
         */
        Alternative alt = new Alternative(new Guard[]{txBytesIn, rxQrIn});
        while (true) {
            try {
                boolean update = false;
                switch (alt.priSelect(new boolean[]{eq(localId, ZERO), true})) {
                    case 0: { // txBytesIn
                        byte[] chunk = txBytesIn.read();
                        currentOutgoingChunk = chunk;
                        localId = UUID.randomUUID();
                        update = true;
                        statusOut.write("Local: " + localId + "\nRemote: " + remoteId);
                        break;
                    }
                    case 1: { // rxQrIn
                        byte[] qr = rxQrIn.read();
                        ByteArrayInputStream bais = new ByteArrayInputStream(qr);
                        long msbAck = Longs.fromByteArray(MeUtils.readNBytes(bais, 8));
                        long lsbAck = Longs.fromByteArray(MeUtils.readNBytes(bais, 8));
                        UUID ack = new UUID(msbAck, lsbAck);
                        long msbId = Longs.fromByteArray(MeUtils.readNBytes(bais, 8));
                        long lsbId = Longs.fromByteArray(MeUtils.readNBytes(bais, 8));
                        UUID rid = new UUID(msbId, lsbId);
                        int len = Ints.fromByteArray(MeUtils.readNBytes(bais, 4));
                        byte[] chunk = MeUtils.readNBytes(bais, len);

                        if (!eq(localId, ZERO) && eq(localId, ack)) {
                            // They've received our msg; time for a new one
                            update = true;
                            localId = ZERO;
                            statusOut.write("Local: " + localId + "\nRemote: " + remoteId);
                            currentOutgoingChunk = EMPTY;
                        }
                        if (!eq(rid, ZERO) && !eq(rid, remoteId)) {
                            // They've sent us a new message
                            update = true;
                            remoteId = rid;
                            statusOut.write("Local: " + localId + "\nRemote: " + remoteId);
                            rxBytesOut.write(chunk);
                        }
                        break;
                    }
                }

                if (update) {
                    byte[] bytes = Bytes.concat(
                            Longs.toByteArray(remoteId.getMostSignificantBits()),
                            Longs.toByteArray(remoteId.getLeastSignificantBits()),
                            Longs.toByteArray(localId.getMostSignificantBits()),
                            Longs.toByteArray(localId.getLeastSignificantBits()),
                            Ints.toByteArray(currentOutgoingChunk.length),
                            currentOutgoingChunk);
                    txQrOut.write(bytes);
                }
            } catch (PoisonException pe) {
                System.err.println("QRCommProcess poisoned, terminating");
                txBytesIn.poison(10);
                txQrOut.poison(10);
                rxBytesOut.poison(10);
                rxQrIn.poison(10);
                throw pe;
            } catch (Throwable t) {
                System.err.println("QRCommProcess got error; continuing");
                t.printStackTrace();
            }
        }
    }

    private static boolean eq(UUID a, UUID b) {
        return (a.getMostSignificantBits() == b.getMostSignificantBits()) && (a.getLeastSignificantBits() == b.getLeastSignificantBits());
    }
}
