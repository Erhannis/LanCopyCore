/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor.tcp;

import com.erhannis.lancopy.refactor.Advertisement;
import com.erhannis.lancopy.refactor.Comm;
import java.util.Objects;

/**
 *
 * @author erhannis
 */
public class TcpComm extends Comm {
    public static final String TYPE = "TCP";
    
    public final String address;

    public TcpComm(Advertisement owner, String address) {
        super(owner, TYPE);
        this.address = address;
    }

    private TcpComm() {
        this(null, null);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) return false;
        TcpComm o = (TcpComm)obj;
        if (!Objects.equals(this.address, o.address)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), address);
    }

    @Override
    public String toString() {
        return super.toString()+"{"+address+"}";
    }

    @Override
    public Comm copyToOwner(Advertisement owner) {
        return new TcpComm(owner, this.address);
    }
}
