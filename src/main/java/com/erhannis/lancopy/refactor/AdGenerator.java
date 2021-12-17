/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor;

import com.erhannis.lancopy.DataOwner;
import java.util.ArrayList;
import java.util.List;
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.CSProcess;
import jcsp.lang.ChannelOutput;
import jcsp.lang.Guard;

/**
 *
 * @author erhannis
 */
public class AdGenerator implements CSProcess {
    private final DataOwner dataOwner;

    private final ChannelOutput<Advertisement> adOut;
    private final AltingChannelInput<List<Comm>> commsIn;

    public AdGenerator(DataOwner dataOwner, ChannelOutput<Advertisement> adOut, AltingChannelInput<List<Comm>> commsIn) {
        this.dataOwner = dataOwner;
        this.adOut = adOut;
        this.commsIn = commsIn;
    }
    
    @Override
    public void run() {
        Advertisement ad = new Advertisement(dataOwner.ID, System.currentTimeMillis(), dataOwner.encryption, new ArrayList<>());
        try {
            Alternative alt = new Alternative(new Guard[]{commsIn});
            while (true) {
                switch (alt.priSelect()) {
                    case 0: // commsIn
                        List<Comm> comms = commsIn.read();
                        ArrayList<Comm> combined = new ArrayList<>(comms);
                        combined.addAll(ad.comms);
                        ad = new Advertisement(dataOwner.ID, System.currentTimeMillis(), dataOwner.encryption, combined);
                        adOut.write(ad);
                        break;
                }
            }
        } finally {
            System.out.println("AdGenerator shutting down");
            dataOwner.errOnce("AdGenerator //TODO Handle poison");
        }
    }    
}
