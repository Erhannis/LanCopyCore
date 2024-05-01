/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor;

import com.erhannis.mathnstuff.FactoryHashMap;
import com.erhannis.mathnstuff.utils.Factory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.Supplier;
import jcsp.helpers.FCClient;
import jcsp.helpers.FCServer;
import jcsp.helpers.JcspUtils;
import jcsp.helpers.SynchronousSplitter;
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.AltingChannelInputInt;
import jcsp.lang.AltingChannelOutputInt;
import jcsp.lang.AltingFCServer;
import jcsp.lang.AltingFunctionChannel;
import jcsp.lang.CSProcess;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;
import jcsp.lang.Guard;
import jcsp.lang.One2OneChannelSymmetricInt;
import jcsp.lang.Parallel;
import jcsp.lang.PoisonException;
import jcsp.lang.ProcessManager;

/**
 *
 * @author erhannis
 */
public class NodeTracker implements CSProcess {

    private final ChannelOutput<Advertisement> adUpdatedOut;
    private final ChannelOutput<Summary> summaryUpdatedOut;
    private final AltingFCServer<UUID, Advertisement> adCallServer;
    private final AltingFCServer<UUID, Summary> summaryCallServer;
    private final AltingFCServer<Void, List<Advertisement>> rosterCallServer;
    private final AltingChannelInput<Advertisement> adIn;
    private final AltingChannelInput<Summary> summaryIn;
    private final AltingChannelInputInt joinIn;
    private final AltingChannelOutputInt joinOut;

    private FactoryHashMap<UUID, HashSet<Advertisement>> ads = new FactoryHashMap<>(new Factory<UUID, HashSet<Advertisement>>() {
        @Override
        public HashSet<Advertisement> construct(UUID input) {
            return new HashSet<Advertisement>();
        }
    });

    private FactoryHashMap<UUID, HashSet<Summary>> summarys = new FactoryHashMap<>(new Factory<UUID, HashSet<Summary>>() {
        @Override
        public HashSet<Summary> construct(UUID input) {
            return new HashSet<Summary>();
        }
    });
    
    public NodeTracker(ChannelOutput<Advertisement> adUpdatedOut, ChannelOutput<Summary> summaryUpdatedOut, AltingChannelInput<Advertisement> adIn, AltingChannelInput<Summary> summaryIn, AltingFCServer<UUID, Advertisement> adCallServer, AltingFCServer<UUID, Summary> summaryCallServer, AltingFCServer<Void, List<Advertisement>> rosterCallServer) {
        this.adUpdatedOut = adUpdatedOut;
        this.summaryUpdatedOut = summaryUpdatedOut;        
        this.adIn = adIn;
        this.summaryIn = summaryIn;

        One2OneChannelSymmetricInt joinChannel = Channel.one2oneSymmetricInt();
        this.joinIn = joinChannel.in();
        this.joinOut = joinChannel.out(); //TODO It'd be nice if I could logDeadlock this....
        
        this.adCallServer = adCallServer;
        this.summaryCallServer = summaryCallServer;
        this.rosterCallServer = rosterCallServer;
    }

    @Override
    public void run() {
        try {
            Alternative alt = new Alternative(new Guard[]{adIn, summaryIn, joinOut, adCallServer, summaryCallServer, rosterCallServer});
            while (true) {
                switch (alt.fairSelect()) {
                    case 0: // adIn
                        {
                            Advertisement ad = adIn.read();
                            HashSet<Advertisement> versions = ads.get(ad.id);
                            Advertisement oldMax = versions.stream().max((a, b) -> Long.compare(a.timestamp, b.timestamp)).orElse(null);
                            if (!versions.contains(ad)) {
                                versions.add(ad);
                                if (oldMax == null || ad.timestamp > oldMax.timestamp) {
                                    adUpdatedOut.write(ad);
                                }
                            }
                            break;
                        }
                    case 1: // summaryIn
                        {
                            Summary summary = summaryIn.read();
                            HashSet<Summary> versions = summarys.get(summary.id);
                            Summary oldMax = versions.stream().max((a, b) -> Long.compare(a.timestamp, b.timestamp)).orElse(null);
                            if (!versions.contains(summary)) {
                                versions.add(summary);
                                if (oldMax == null || summary.timestamp > oldMax.timestamp) {
                                    summaryUpdatedOut.write(summary);
                                }
                            }
                            break;
                        }
                    case 2: // joinOut
                        joinOut.write(0);
                        break;
                    case 3: // adCallServer
                    {
                        UUID id = adCallServer.startRead();
                        HashSet<Advertisement> versions = ads.get(id);
                        Advertisement max = versions.stream().max((a, b) -> Long.compare(a.timestamp, b.timestamp)).orElse(null);
                        adCallServer.endRead(max);
                        break;
                    }
                    case 4: // summaryCallServer
                    {
                        UUID id = summaryCallServer.startRead();
                        HashSet<Summary> versions = summarys.get(id);
                        Summary max = versions.stream().max((a, b) -> Long.compare(a.timestamp, b.timestamp)).orElse(null);
                        summaryCallServer.endRead(max);
                        break;
                    }
                    case 5: // rosterCallServer
                    {
                        rosterCallServer.startRead();
                        ArrayList<Advertisement> roster = new ArrayList<>();
                        for (Entry<UUID, HashSet<Advertisement>> e : ads.entrySet()) {
                            HashSet<Advertisement> versions = ads.get(e.getKey());
                            Advertisement max = versions.stream().max((a, b) -> Long.compare(a.timestamp, b.timestamp)).orElse(null);
                            roster.add(max);
                        }
                        rosterCallServer.endRead(roster);
                        break;
                    }
                }
            }
        } catch (PoisonException e) {
            int s = e.getStrength();
            adUpdatedOut.poison(s);
            summaryUpdatedOut.poison(s);
            adIn.poison(s);
            summaryIn.poison(s);
            joinOut.poison(s);
        }
    }

    public synchronized HashSet<Advertisement> getAdvertisements() {
        LD(joinIn::startRead);
        HashSet<Advertisement> roster = new HashSet<>();
        for (HashSet<Advertisement> versions : ads.values()) {
            Advertisement max = versions.stream().max((a, b) -> Long.compare(a.timestamp, b.timestamp)).orElse(null);
            roster.add(max);
        }
        joinIn.endRead();
        return roster;
    }

    public synchronized HashSet<Summary> getSummaries() {
        LD(joinIn::startRead);
        HashSet<Summary> roster = new HashSet<>();
        for (HashSet<Summary> versions : summarys.values()) {
            Summary max = versions.stream().max((a, b) -> Long.compare(a.timestamp, b.timestamp)).orElse(null);
            roster.add(max);
        }
        joinIn.endRead();
        return roster;
    }
    
    private static <T> T LD(Supplier<T> r) {
        return JcspUtils.logDeadlock(r);
    }
}
