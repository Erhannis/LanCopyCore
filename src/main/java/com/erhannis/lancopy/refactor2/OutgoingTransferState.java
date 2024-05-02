package com.erhannis.lancopy.refactor2;

import java.io.IOException;
import java.util.UUID;

public abstract class OutgoingTransferState {
    public final UUID correlationId; //THINK This may not need to be here.  I'm not even sure we REALLY use it.
    public final UUID targetId;
    public boolean eom;

    public OutgoingTransferState(UUID correlationId, UUID targetId, boolean eom) {
        this.correlationId = correlationId;
        this.targetId = targetId;
        this.eom = eom;
    }

    public abstract Object nextMessage() throws IOException;
}
