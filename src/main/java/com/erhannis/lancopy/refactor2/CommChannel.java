/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2;

import java.nio.channels.ByteChannel;
import java.util.function.Function;

/**
 *
 * @author erhannis
 */
public abstract class CommChannel implements ByteChannel {
    public final Function<Interrupt, Boolean> interruptCallback;
    
    public CommChannel(Function<Interrupt, Boolean> interruptCallback) {
        this.interruptCallback = interruptCallback;
    }    
}
