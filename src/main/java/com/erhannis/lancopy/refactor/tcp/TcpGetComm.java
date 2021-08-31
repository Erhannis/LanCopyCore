/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor.tcp;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.refactor.Advertisement;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.lancopy.refactor.Summary;
import com.erhannis.mathnstuff.MeUtils;
import com.erhannis.mathnstuff.Pair;
import com.erhannis.mathnstuff.utils.DThread;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import jcsp.helpers.FCClient;
import jcsp.helpers.JcspUtils;
import jcsp.helpers.JcspUtils.DeadlockLoggingChannelOutput;
import jcsp.lang.Alternative;
import jcsp.lang.AltingBarrier;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.AltingFCServer;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.CSProcess;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;
import jcsp.lang.Crew;
import jcsp.lang.Guard;
import jcsp.lang.ProcessManager;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 *
 * @author erhannis
 */
public class TcpGetComm implements CSProcess {

    public class WsClient extends WebSocketListener {

        private static final int NORMAL_CLOSURE_STATUS = 1000;

        private final DataOwner dataOwner;

        private final ConcurrentHashMap<WebSocket, TcpComm> socket2comm = new ConcurrentHashMap<>();
        private final OkHttpClient client = new OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).build();

        private final ChannelOutput<Summary> internalSummaryOut;
        private final ChannelOutput<List<Advertisement>> internalRosterOut;
        private final ChannelOutput<Pair<Comm, Boolean>> internalStatusOut;

        private final Crew crew = new Crew();

        public WsClient(DataOwner dataOwner, ChannelOutput<Summary> internalSummaryOut, ChannelOutput<List<Advertisement>> internalRosterOut, ChannelOutput<Pair<Comm, Boolean>> internalStatusOut) {
            this.dataOwner = dataOwner;
            this.internalSummaryOut = internalSummaryOut;
            this.internalRosterOut = internalRosterOut;
            this.internalStatusOut = internalStatusOut;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            System.out.println("CWS Open");
            crew.startRead();
            TcpComm comm;
            try {
                comm = socket2comm.get(webSocket);
            } finally {
                crew.endRead();
            }
            if (comm == null) {
                System.err.println("COMM NULL");
            }
            internalStatusOut.write(Pair.gen(comm, true));
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            System.out.println("CWS Receiving : " + text);
            // WS msgs now binary
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            System.out.println("CWS Receiving bytes : " + bytes.hex());
            if (bytes.size() == 0) {
                return;
            }
            Object o = dataOwner.deserialize(bytes.toByteArray());
            if (o instanceof List) {
                List l = (List) o;
                for (Object a : l) {
                    if (!(a instanceof Advertisement)) {
                        throw new IllegalArgumentException("CWS rx list not of Advertisement");
                    }
                }
                internalRosterOut.write((List<Advertisement>) l);
            } else if (o instanceof Summary) {
                internalSummaryOut.write((Summary) o);
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(NORMAL_CLOSURE_STATUS, null); //TODO Should this?
            System.out.println("CWS Closing : " + code + " / " + reason);
            crew.startRead();
            TcpComm comm;
            try {
                comm = socket2comm.get(webSocket);
            } finally {
                crew.endRead();
            }
            //dataOwner.observedNode(new NodeInfo(info.id, info.url, info.summary, NodeInfo.State.INACTIVE));
            if (comm == null) {
                System.err.println("COMM NULL");
            }
            internalStatusOut.write(Pair.gen(comm, false));
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            System.err.println("CWS Error : " + t.getMessage());
            crew.startRead();
            TcpComm comm;
            try {
                comm = socket2comm.get(webSocket);
            } finally {
                crew.endRead();
            }
            //dataOwner.observedNode(new NodeInfo(info.id, info.url, info.summary, NodeInfo.State.INACTIVE));
            if (comm == null) {
                System.err.println("COMM NULL");
            }
            internalStatusOut.write(Pair.gen(comm, false));
        }

        public void connect(TcpComm comm) {
            System.out.println("CWS addNode " + comm.owner.id + " via " + comm);
            dataOwner.errOnce("WsClient //TODO Split websocket channels?");
            crew.startWrite();
            try {
                WebSocket ws = client.newWebSocket(new Request.Builder().url(new HttpUrl.Builder().scheme(comm.scheme).host(comm.host).port(comm.port) + "ws/updates").build(), this);
                socket2comm.put(ws, comm);
            } finally {
                crew.endWrite();
            }
            // For availability robustness, self-report to server
            //ws.send(dataOwner.ID + ";" + dataOwner.PORT + ";" + dataOwner.localSummary.get());
        }

        public void shutdown() {
            crew.startRead();
            try {
                for (WebSocket ws : socket2comm.keySet()) {
                    ws.close(NORMAL_CLOSURE_STATUS, "Shutting down");
                }
            } finally {
                crew.endRead();
            }
            client.dispatcher().executorService().shutdown();
        }
    }

