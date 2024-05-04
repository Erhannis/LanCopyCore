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
public abstract class TunnelConnectionMessage {
    public final UUID connectionId; //NEXT I think this may need to go away

    public TunnelConnectionMessage(UUID connectionId) {
        this.connectionId = connectionId;
    }
    
    // Kryo
    public TunnelConnectionMessage() {
        this.connectionId = null;
    }

    @Override
    public String toString() {
        return "TCM{"+connectionId+"}";
    }    
}
