/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2.messages.local;

/**
 *
 * @author erhannis
 */
public abstract class LocalMessage<K, V> {
    //THINK Not sure how I feel about these being here.  Maybe they should just be in subclasses only.
    public final K routing;
    public final V payload;

    public LocalMessage(K routing, V payload) {
        this.routing = routing;
        this.payload = payload;
    }
}
