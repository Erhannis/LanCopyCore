/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.refactor2.tcp.TcpCommChannel;
import java.io.DataOutput;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jcsp.helpers.JcspUtils;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.CSProcess;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;

/**
 *
 * @author erhannis
 */
public class CommsManager implements CSProcess {
    public static class CommsToken {
        // ???
    }

    private final DataOwner dataOwner;
    
    public CommsManager(DataOwner dataOwner) {
        this.dataOwner = dataOwner;
    }

    private final ChannelOutput<CommChannel> serverChannelOut;

    public void blah1() {
        //TODO Fallback to "Comms.tcp.enabled"?
        boolean tcpEnabled = (Boolean) dataOwner.options.getOrDefault("Comms.tcp.server_enabled", true);
        
        int port = (int) dataOwner.options.getOrDefault("Comms.tcp.server_port", 0);
        while (true) {
            try {
                TcpCommChannel.serverThread(sc -> {
                    TcpCommChannel tcpCommChannel = new TcpCommChannel(interrupt -> {
                        System.err.println("//TODO Handle interrupt properly");
                        return true;
                    }, sc);
                    serverChannelOut.write(tcpCommChannel);
                }, port);
            } catch (IOException ex) {
                Logger.getLogger(CommsManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    @Override
    public void run() {
        Any2OneChannel<CommChannel> serverChannelChannel = Channel.<CommChannel> any2one();
        AltingChannelInput<CommChannel> serverChannelIn = serverChannelChannel.in();
        ChannelOutput<CommChannel> serverChannelOut = serverChannelChannel.out();
        
    }
}