    private final DataOwner dataOwner;

    private final AltingChannelInput<List<Comm>> subscribeIn;
    private final AltingChannelInput<Collection<Comm>> pokeIn;
    private final ChannelOutput<Summary> summaryOut;
    private final ChannelOutput<Advertisement> rosterOut;
    private final AltingFCServer<List<Comm>, Pair<String, InputStream>> dataCall;
    private final FCClient<String, Advertisement> adCall;
    private final ChannelOutput<Pair<Comm, Boolean>> statusOut;

    public TcpGetComm(DataOwner dataOwner, AltingChannelInput<List<Comm>> subscribeIn, AltingChannelInput<Collection<Comm>> pokeIn, ChannelOutput<Summary> summaryOut, ChannelOutput<Advertisement> rosterOut, AltingFCServer<List<Comm>, Pair<String, InputStream>> dataCall, FCClient<String, Advertisement> adCall, ChannelOutput<Pair<Comm, Boolean>> statusOut) {
        this.dataOwner = dataOwner;
        this.pokeIn = pokeIn;
        this.subscribeIn = subscribeIn;
        this.summaryOut = summaryOut;
        this.rosterOut = rosterOut;
        this.dataCall = dataCall;
        this.adCall = adCall;
        this.statusOut = statusOut;
    }

