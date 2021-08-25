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
import com.erhannis.mathnstuff.Pair;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import jcsp.helpers.FCClient;
import jcsp.helpers.JcspUtils;
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.AltingFCServer;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.CSProcess;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;
import jcsp.lang.Guard;
import jcsp.lang.Parallel;
import jcsp.lang.ProcessManager;
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

        public WsClient(DataOwner dataOwner, ChannelOutput<Summary> internalSummaryOut, ChannelOutput<List<Advertisement>> internalRosterOut, ChannelOutput<Pair<Comm, Boolean>> internalStatusOut) {
            this.dataOwner = dataOwner;
            this.internalSummaryOut = internalSummaryOut;
            this.internalRosterOut = internalRosterOut;
            this.internalStatusOut = internalStatusOut;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            System.out.println("CWS Open");
            final TcpComm comm = socket2comm.get(webSocket);
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
            TcpComm comm = socket2comm.get(webSocket);
            //dataOwner.observedNode(new NodeInfo(info.id, info.url, info.summary, NodeInfo.State.INACTIVE));
            internalStatusOut.write(Pair.gen(comm, false));
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            System.err.println("CWS Error : " + t.getMessage());
            TcpComm comm = socket2comm.get(webSocket);
            //dataOwner.observedNode(new NodeInfo(info.id, info.url, info.summary, NodeInfo.State.INACTIVE));
            internalStatusOut.write(Pair.gen(comm, false));
        }

        public void connect(TcpComm comm) {
            System.out.println("CWS addNode " + comm.owner.id + " via " + comm);
            dataOwner.errOnce("WsClient //TODO Split websocket channels?");
            WebSocket ws = client.newWebSocket(new Request.Builder().url("http://" + comm.address + "/ws/updates").build(), this);
            socket2comm.put(ws, comm);
            // For availability robustness, self-report to server
            //ws.send(dataOwner.ID + ";" + dataOwner.PORT + ";" + dataOwner.localSummary.get());
        }

        public void shutdown() {
            for (WebSocket ws : socket2comm.keySet()) {
                ws.close(NORMAL_CLOSURE_STATUS, "Shutting down");
            }
            client.dispatcher().executorService().shutdown();
        }
    }

    private final DataOwner dataOwner;

    private final AltingChannelInput<List<Comm>> subscribeIn;
    private final ChannelOutput<Summary> summaryOut;
    private final ChannelOutput<Advertisement> rosterOut;
    private final AltingFCServer<List<Comm>, Pair<String, InputStream>> dataCall;
    private final FCClient<String, Advertisement> adCall;
    private final ChannelOutput<Pair<Comm, Boolean>> statusOut;

    public TcpGetComm(DataOwner dataOwner, AltingChannelInput<List<Comm>> subscribeIn, ChannelOutput<Summary> summaryOut, ChannelOutput<Advertisement> rosterOut, AltingFCServer<List<Comm>, Pair<String, InputStream>> dataCall, FCClient<String, Advertisement> adCall, ChannelOutput<Pair<Comm, Boolean>> statusOut) {
        this.dataOwner = dataOwner;
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
        Any2OneChannel<Pair<Comm, Boolean>> internalStatusChannel = Channel.<Pair<Comm, Boolean>>any2one();

        WsClient wc = new WsClient(dataOwner, JcspUtils.logDeadlock(internalSummaryChannel.out()), JcspUtils.logDeadlock(internalRosterChannel.out()), JcspUtils.logDeadlock(internalStatusChannel.out()));

        AltingChannelInput<Summary> internalSummaryIn = internalSummaryChannel.in();
        AltingChannelInput<List<Advertisement>> internalRosterIn = internalRosterChannel.in();
        AltingChannelInput<Pair<Comm, Boolean>> internalStatusIn = internalStatusChannel.in();

        // Fetch process
        Alternative alt = new Alternative(new Guard[]{dataCall, subscribeIn, internalSummaryIn, internalRosterIn, internalStatusIn});
        try {
            while (true) {
                switch (alt.priSelect()) {
                    case 0: // dataCall
                    {
                        List<Comm> comms = dataCall.startRead();
                        //TODO Trying all the Comms could be bad
                        Pair<String, InputStream> result = null;
                        for (Comm comm : comms) {
                            //TODO Why did I decide to do it this way?
                            if (TcpComm.TYPE.equals(comm.type)) {
                                try {
                                    Request request = new Request.Builder().url("http://" + ((TcpComm) comm).address + "/get/data").build();
                                    try {
                                        Response response = dataOwner.ohClient.newCall(request).execute();
                                        result = Pair.gen(response.header("content-type"), response.body().byteStream());
                                        // If work:
                                        break;
                                    } catch (Exception e) {
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        dataCall.endRead(result);
                        break;
                    }
                    case 1: // subscribeIn
                    {
                        List<Comm> comms = subscribeIn.read();
                        dataOwner.errOnce("TcpGetComm //TODO Don't subscribe to a Comm more than once!!");
                        //TODO Permit refreshing of connections
                        for (Comm comm : comms) {
                            if (TcpComm.TYPE.equals(comm.type)) {
                                try {
                                    wc.connect((TcpComm) comm);
                                    //TODO Don't automatically do this?  Have main request it?
                                    new ProcessManager(() -> {
                                        byte[] ladBytes = dataOwner.serialize(adCall.call(dataOwner.ID));
                                        Request request = new Request.Builder().post(RequestBody.create(ladBytes, MediaType.get("lancopy/advertisement"))).url("http://" + ((TcpComm) comm).address + "/post/advertisement").build();
                                        try (Response response = dataOwner.ohClient.newCall(request).execute()) {
                                            //TODO Do something?
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }).start();
                                } catch (Exception e) {
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
                    case 2: // internalSummaryIn
                    {
                        summaryOut.write(internalSummaryIn.read());
                        break;
                    }
                    case 3: // internalRosterIn
                    {
                        List<Advertisement> roster = internalRosterIn.read();
                        //TODO Compactify?  Tracker might be more efficient that way.  Maybe.
                        for (Advertisement ad : roster) {
                            rosterOut.write(ad);
                        }
                        break;
                    }
                    case 4: // internalStatusIn
                    {
                        statusOut.write(internalStatusIn.read());
                        break;
                    }
                }
            }
        } finally {
            dataOwner.errOnce("TcpGetComm //TODO Handle poison");
            wc.shutdown();
        }
    }
}
