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
public class TunnelConnectionSuccessMessage extends TunnelConnectionMessage {
    public TunnelConnectionSuccessMessage(UUID connectionId) {
        super(connectionId);
    }

    // Kryo
    public TunnelConnectionSuccessMessage() {
    }

    @Override
    public String toString() {
        return "TCSM{;"+super.toString()+"}";
    }        
}
