/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor2.qr;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.refactor.Advertisement;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.lancopy.refactor2.CommChannel;
import java.util.Objects;

/**
 *
 * @author erhannis
 */
public class QRComm extends Comm {
    public static final String TYPE = "QR";
    
    public QRComm(Advertisement owner) {
        super(owner, TYPE, 7);
    }

    private QRComm() {
        this(null);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) return false;
        QRComm o = (QRComm)obj;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode());
    }

    @Override
    public String toString() {
        return super.toString()+"{"+"}";
    }

    @Override
    public Comm copyToOwner(Advertisement owner) {
        return new QRComm(owner);
    }

    @Override
    public CommChannel connect(DataOwner dataOwner) throws Exception {
        QRCommFrame qcf = new QRCommFrame(dataOwner, this);
        qcf.setVisible(true);
        return qcf.channel;
    }
}
