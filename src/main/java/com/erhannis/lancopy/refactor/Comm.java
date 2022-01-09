/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor;

import com.erhannis.lancopy.refactor2.CommChannel;
import java.util.Objects;

/**
 *
 * @author erhannis
 */
public abstract class Comm {
    /** The score used in cases where no score is known - like incoming connections */
    public static final double DEFAULT_SCORE = 5;
    
    public final Advertisement owner;
    public final String type;
    /**
     * Subjective score of a Comm - used to prefer connection attempts.
     * 0 is best, 10 is worst.<br/>
     * Positive qualities include speed and reliability.<br/>
     * Negative qualities include wastefulness, bother to the user, and requiring user intervention.<br/>
     * Rule of thumb:<br/>
     * 1 - TCP
     * 3 - Filtered UDP broadcast
     * 5 - Speaker/mic modem
     * 7 - QR codes
     * 9 - Hand-written messages by carrier pigeon
     */
    public final double score;

    public Comm(Advertisement owner, String type, double score) {
        this.owner = owner;
        this.type = type;
        this.score = score;
    }

    private Comm() {
        this(null, null, 100);
    }
    
    public abstract CommChannel connect() throws Exception;
    
    @Override
    public boolean equals(Object obj) {
        if (obj == null || (obj.getClass() != this.getClass())) {
            return false;
        }
        Comm o = (Comm)obj;
        // Can't recursively check owner, else infinite loop
        if (!Objects.equals(this.type, o.type)) return false;
        if (!Objects.equals(this.owner == null ? null : this.owner.id, o.owner == null ? null : o.owner.id)) return false;
        if (!Objects.equals(this.score, o.score)) return false;
        return true;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(type, owner == null ? null : owner.id, score);
    }

    @Override
    public String toString() {
        return "Comm{"+score+","+type+"}"; //TODO Include owner id?
    }
    
    public abstract Comm copyToOwner(Advertisement owner);
}
