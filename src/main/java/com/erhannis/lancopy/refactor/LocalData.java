/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.data.Data;
import jcsp.helpers.FCServer;
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.AltingFCServer;
import jcsp.lang.CSProcess;
import jcsp.lang.ChannelOutput;
import jcsp.lang.Guard;

/**
 *
 * @author erhannis
 */
public class LocalData implements CSProcess {
    private final DataOwner dataOwner;

    private Data data = null;
    public final AltingFCServer<Void, Data> dataFC;
    private final ChannelOutput<Summary> summaryOut;
    private final AltingChannelInput<Data> dataIn;

    public LocalData(DataOwner dataOwner, AltingFCServer<Void, Data> dataFC, ChannelOutput<Summary> summaryOut, AltingChannelInput<Data> dataIn) {
        this.dataOwner = dataOwner;
        this.dataFC = dataFC;
        this.summaryOut = summaryOut;
        this.dataIn = dataIn;
    }
    
    @Override
    public void run() {
        try {
            Alternative alt = new Alternative(new Guard[]{dataIn, dataFC});
            while (true) {
                switch (alt.priSelect()) {
                    case 0: // dataIn
                        data = dataIn.read();
                        String summary = data.toString();
                        int summaryLength = (int) dataOwner.options.getOrDefault("summary_length", 100);
                        summary = summary.substring(0, Math.min(summary.length(), summaryLength));
                        summaryOut.write(new Summary(dataOwner.ID, System.currentTimeMillis(), summary));
                        break;
                    case 1: // dataFC
                        dataFC.startRead();
                        dataFC.endRead(data);
                        break;
                }
            }
        } finally {
            System.out.println("LocalData shutting down");
            dataOwner.errOnce("LocalData //TODO Handle poison");
        }
    }    
}
