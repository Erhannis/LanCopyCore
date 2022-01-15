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
public class IdentificationMessage extends Message {
    public final UUID nodeId;
    //TODO Timestamp or anything?

    public IdentificationMessage(UUID nodeId) {
        this.nodeId = nodeId;
    }
    
    // Kryo
    private IdentificationMessage() {
        this.nodeId = null;
    }
}
