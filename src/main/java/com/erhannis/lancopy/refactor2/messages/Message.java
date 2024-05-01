/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2.messages;

import java.util.UUID;

/**
 *
 * @author erhannis
 */
public abstract class Message {
    public final UUID correlationId;
    //TODO Store nodeId, correlationId, etc., here?
    
    public Message() {
        this.correlationId = UUID.randomUUID();
    }
    
    public Message(UUID correlationId) {
        this.correlationId = correlationId;
    }

    @Override
    public String toString() {
        return "Msg{"+correlationId+"}";
    }
}
