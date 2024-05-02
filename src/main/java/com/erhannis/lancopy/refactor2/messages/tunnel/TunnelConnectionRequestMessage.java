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
public class TunnelConnectionRequestMessage extends TunnelConnectionMessage {
    public final UUID targetTunnelId;
    
    public TunnelConnectionRequestMessage(UUID connectionId, UUID targetTunnelId) {
        super(connectionId);
        this.targetTunnelId = targetTunnelId;
    }

    // Kryo
    public TunnelConnectionRequestMessage() {
        this.targetTunnelId = null;
    }
    
    @Override
    public String toString() {
        return "TCRM{"+targetTunnelId+","+super.toString()+"}";
    }        
}
