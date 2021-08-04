/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

import com.google.common.base.Objects;

/**
 *
 * @author erhannis
 */
public class NodeInfo {
    public static enum State {
        ACTIVE, INACTIVE
    }
    
    public final String id; //TODO Should this be included or not?  It seems like it should, but also, then like, the key is included in the value, which is redundant
    public final String url;
    public final String summary;
    public final State active;
    
    public NodeInfo(String id, String url, String summary, State active) {
        this.id = id;
        this.url = url;
        this.summary = summary;
        this.active = active;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id, url, summary, active);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof NodeInfo)) return false;
        NodeInfo o = (NodeInfo)obj;
        if (!Objects.equal(this.id, o.id)) return false;
        if (!Objects.equal(this.url, o.url)) return false;
        if (!Objects.equal(this.summary, o.summary)) return false;
        if (!Objects.equal(this.active, o.active)) return false;
        return true;
    }
}