    @Override
    public void run() {
        dataOwner.errOnce("TcpGetComm //TODO Permit request roster?");

        Any2OneChannel<Summary> internalSummaryChannel = Channel.<Summary>any2one();
        Any2OneChannel<List<Advertisement>> internalRosterChannel = Channel.<List<Advertisement>>any2one();
        Any2OneChannel<Pair<Comm, Boolean>> internalStatusChannel = Channel.<Pair<Comm, Boolean>>any2one(0);

        // Used both in the WsClient and in some of the one-off threads down below
        ChannelOutput<Pair<Comm, Boolean>> internalStatusOut = JcspUtils.logDeadlock(internalStatusChannel.out());
        
        WsClient wc = new WsClient(dataOwner, JcspUtils.logDeadlock(internalSummaryChannel.out()), JcspUtils.logDeadlock(internalRosterChannel.out()), internalStatusOut);

        AltingChannelInput<Summary> internalSummaryIn = internalSummaryChannel.in();
        AltingChannelInput<List<Advertisement>> internalRosterIn = internalRosterChannel.in();
        AltingChannelInput<Pair<Comm, Boolean>> internalStatusIn = internalStatusChannel.in();

        // Fetch process
        Alternative alt = new Alternative(new Guard[]{dataCall, subscribeIn, pokeIn, internalSummaryIn, internalRosterIn, internalStatusIn});
        try {
            while (true) {
                switch (alt.priSelect()) {
                    case 0: // dataCall
                    {
                        List<Comm> comms = dataCall.startRead();
                        //TODO Trying all the Comms could be bad
                        final Pair<String, InputStream> result;

                        boolean parallel = (Boolean) dataOwner.options.getOrDefault("GetComm.PARALLEL_ATTEMPTS", true);
                        boolean highLatency = (Boolean) dataOwner.options.getOrDefault("GetComm.HIGH_LATENCY", false);
                        boolean checkPoke = (Boolean) dataOwner.options.getOrDefault("GetComm.REQUIRE_POKE_STATUS_SUCCESS", true);

                        if (parallel) {
                            Any2OneChannel<Pair<String, InputStream>> resultChannel = Channel.<Pair<String, InputStream>>any2one(0);
                            AltingChannelInput<Pair<String, InputStream>> resultIn = resultChannel.in();
                            DeadlockLoggingChannelOutput<Pair<String, InputStream>> resultOut = JcspUtils.logDeadlock(resultChannel.out());

                            AltingBarrier failureBarrier = AltingBarrier.create();

                            //TODO Some kinda thread pool?
                            HashSet<Thread> attempts = new HashSet<>();
                            for (Comm comm : comms) {
                                try {
                                    //TODO Why did I decide to do it this way?
                                    if (TcpComm.TYPE.equals(comm.type)) {
                                        TcpComm tc = (TcpComm) comm;
                                        //TODO Use the results to update status?

                                        if (highLatency) {
                                            // Don't wait for a test GET to come back - immediately start fetching from all endpoints and see which one wins
                                            AltingBarrier bar = failureBarrier.expand();
                                            attempts.add(new DThread(() -> {
                                                bar.mark();
                                                System.out.println("-->Poke call " + comm);
                                                Request request = new Request.Builder().url(new HttpUrl.Builder().scheme(tc.scheme).host(tc.host).port(tc.port).addPathSegments("get/data").build()).build();
                                                try {
                                                    Response response = dataOwner.ohClient.newCall(request).execute();
                                                    System.out.println("Request succeeded! " + comm);
                                                    resultOut.write(Pair.gen(response.header("content-type"), response.body().byteStream()), comm+"");
                                                } catch (Throwable e) {
                                                    System.err.println("Request failed!" + comm);
                                                    e.printStackTrace();
                                                }
                                                bar.contract();
                                            }));
                                        } else {
                                            // Send test GET first, to see which comms work, without incurring large costs
                                            AltingBarrier bar = failureBarrier.expand();
                                            attempts.add(new DThread(() -> {
                                                bar.mark();
                                                System.out.println("-->Poke call " + comm);
                                                dataOwner.errOnce("TcpGetComm //TODO okhttp doesn't seem to always react to interruption");
                                                Request request = new Request.Builder().url(new HttpUrl.Builder().scheme(tc.scheme).host(tc.host).port(tc.port).addPathSegments("get/poke").build()).build();
                                                try {
                                                    Response response = dataOwner.ohClient.newCall(request).execute();
                                                    if (checkPoke) {
                                                        if (!response.isSuccessful()) {
                                                            System.err.println("Poke returned status " + response.code() + "; returning! " + comm);
                                                            bar.contract();
                                                            return;
                                                        }
                                                    }
                                                    System.err.println("Poke succeeded! " + comm);
                                                } catch (Throwable e) {
                                                    System.err.println("Poke failed; returning! " + comm);
                                                    e.printStackTrace();
                                                    bar.contract();
                                                    return;
                                                }
                                                request = new Request.Builder().url(new HttpUrl.Builder().scheme(tc.scheme).host(tc.host).port(tc.port).addPathSegments("get/data").build()).build();
                                                try {
                                                    Response response = dataOwner.ohClient.newCall(request).execute();
                                                    System.err.println("Request succeeded! " + comm);
                                                    resultOut.write(Pair.gen(response.header("content-type"), response.body().byteStream()), comm+"");
                                                } catch (Throwable e) {
                                                    System.err.println("Request failed! " + comm);
                                                    e.printStackTrace();
                                                }
                                                bar.contract();
                                            }));
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            for (Thread t : attempts) {
                                t.start();
                            }

                            Alternative getAlt = new Alternative(new Guard[]{resultIn, failureBarrier});
                            switch (getAlt.priSelect()) {
                                case 0: { // resultIn
                                    result = resultIn.read();
                                    System.out.println("dataCall rx resultIn");
                                    resultIn.poison(10);
                                    for (Thread t : attempts) {
                                        t.interrupt();
                                    }
                                    break;
                                }
                                case 1: { // failureBarrier
                                    failureBarrier.contract();
                                    System.out.println("dataCall - All comms failed");
                                    result = null;
                                    break;
                                }
                                default: {
                                    throw new AssertionError();
                                }
                            }
                        } else {
                            Pair<String, InputStream> result0 = null;
                            for (Comm comm : comms) {
                                try {
                                    //TODO Why did I decide to do it this way?
                                    if (TcpComm.TYPE.equals(comm.type)) {
                                        TcpComm tc = (TcpComm) comm;
                                        Request request = new Request.Builder().url(new HttpUrl.Builder().scheme(tc.scheme).host(tc.host).port(tc.port).addPathSegments("get/data").build()).build();
                                        try {
                                            Response response = dataOwner.ohClient.newCall(request).execute();
                                            result0 = Pair.gen(response.header("content-type"), response.body().byteStream());
                                            // If work:
                                            break;
                                        } catch (Throwable e) {
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            result = result0;
                        }
                        dataCall.endRead(result);
                        break;
                    }

                    case 1: // subscribeIn
                    {
                        List<Comm> comms = subscribeIn.read();
                        dataOwner.errOnce("TcpGetComm //TODO Don't subscribe to a Comm more than once!!");
                        byte[] ladBytes = dataOwner.serialize(adCall.call(dataOwner.ID));
                        //TODO Permit refreshing of connections
                        for (Comm comm : comms) {
                            if (TcpComm.TYPE.equals(comm.type)) {
                                try {
                                    wc.connect((TcpComm) comm);
                                    //TODO Don't automatically do this?  Have main request it?
                                    new ProcessManager(() -> {
                                        TcpComm tc = (TcpComm) comm;
                                        Request request = new Request.Builder().post(RequestBody.create(ladBytes, MediaType.get("lancopy/advertisement"))).url(new HttpUrl.Builder().scheme(tc.scheme).host(tc.host).port(tc.port).addPathSegments("post/advertisement").build()).build();
                                        //TODO Use info for comm status?
                                        try (Response response = dataOwner.ohClient.newCall(request).execute()) {
                                            //TODO Do something?
                                        } catch (ConnectException | NoRouteToHostException e) {
                                            MeUtils.getStackTrace(e.getMessage()).printStackTrace();
                                        } catch (SocketTimeoutException e) {
                                            MeUtils.getStackTrace(e.getMessage()).printStackTrace();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }).start();
                                } catch (Throwable e) {
                                    e.printStackTrace();
                                }
                                // If work:
                                //TODO How do we know?  Wait for several seconds on multiple comms?  Try all and see which ones make it?
                                //  I guess for now, try all.  But it's wasteful.
                                //TODO Make less wasteful.
                                //break;
                            }
                        }
                        break;
                    }
                    case 2: // pokeIn
                    {
                        boolean checkPoke = (Boolean) dataOwner.options.getOrDefault("GetComm.REQUIRE_POKE_STATUS_SUCCESS", true);
                        Collection<Comm> comms = pokeIn.read();
                        for (Comm comm : comms) {
                            if (TcpComm.TYPE.equals(comm.type)) {
                                try {
                                    new ProcessManager(() -> {
                                        TcpComm tc = (TcpComm) comm;
                                        Request request = new Request.Builder().get().url(new HttpUrl.Builder().scheme(tc.scheme).host(tc.host).port(tc.port).addPathSegments("get/poke").build()).build();
                                        try (Response response = dataOwner.ohClient.newCall(request).execute()) {
                                            if (checkPoke) {
                                                internalStatusOut.write(Pair.gen(comm, response.isSuccessful()));
                                            } else {
                                                internalStatusOut.write(Pair.gen(comm, true));
                                            }
                                            return;
                                        } catch (ConnectException | NoRouteToHostException e) {
                                            MeUtils.getStackTrace(e.getMessage()).printStackTrace();
                                        } catch (SocketTimeoutException e) {
                                            MeUtils.getStackTrace(e.getMessage()).printStackTrace();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        internalStatusOut.write(Pair.gen(comm, false));
                                    }).start();
                                } catch (Throwable e) {
                                    e.printStackTrace();
                                }
                                // If work:
                                //TODO How do we know?  Wait for several seconds on multiple comms?  Try all and see which ones make it?
                                //  I guess for now, try all.  But it's wasteful.
                                //TODO Make less wasteful.
                                //break;
                            }
                        }
                        break;
                    }
                    case 3: // internalSummaryIn
                    {
                        summaryOut.write(internalSummaryIn.read());
                        break;
                    }
                    case 4: // internalRosterIn
                    {
                        List<Advertisement> roster = internalRosterIn.read();
                        //TODO Compactify?  Tracker might be more efficient that way.  Maybe.
                        for (Advertisement ad : roster) {
                            rosterOut.write(ad);
                        }
                        break;
                    }
                    case 5: // internalStatusIn
                    {
                        statusOut.write(internalStatusIn.read());
                        break;
                    }
                }
            }
        } finally {
            dataOwner.errOnce("TcpGetComm //TODO Handle poison");
            wc.shutdown();
            internalStatusIn.poison(10);
        }
    }
}
