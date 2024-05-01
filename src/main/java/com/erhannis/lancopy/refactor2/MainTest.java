/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.refactor.Advertisement;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.lancopy.refactor.Summary;
import com.erhannis.lancopy.refactor.tcp.TcpComm;
import com.erhannis.lancopy.refactor2.NodeManager.ChannelReader;
import com.erhannis.lancopy.refactor2.tcp.TcpCommChannel;
import com.erhannis.mathnstuff.MeUtils;
import com.erhannis.mathnstuff.Pair;
import com.erhannis.mathnstuff.utils.DThread;
import com.erhannis.mathnstuff.utils.Options;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import jcsp.helpers.NameParallel;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.CSProcess;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;
import jcsp.lang.Parallel;
import jcsp.lang.ProcessManager;

/**
 *
 * @author erhannis
 */
public class MainTest {

    public static void main(String[] args) throws InterruptedException, IOException {
        if (1 == 1) {
            final PipedInputStream pis = new PipedInputStream(1024 * 256);
            final PipedOutputStream pos = new PipedOutputStream(pis);
            new NameParallel(new CSProcess[]{
                () -> {
                    try {
                        for (int i = 0; i < 10; i++) {
                            try {
                                System.out.println("--> WRITER tx " + i);
                                pos.write(i);
                                System.out.println("<-- WRITER tx " + i);
                            } catch (IOException ex) {
                                Logger.getLogger(MainTest.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                        pos.flush();
                        pos.close();
                    } catch (IOException ex) {
                        Logger.getLogger(MainTest.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }, () -> {
                    while (true) {
                        try {
                            byte[] msg = new byte[1024];
                            int count = pis.read(msg);
                            if (count < 0) {
                                System.out.println("READER exit");
                                return;
                            } else {
                                System.out.println("READER rx " + Arrays.toString(msg));
                            }
                        } catch (IOException ex) {
                            Logger.getLogger(MainTest.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }).run();

            if (1 == 1) {
                return;
            }
        }
        new DThread(() -> {
            main2(false, new String[]{});
        }).start();
        Thread.sleep(1000);
        main2(true, new String[]{});
    }

    public static void main2(boolean toggle, String[] args) {
        //boolean toggle = false;
        int i = 0;
        int j = 1;
        if (args.length >= 2) {
            i = Integer.parseInt(args[0]);
            j = Integer.parseInt(args[1]);
        }
        if (toggle) {
            int k = i;
            i = j;
            j = k;
        }
        DataOwner dataOwner = new DataOwner("options" + i + ".dat", null, (msg) -> true);
        dataOwner.options.set("Security.KEYSTORE_PATH", "lancopy" + i + ".ks");
        dataOwner.options.set("Security.TRUSTSTORE_PATH", "lancopy" + i + ".ts");
        dataOwner.options.set("Security.ENCRYPTION", true);
        try {
            Options.saveOptions(dataOwner.options, "options" + i + ".dat");
        } catch (IOException ex) {
            Logger.getLogger(MainTest.class.getName()).log(Level.SEVERE, null, ex);
        }

        Any2OneChannel<List<Comm>> lcommsChannel = Channel.<List<Comm>>any2one();
        AltingChannelInput<List<Comm>> lcommsIn = lcommsChannel.in();
        ChannelOutput<List<Comm>> lcommsOut = lcommsChannel.out();

        Any2OneChannel<Advertisement> radChannel = Channel.<Advertisement>any2one();
        AltingChannelInput<Advertisement> radIn = radChannel.in();
        ChannelOutput<Advertisement> radOut = radChannel.out();

        Any2OneChannel<Advertisement> aadChannel = Channel.<Advertisement>any2one();
        AltingChannelInput<Advertisement> aadIn = aadChannel.in();
        ChannelOutput<Advertisement> aadOut = aadChannel.out();

        Any2OneChannel<Summary> rsumChannel = Channel.<Summary>any2one();
        AltingChannelInput<Summary> rsumIn = rsumChannel.in();
        ChannelOutput<Summary> rsumOut = rsumChannel.out();

        Any2OneChannel<Summary> lsumChannel = Channel.<Summary>any2one();
        AltingChannelInput<Summary> lsumIn = lsumChannel.in();
        ChannelOutput<Summary> lsumOut = lsumChannel.out();

        Any2OneChannel<Pair<Comm, Boolean>> commStatusChannel = Channel.<Pair<Comm, Boolean>>any2one();
        AltingChannelInput<Pair<Comm, Boolean>> commStatusIn = commStatusChannel.in();
        ChannelOutput<Pair<Comm, Boolean>> commStatusOut = commStatusChannel.out();

        Any2OneChannel<List<Comm>> subscribeChannel = Channel.<List<Comm>>any2one();
        AltingChannelInput<List<Comm>> subscribeIn = subscribeChannel.in();
        ChannelOutput<List<Comm>> subscribeOut = subscribeChannel.out();

        int localPort = 10000 + i;
        UUID remoteId = UUID.randomUUID();
        int remotePort = 10000 + j;

        //dataOwner.options.set("Comms.tcp.server_port", 0);
        dataOwner.options.set("Comms.tcp.server_port", localPort);
        List<Comm> lComms = Lists.newArrayList(new TcpComm(null, "localhost", localPort));
        Advertisement lad = new Advertisement(dataOwner.ID, System.currentTimeMillis(), lComms, true, null);

        CommsManager cm = new CommsManager(dataOwner, lcommsOut, radOut, aadIn, rsumOut, lsumIn, commStatusOut, subscribeIn, null, null, null, null, null, null, null, null);

        new ProcessManager(new NameParallel(new CSProcess[]{
            cm,
            () -> {
                while (true) {
                    System.out.println("rx lcomms: " + lcommsIn.read());
                }
            }, () -> {
                while (true) {
                    System.out.println("rx rad: " + radIn.read());
                }
            }, () -> {
                while (true) {
                    System.out.println("rx rsum: " + rsumIn.read());
                }
            }, () -> {
                while (true) {
                    System.out.println("rx commStatus: " + commStatusIn.read());
                }
            }, () -> {
                while (true) {
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(MainTest.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    lsumOut.write(new Summary(dataOwner.ID, System.currentTimeMillis(), "current time: " + System.currentTimeMillis()));
                }
            }
        })).start();

        aadOut.write(lad);

        List<Comm> comms = Lists.newArrayList(new TcpComm(null, "localhost", remotePort));
        Advertisement rad = new Advertisement(remoteId, System.currentTimeMillis(), comms, true, null);
        subscribeOut.write(rad.comms);

        while (true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ex) {
                Logger.getLogger(MainTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
