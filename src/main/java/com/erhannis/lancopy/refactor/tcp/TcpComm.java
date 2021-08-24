/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor.tcp;

import java.util.Objects;

/**
 *
 * @author erhannis
 */
public class TcpComm {
    public final String address;

    public TcpComm(String address) {
        this.address = address;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof TcpComm)) {
            return false;
        }
        TcpComm o = (TcpComm)obj;
        if (!Objects.equals(this.address, o.address)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }

    @Override
    public String toString() {
        return address;
    }
}
