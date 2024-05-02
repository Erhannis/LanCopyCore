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
public class TunnelErrorMessage {
    public final UUID tunnelId;
    public final String message;

    public TunnelErrorMessage(UUID tunnelId, String message) {
        this.tunnelId = tunnelId;
        this.message = message;
    }
    
    // Kryo
    public TunnelErrorMessage() {
        this.tunnelId = null;
        this.message = null;
    }    

    @Override
    public String toString() {
        return "TEM{"+tunnelId+","+message+"}";
    }    
}
