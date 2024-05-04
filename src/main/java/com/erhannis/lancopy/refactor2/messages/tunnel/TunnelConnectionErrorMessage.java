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
public class TunnelConnectionErrorMessage extends TunnelConnectionMessage {
    public final String message;

    public TunnelConnectionErrorMessage(UUID connectionId, String message) {
        super(connectionId);
        this.message = message;
    }

    // Kryo
    public TunnelConnectionErrorMessage() {
        this.message = null;
    }
    
    @Override
    public String toString() {
        //MISC Not escaped
        return "TCEM{\""+message+"\";"+super.toString()+"}";
    }
}
