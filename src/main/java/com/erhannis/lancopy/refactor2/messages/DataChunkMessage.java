/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2.messages;

import com.erhannis.lancopy.data.Data;
import java.util.UUID;

/**
 *
 * @author erhannis
 */
public class DataChunkMessage extends Message {
    public final long index;
    public final byte[] data;
    public boolean eom;

    /**
     * DataStartMessage assumed to have index 0; the next should be 1, and so forth.
     * 
     * @param correlationId
     * @param index
     * @param data
     * @param eom 
     */
    public DataChunkMessage(UUID correlationId, int index, byte[] data, boolean eom) {
        super(correlationId);
        this.index = index;
        this.data = data;
        this.eom = eom;
    }

    // Kryo
    private DataChunkMessage() {
        this.index = 0;
        this.data = null;
        this.eom = false;
    }
}
