/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor;

import com.erhannis.mathnstuff.MeUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 *
 * @author erhannis
 */
public class Advertisement {    
    public final String id;
    public final long timestamp;
    public final List<Comm> comms;

    public Advertisement(String id, long timestamp, List<Comm> comms) {
        this.id = id;
        this.timestamp = timestamp;
        this.comms = Collections.unmodifiableList(comms.stream().map(c -> c.copyToOwner(this)).collect(Collectors.toList()));
        //this.comms = new ArrayList<>(comms.stream().map(c -> c.copyToOwner(this)).collect(Collectors.toList()));
    }
    
    private Advertisement() {
        this.id = null;
        this.timestamp = 0;
        this.comms = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Advertisement)) {
            return false;
        }
        Advertisement o = (Advertisement)obj;
        if (!Objects.equals(this.id, o.id) || this.timestamp != o.timestamp || !Objects.deepEquals(this.comms, o.comms)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, timestamp, comms);
    }

    @Override
    public String toString() {
        return "Ad{"+id+","+timestamp+",["+MeUtils.join(",",comms)+"]}";
    }
}