/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor;

import com.erhannis.mathnstuff.FactoryHashMap;
import com.erhannis.mathnstuff.utils.Factory;
import java.util.HashSet;
import jcsp.helpers.SynchronousSplitter;
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.AltingChannelInputInt;
import jcsp.lang.AltingChannelOutputInt;
import jcsp.lang.CSProcess;
import jcsp.lang.Channel;
import jcsp.lang.Guard;
import jcsp.lang.One2OneChannelSymmetricInt;
import jcsp.lang.PoisonException;
import jcsp.lang.ProcessManager;

/**
 *
 * @author erhannis
 */
public class NodeRoster implements CSProcess {

    public final SynchronousSplitter<Advertisement> adUpdatedOut;
    private final AltingChannelInput<Advertisement> rxAdIn;
    private final AltingChannelInputInt joinIn;
    private final AltingChannelOutputInt joinOut;

    private FactoryHashMap<String, HashSet<Advertisement>> ads = new FactoryHashMap<>(new Factory<String, HashSet<Advertisement>>() {
        @Override
        public HashSet<Advertisement> construct(String input) {
            return new HashSet<Advertisement>();
        }
    });

    public NodeRoster(AltingChannelInput<Advertisement> rxAdIn) {
        this.adUpdatedOut = new SynchronousSplitter<>();
        new ProcessManager(adUpdatedOut).start();
        this.rxAdIn = rxAdIn;

        One2OneChannelSymmetricInt joinChannel = Channel.one2oneSymmetricInt();
        this.joinIn = joinChannel.in();
        this.joinOut = joinChannel.out();
    }

    @Override
    public void run() {
        try {
            Alternative alt = new Alternative(new Guard[]{rxAdIn, joinOut});
            while (true) {
                switch (alt.fairSelect()) {
                    case 0: // rxAdIn
                        Advertisement ad = rxAdIn.read();
                        HashSet<Advertisement> versions = ads.get(ad.id);
                        Advertisement oldMax = versions.stream().max((a, b) -> Long.compare(a.timestamp, b.timestamp)).orElse(null);
                        if (!versions.contains(ad)) {
                            versions.add(ad);
                            if (oldMax == null || ad.timestamp > oldMax.timestamp) {
                                adUpdatedOut.write(ad);
                            }
                        }
                        break;
                    case 1: // joinOut
                        joinOut.write(0);
                        break;
                }
            }
        } catch (PoisonException e) {
            int s = e.getStrength();
            adUpdatedOut.poison(s);
            rxAdIn.poison(s);
            joinOut.poison(s);
        }
    }

    public synchronized HashSet<Advertisement> getAdvertisements() {
        joinIn.startRead();
        HashSet<Advertisement> roster = new HashSet<>();
        for (HashSet<Advertisement> versions : ads.values()) {
            Advertisement max = versions.stream().max((a, b) -> Long.compare(a.timestamp, b.timestamp)).orElse(null);
            roster.add(max);
        }
        joinIn.endRead();
        return roster;
    }
}
