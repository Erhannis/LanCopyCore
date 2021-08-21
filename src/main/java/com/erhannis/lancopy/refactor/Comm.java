/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor;

import java.util.Objects;

/**
 *
 * @author erhannis
 */
public class Comm {
    public final String type;
    public final Object data;

    public Comm(String type, Object data) {
        this.type = type;
        this.data = data;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Comm)) {
            return false;
        }
        Comm o = (Comm)obj;
        if (!Objects.equals(this.type, o.type) || !Objects.deepEquals(this.data, o.data)) {
            return false;
        }
        return true;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(type, data);
    }

    @Override
    public String toString() {
        return "Comm{"+type+","+data+"}";
    }
}
