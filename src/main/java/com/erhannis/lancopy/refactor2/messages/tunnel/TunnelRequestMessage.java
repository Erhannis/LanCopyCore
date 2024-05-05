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
public class TunnelRequestMessage {
    public static enum TunnelDirection {
        LISTEN_AT_TARGET,
        TALK_FROM_TARGET;
    }
    
    public static enum TunnelProtocol {
        TCP,
        UDP; //RAINY Implement
    }
    
    public final UUID initiatorTunnelId;
    public final TunnelDirection tunnelDirection;
    public final int incomingPort; // Not strictly necessary on some messages, but might be useful
    public final String outgoingAddress;
    public final int outgoingPort;
    public final TunnelProtocol protocol;

    public TunnelRequestMessage(UUID initiatorTunnelId, TunnelDirection tunnelDirection, int incomingPort, String outgoingAddress, int outgoingPort, TunnelProtocol protocol) {
        this.initiatorTunnelId = initiatorTunnelId;
        this.tunnelDirection = tunnelDirection;
        this.incomingPort = incomingPort;
        this.outgoingAddress = outgoingAddress;
        this.outgoingPort = outgoingPort;
        this.protocol = protocol;
    }

    // Kryo
    public TunnelRequestMessage() {
        this.initiatorTunnelId = null;
        this.tunnelDirection = null;
        this.incomingPort = 0;
        this.outgoingAddress = null;
        this.outgoingPort = 0;
        this.protocol = null;
    }
    
    @Override
    public String toString() {
        return "TRM{"+initiatorTunnelId+","+tunnelDirection+","+incomingPort+","+outgoingAddress+":"+outgoingPort+","+protocol+"}";
    }
}
