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
public class DataStartMessage extends Message {
    public String mimeType;
    public final byte[] data;
    public boolean eom;

    public DataStartMessage(UUID correlationId, String mimeType, byte[] data, boolean eom) {
        super(correlationId);
        this.mimeType = mimeType;
        this.data = data;
        this.eom = eom;
    }

    // Kryo
    private DataStartMessage() {
        this.mimeType = null;
        this.data = null;
        this.eom = false;
    }
}
