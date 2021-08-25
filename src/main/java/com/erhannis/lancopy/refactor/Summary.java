/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor;

import com.erhannis.mathnstuff.MeUtils;
import java.util.Objects;

/**
 *
 * @author erhannis
 */
public class Summary {
    public final String id;
    public final long timestamp;
    public final String summary;

    public Summary(String id, long timestamp, String summary) {
        this.id = id;
        this.timestamp = timestamp;
        this.summary = summary;
    }
    
    private Summary() {
        this(null, 0, null);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Summary)) {
            return false;
        }
        Summary o = (Summary)obj;
        if (!Objects.equals(this.id, o.id) || this.timestamp != o.timestamp || !Objects.deepEquals(this.summary, o.summary)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, timestamp, summary);
    }

    @Override
    public String toString() {
        return "Sum{"+id+","+timestamp+",\""+summary+"\"}";
    }
}
