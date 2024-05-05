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
    //RAINY I think this may need to go away; it's at the center of a failure of a node to open a tunnel to itself (a conceptually reasonable thing)
    public final UUID connectionId;

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
