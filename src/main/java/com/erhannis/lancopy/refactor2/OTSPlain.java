package com.erhannis.lancopy.refactor2;

import com.erhannis.lancopy.refactor2.messages.DataChunkMessage;
import com.erhannis.lancopy.refactor2.messages.DataStartMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class OTSPlain extends OutgoingTransferState {
    public final InputStream is;
    public final int chunkSize;
    public final String mimeType;
    public int index = 0;

    //TODO Deal with ordering and retransmission etc., for extra robustness spanning multiple comms
    //TODO Deal with ACK

    public OTSPlain(UUID correlationId, UUID targetId, InputStream is, int chunkSize, String mimeType) {
        super(correlationId, targetId, false);
        this.is = is;
        this.chunkSize = chunkSize;
        this.mimeType = mimeType;
    }

    @Override
    public Object nextMessage() throws IOException {
        if (eom) {
            throw new IOException("Already EOM!");
        }
        byte[] data = new byte[chunkSize];
        int count = is.read(data);
        if (count < 0) {
            eom = true;
            data = new byte[0];
            //TODO It'd be nice if we could set eom on the last chunk to contain data....
        } else if (count != chunkSize) {
            byte[] newData = new byte[count];
            System.arraycopy(data, 0, newData, 0, count);
            data = newData;
        }
        if (index == 0) {
            index++;
            return new DataStartMessage(correlationId, mimeType, data, eom);
        } else {
            index++;
            return new DataChunkMessage(correlationId, index, data, eom);
        }
    }
}
