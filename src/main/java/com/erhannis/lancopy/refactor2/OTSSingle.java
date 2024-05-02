package com.erhannis.lancopy.refactor2;

import java.io.IOException;
import java.util.UUID;

/**
 * TX single message.  A bit of a shoehorn for tx arbitrary messages from anywhere in code.
 * @author erhannis
 */
public class OTSSingle extends OutgoingTransferState {
    public Object msg;
    
    public OTSSingle(UUID correlationId, UUID targetId, Object msg) {
        super(correlationId, targetId, false);
        this.msg = msg;
    }

    @Override
    public Object nextMessage() throws IOException {
        if (eom) {
            throw new IOException("Already EOM!");
        }
        eom = true;
        return msg;
    }
}
