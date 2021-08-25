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
public abstract class Comm {
    public final Advertisement owner;
    public final String type;

    public Comm(Advertisement owner, String type) {
        this.owner = owner;
        this.type = type;
    }

    private Comm() {
        this(null, null);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj.getClass() != this.getClass())) {
            return false;
        }
        Comm o = (Comm)obj;
        // Can't recursively check owner, else infinite loop
        if (!Objects.equals(this.type, o.type) || !Objects.equals(this.owner.id, o.owner.id)) {
            return false;
        }
        return true;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(type, owner.id);
    }

    @Override
    public String toString() {
        return "Comm{"+type+"}"; //TODO Include owner id?
    }
    
    public abstract Comm copyToOwner(Advertisement owner);
}
