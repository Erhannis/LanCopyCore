/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2.messages.tunnel;

import java.util.UUID;

/**
 *
 * @author erhannis
 */
public class TunnelConnectionDataMessage extends TunnelConnectionMessage {
    public final int index;
    public final byte[] data;

    public TunnelConnectionDataMessage(int index, byte[] data, UUID connectionId) {
        super(connectionId);
        this.index = index;
        this.data = data;
    }

    // Kryo
    public TunnelConnectionDataMessage() {
        this.index = 0;
        this.data = null;
    }

    @Override
    public String toString() {
        return "TCDM{"+index+",["+data.length+"];"+super.toString()+"}";
    }    
}
