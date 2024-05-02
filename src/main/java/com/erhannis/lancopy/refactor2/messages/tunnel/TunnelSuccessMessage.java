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
public class TunnelSuccessMessage {
    public final UUID initiatorTunnelId;
    public final UUID responseTunnelId;

    public TunnelSuccessMessage(UUID initiatorTunnelId, UUID responseTunnelId) {
        this.initiatorTunnelId = initiatorTunnelId;
        this.responseTunnelId = responseTunnelId;
    }
    
    // Kryo
    public TunnelSuccessMessage() {
        this.initiatorTunnelId = null;
        this.responseTunnelId = null;
    }

    @Override
    public String toString() {
        return "TSM{"+initiatorTunnelId+","+responseTunnelId+"}";
    }
}
