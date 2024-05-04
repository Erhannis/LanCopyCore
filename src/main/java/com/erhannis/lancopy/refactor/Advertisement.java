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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 *
 * @author erhannis
 */
public class Advertisement {
    public final UUID id; // Id of associated node
    public final long timestamp;
    public final List<Comm> comms;
    public final boolean encrypted;
    public final String fingerprint;
    //TODO SECURITY Sign own Advertisement?

    public Advertisement(UUID id, long timestamp, List<Comm> comms, boolean encrypted, String fingerprint) {
        this.id = id;
        this.timestamp = timestamp;
        this.comms = Collections.unmodifiableList(comms.stream().map(c -> c.copyToOwner(this)).collect(Collectors.toList()));
        //this.comms = new ArrayList<>(comms.stream().map(c -> c.copyToOwner(this)).collect(Collectors.toList()));
        this.encrypted = encrypted;
        this.fingerprint = fingerprint;
    }
    
    private Advertisement() {
        this.id = null;
        this.timestamp = 0;
        this.comms = null;
        this.encrypted = false;
        this.fingerprint = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Advertisement)) {
            return false;
        }
        Advertisement o = (Advertisement)obj;
        if (!Objects.equals(this.id, o.id)
                || this.timestamp != o.timestamp
                || !Objects.deepEquals(this.comms, o.comms)
                || this.encrypted != o.encrypted
                || !Objects.deepEquals(this.fingerprint, o.fingerprint)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, timestamp, comms, encrypted, fingerprint);
    }

    @Override
    public String toString() {
        return "Ad{"+id+","+encrypted+"("+fingerprint+"),"+timestamp+",["+MeUtils.join(",",comms)+"]}";
    }
}