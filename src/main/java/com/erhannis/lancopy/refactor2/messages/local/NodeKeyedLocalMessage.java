/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2.messages.local;

import java.util.UUID;

/**
 *
 * @author erhannis
 */
public class NodeKeyedLocalMessage extends LocalMessage<UUID, Object> {
    public NodeKeyedLocalMessage(UUID targetNode, Object payload) {
        super(targetNode, payload);
    }
}
