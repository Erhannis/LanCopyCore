/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.refactor.tcp;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.data.Data;
import com.erhannis.lancopy.refactor.Advertisement;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.lancopy.refactor.Summary;
import com.erhannis.mathnstuff.MeUtils;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import jcsp.helpers.FCClient;
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.CSProcess;
import jcsp.lang.ChannelOutput;
import jcsp.lang.Guard;
import org.eclipse.jetty.util.MultiException;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import spark.Spark;

/**
 * Server Comm, over TCP<br/>
 * Responsible for publishing updates and serving data
 *
 * @author erhannis
 */
public class TcpPutComm implements CSProcess {

    @WebSocket
    public static class WsServer {

        private final DataOwner dataOwner;

        private final FCClient<String, Summary> summaryCall;
        private final FCClient<Void, List<Advertisement>> rosterCall;

        private static final Queue<Session> sessions = new ConcurrentLinkedQueue<>();

        public WsServer(DataOwner dataOwner, FCClient<String, Summary> summaryCall, FCClient<Void, List<Advertisement>> rosterCall) {
            this.dataOwner = dataOwner;
            this.summaryCall = summaryCall;
            this.rosterCall = rosterCall;
        }

        @OnWebSocketConnect
        public void connected(Session session) throws IOException {
            System.out.println("SWS Connected");
            sessions.add(session);
            try {
                Summary summary = summaryCall.call(dataOwner.ID);
                byte[] sbytes = dataOwner.serialize(summary);
                RemoteEndpoint re = session.getRemote();
                synchronized (re) {
                    re.sendBytes(ByteBuffer.wrap(sbytes));
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
            try {
                List<Advertisement> roster = rosterCall.call(null);
                byte[] rbytes = dataOwner.serialize(roster);
                RemoteEndpoint re = session.getRemote();
                synchronized (re) {
                    re.sendBytes(ByteBuffer.wrap(rbytes));
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        @OnWebSocketClose
        public void closed(Session session, int statusCode, String reason) {
            System.out.println("SWS Closing : " + statusCode + " / " + reason);
            sessions.remove(session);
        }

        @OnWebSocketMessage
        public void message(Session session, String message) throws IOException {
            System.out.println("SWS Receiving : " + message);
        }

        public void broadcast(byte[] msg) {
            MultiException me = new MultiException();
            ByteBuffer bb = ByteBuffer.wrap(msg);
            for (Session s : sessions) {
                try {
                    RemoteEndpoint re = s.getRemote();
                    synchronized (re) {
                        re.sendBytes(bb);
                    }
                } catch (IOException ex) {
                    me.addSuppressed(ex);
                }
            }
            if (me.getSuppressed().length > 0) {
                try {
                    throw me;
                } catch (MultiException ex) {
                    Logger.getLogger(WsServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    private final DataOwner dataOwner;

    private final ChannelOutput<List<Comm>> commsOut; //TODO Support changes?  Removals?
    private final ChannelOutput<Advertisement> rxAdOut;
    private final AltingChannelInput<Advertisement> txRosterIn;
    private final AltingChannelInput<Summary> txLSummaryIn;
    private final FCClient<Void, Data> ldataCall;
    private final FCClient<String, Summary> summaryCall;
    private final FCClient<Void, List<Advertisement>> rosterCall;

    public TcpPutComm(DataOwner dataOwner, ChannelOutput<List<Comm>> commsOut, ChannelOutput<Advertisement> rxAdOut, AltingChannelInput<Advertisement> txRosterIn, AltingChannelInput<Summary> txLSummaryIn, FCClient<Void, Data> ldataCall, FCClient<String, Summary> summaryCall, FCClient<Void, List<Advertisement>> rosterCall) {
        this.dataOwner = dataOwner;
        this.commsOut = commsOut;
        this.rxAdOut = rxAdOut;
        this.txRosterIn = txRosterIn;
        this.txLSummaryIn = txLSummaryIn;
        this.ldataCall = ldataCall;
        this.summaryCall = summaryCall;
        this.rosterCall = rosterCall;
    }

    @Override
    public void run() {
        try {
            WsServer wsServer = new WsServer(dataOwner, summaryCall, rosterCall);

            //TODO Split websocket channels?
            //TODO Hmm, I've kinda failed my usual thing of insulating inner processes from outer processes
            //TODO Not sure you can have more than one Spark server, so that might complicate composition
            Spark.port(0);
            Spark.webSocket("/ws/updates", wsServer);
            Spark.post("post/advertisement", (request, response) -> {
                //MAYBE Check mime type?  It's not really necessary....
                Advertisement ad = (Advertisement) dataOwner.deserialize(request.bodyAsBytes()); //TODO InputStream?
                rxAdOut.write(ad);
                return null;
            });
            Spark.get("/get/time", (request, response) -> {
                return System.currentTimeMillis();
            });
            Spark.get("/get/data", (request, response) -> {
                Data data = ldataCall.call(null);
                response.type(data.getMime());
                return data.serialize();
            });
            Spark.get("/get/roster", (request, response) -> {
                List<Advertisement> roster = rosterCall.call(null);
                response.type("lancopy/roster");
                return dataOwner.serialize(roster);
            });
            Spark.awaitInitialization();
            int port = Spark.port();
            System.out.println("TcpPutComm " + dataOwner.ID + " starting on port " + port);

            //TODO Allow whitelist/blacklist interfaces
            dataOwner.errOnce("TcpPutComm //TODO Deal with interface changes?");
            ArrayList<Comm> newComms = new ArrayList<>();
            for (InetAddress addr : MeUtils.listAllInterfaceAddresses()) {
                try {
                    newComms.add(new TcpComm(null, "http", InetAddress.getByAddress(addr.getAddress()).getHostAddress(), port));
                } catch (UnknownHostException ex) {
                    Logger.getLogger(TcpPutComm.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            commsOut.write(newComms);

            Alternative alt = new Alternative(new Guard[]{txRosterIn, txLSummaryIn});
            while (true) {
                switch (alt.priSelect()) {
                    case 0: // txRosterIn
                        Advertisement roster = txRosterIn.read();
                        wsServer.broadcast(dataOwner.serialize(roster));
                        break;
                    case 1: // txLSummaryIn
                        Summary lSummary = txLSummaryIn.read();
                        wsServer.broadcast(dataOwner.serialize(lSummary));
                        break;
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        } finally {
            System.out.println("TcpPutComm shutting down");
            dataOwner.errOnce("TcpPutComm //TODO Handle poison");
            dataOwner.errOnce("TcpPutComm //TODO Remove comms");
            Spark.stop();
            Spark.awaitStop();
            //ws.shutdown();
        }
    }
}
