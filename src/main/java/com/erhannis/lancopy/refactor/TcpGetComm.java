/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import jcsp.lang.Alternative;
import jcsp.lang.AltingFunctionChannel;
import jcsp.lang.CSProcess;
import jcsp.lang.ChannelOutput;
import jcsp.lang.Guard;

/**
 *
 * @author erhannis
 */
public class TcpGetComm implements CSProcess {
    private static final String TYPE = "TCP";
    
    private final ChannelOutput<Summary> summaryOut;
    private final ChannelOutput<List<Advertisement>> rosterOut;
    private final AltingFunctionChannel<List<Comm>, InputStream> dataCall;
    private final AltingFunctionChannel<List<Comm>, List<Advertisement>> rosterCall;

    public TcpGetComm(ChannelOutput<Summary> summaryOut, ChannelOutput<List<Advertisement>> rosterOut, AltingFunctionChannel<Comm, InputStream> dataCall, AltingFunctionChannel<Comm, List<Advertisement>> rosterCall) {
        this.summaryOut = summaryOut;
        this.rosterOut = rosterOut;
        this.dataCall = dataCall;
        this.rosterCall = rosterCall;
    }
    
    @Override
    public void run() {
        Alternative alt = new Alternative(new Guard[]{dataCall, rosterCall});
        while (true) {
            switch (alt.priSelect()) {
                case 0: // dataCall
                    {
                        List<Comm> comms = dataCall.startRead();
                        InputStream result = null;
                        //TODO Trying all the Comms could be bad
                        for (Comm comm : comms) {
                            if (TYPE.equals(comm.type)) {
                                asdf;
                                // If work:
                                break;
                            }
                        }
                        dataCall.endRead(result);
                        break;
                    }
                case 1: // rosterCall
                    {
                        List<Comm> comms = rosterCall.startRead();
                        List<Advertisement> result = null;
                        //TODO Trying all the Comms could be bad
                        for (Comm comm : comms) {
                            if (TYPE.equals(comm.type)) {
                                asdf;
                                // If work:
                                break;
                            }
                        }
                        rosterCall.endRead(result);
                        break;
                    }
            }
        }
    }
}
