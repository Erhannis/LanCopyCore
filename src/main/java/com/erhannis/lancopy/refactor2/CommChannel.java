/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2;

import com.erhannis.lancopy.refactor.Comm;
import java.nio.channels.ByteChannel;
import java.util.function.Function;

/**
 * Note that comm may be null if this was created by an incoming connection.
 * @author erhannis
 */
public abstract class CommChannel implements ByteChannel {
    public final Function<Interrupt, Boolean> interruptCallback;
    public final Comm comm;
    
    public CommChannel(Function<Interrupt, Boolean> interruptCallback, Comm comm) {
        this.interruptCallback = interruptCallback;
        this.comm = comm;
    }
    
    @Override
    public String toString() {
        return "CommChan{"+comm+"}";
    }
}
