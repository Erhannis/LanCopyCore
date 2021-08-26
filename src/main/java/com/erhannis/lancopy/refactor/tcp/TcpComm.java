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
    
    public final String scheme;
    public final String host;
    public final int port;

    public TcpComm(Advertisement owner, String scheme, String host, int port) {
        super(owner, TYPE);
        this.scheme = scheme;
        this.host = host;
        this.port = port;
    }

    private TcpComm() {
        this(null, null, null, 0);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) return false;
        TcpComm o = (TcpComm)obj;
        if (!(Objects.equals(this.scheme, o.scheme)
           && Objects.equals(this.host, o.host)
           && Objects.equals(this.port, o.port))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), scheme, host, port);
    }

    @Override
    public String toString() {
        return super.toString()+"{"+scheme+"://"+host+":"+port+"}";
    }

    @Override
    public Comm copyToOwner(Advertisement owner) {
        return new TcpComm(owner, this.scheme, this.host, this.port);
    }
}
