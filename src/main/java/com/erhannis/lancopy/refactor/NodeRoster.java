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
import jcsp.lang.Guard;
import jcsp.lang.One2OneChannelSymmetricInt;
import jcsp.lang.Parallel;
import jcsp.lang.PoisonException;
import jcsp.lang.ProcessManager;

/**
 *
 * @author erhannis
 */
public class NodeRoster implements CSProcess {

    public final SynchronousSplitter<Advertisement> adUpdatedOut;
    public final SynchronousSplitter<Summary> summaryUpdatedOut;
    private final FCClient<String, Advertisement> adCall;
    private final FCClient<Void, List<Advertisement>> rosterCall;
    private final AltingFCServer<String, Advertisement> adCallServer;
    private final AltingFCServer<Void, List<Advertisement>> rosterCallServer;
    private final AltingChannelInput<Advertisement> adIn;
    private final AltingChannelInput<Summary> summaryIn;
    private final AltingChannelInputInt joinIn;
    private final AltingChannelOutputInt joinOut;

    private FactoryHashMap<String, HashSet<Advertisement>> ads = new FactoryHashMap<>(new Factory<String, HashSet<Advertisement>>() {
        @Override
        public HashSet<Advertisement> construct(String input) {
            return new HashSet<Advertisement>();
        }
    });

    private FactoryHashMap<String, HashSet<Summary>> summarys = new FactoryHashMap<>(new Factory<String, HashSet<Summary>>() {
        @Override
        public HashSet<Summary> construct(String input) {
            return new HashSet<Summary>();
        }
    });
    
    public NodeRoster(AltingChannelInput<Advertisement> adIn, AltingChannelInput<Summary> summaryIn) {
        this.adUpdatedOut = new SynchronousSplitter<>();
        this.summaryUpdatedOut = new SynchronousSplitter<>();
        new ProcessManager(new Parallel(new CSProcess[]{adUpdatedOut, summaryUpdatedOut})).start();
        this.adIn = adIn;
        this.summaryIn = summaryIn;

        One2OneChannelSymmetricInt joinChannel = Channel.one2oneSymmetricInt();
        this.joinIn = joinChannel.in();
        this.joinOut = joinChannel.out();
        
        AltingFunctionChannel<String, Advertisement> adCall = new AltingFunctionChannel<>();
        this.adCall = adCall;
        this.adCallServer = adCall;

        AltingFunctionChannel<Void, List<Advertisement>> rosterCall = new AltingFunctionChannel<>();
        this.rosterCall = rosterCall;
        this.rosterCallServer = rosterCall;
    }

    @Override
    public void run() {
        try {
            Alternative alt = new Alternative(new Guard[]{adIn, summaryIn, joinOut, adCallServer, rosterCallServer});
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
                        String id = adCallServer.startRead();
                        HashSet<Advertisement> versions = ads.get(id);
                        Advertisement max = versions.stream().max((a, b) -> Long.compare(a.timestamp, b.timestamp)).orElse(null);
                        adCallServer.endRead(max);
                        break;
                    }
                    case 4: // rosterCallServer
                    {
                        rosterCallServer.startRead();
                        ArrayList<Advertisement> roster = new ArrayList<>();
                        for (Entry<String, HashSet<Advertisement>> e : ads.entrySet()) {
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
