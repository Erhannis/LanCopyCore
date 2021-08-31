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
import fi.iki.elonen.NanoHTTPD.Response.IStatus;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import fi.iki.elonen.NanoWSD;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

/**
 * Server Comm, over TCP<br/>
 * Responsible for publishing updates and serving data
 *
 * @author erhannis
 */
public class TcpPutComm implements CSProcess {
    public static class DebugWebSocketServer extends NanoWSD {
        private static final Logger LOG = Logger.getLogger(DebugWebSocketServer.class.getName());

        private final DataOwner dataOwner;
        
        private final boolean debug0 = true;

        private final ChannelOutput<Advertisement> rxAdOut;
        private final FCClient<Void, Data> ldataCall;
        private final FCClient<String, Summary> summaryCall;
        private final FCClient<Void, List<Advertisement>> rosterCall;

        public DebugWebSocketServer(int port, DataOwner dataOwner, ChannelOutput<Advertisement> rxAdOut, FCClient<Void, Data> ldataCall, FCClient<String, Summary> summaryCall, FCClient<Void, List<Advertisement>> rosterCall) {
            super(port);
            this.rxAdOut = rxAdOut;
            this.dataOwner = dataOwner;
            this.ldataCall = ldataCall;
            this.summaryCall = summaryCall;
            this.rosterCall = rosterCall;
        }

        @Override
        protected WebSocket openWebSocket(IHTTPSession handshake) {
            System.out.println("openWebSocket");
            return new DebugWebSocket(this, handshake);
        }

        private static class DebugWebSocket extends WebSocket {

            private final DebugWebSocketServer server;

            public DebugWebSocket(DebugWebSocketServer server, IHTTPSession handshakeRequest) {
                super(handshakeRequest);
                this.server = server;
            }

            @Override
            protected void onOpen() {
            }

            @Override
            protected void onClose(WebSocketFrame.CloseCode code, String reason, boolean initiatedByRemote) {
                if (server.debug0) {
                    System.out.println("C [" + (initiatedByRemote ? "Remote" : "Self") + "] " + (code != null ? code : "UnknownCloseCode[" + code + "]")
                            + (reason != null && !reason.isEmpty() ? ": " + reason : ""));
                }
            }

            @Override
            protected void onMessage(WebSocketFrame message) {
                try {
                    message.setUnmasked();
                    sendFrame(message);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            protected void onPong(WebSocketFrame pong) {
                if (server.debug0) {
                    System.out.println("P " + pong);
                }
            }

            @Override
            protected void onException(IOException exception) {
                DebugWebSocketServer.LOG.log(Level.SEVERE, "exception occured", exception);
            }

            @Override
            protected void debugFrameReceived(WebSocketFrame frame) {
                if (server.debug0) {
                    System.out.println("R " + frame);
                }
            }

            @Override
            protected void debugFrameSent(WebSocketFrame frame) {
                if (server.debug0) {
                    System.out.println("S " + frame);
                }
            }
        }

        private void listItem(StringBuilder sb, Map.Entry<String, ? extends Object> entry) {
            sb.append("<li><code><b>").append(entry.getKey()).append("</b> = ").append(entry.getValue()).append("</code></li>");
        }

        @Override
        public Response serveHttp(IHTTPSession session) {
            Map<String, List<String>> decodedQueryParameters = decodeParameters(session.getQueryParameterString());

            switch (session.getUri()) {
                case "/post/advertisement": {
                    //MAYBE Check mime type?  It's not really necessary....
                    InputStream is = session.getInputStream();
                    try {
                        Advertisement ad = (Advertisement) dataOwner.deserialize(MeUtils.inputStreamToBytes(is)); //TODO InputStream?
                        rxAdOut.write(ad);
                    } catch (IOException ex) {
                        Logger.getLogger(TcpPutComm.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    return null;
                }

                case "/get/poke": {
                    return newFixedLengthResponse(System.currentTimeMillis()+"");
                }
                case "/get/data": {
                    Data data = ldataCall.call(null);
                    return newChunkedResponse(Status.OK, data.getMime(), data.serialize());
                }
                case "/get/roster": {
                    List<Advertisement> roster = rosterCall.call(null);
                    return newChunkedResponse(Status.OK, "lancopy/roster", new ByteArrayInputStream(dataOwner.serialize(roster))); //TODO InputStream
                }
                default:
                    //throw new AssertionError();
            }


            //TODO Remove
            StringBuilder sb = new StringBuilder();
            sb.append("<html>");
            sb.append("<head><title>Debug Server</title></head>");
            sb.append("<body>");
            sb.append("<h1>Debug Server</h1>");

            sb.append("<p><blockquote><b>URI</b> = ").append(String.valueOf(session.getUri())).append("<br />");

            sb.append("<b>Method</b> = ").append(String.valueOf(session.getMethod())).append("</blockquote></p>");

            sb.append("<h3>Headers</h3><p><blockquote>").append(toString(session.getHeaders())).append("</blockquote></p>");

            sb.append("<h3>Parms</h3><p><blockquote>").append(toString(session.getParms())).append("</blockquote></p>");

            sb.append("<h3>Parms (multi values?)</h3><p><blockquote>").append(toString(decodedQueryParameters)).append("</blockquote></p>");

            try {
                Map<String, String> files = new HashMap<String, String>();
                session.parseBody(files);
                sb.append("<h3>Files</h3><p><blockquote>").append(toString(files)).append("</blockquote></p>");
            } catch (Exception e) {
                e.printStackTrace();
            }

            sb.append("</body>");
            sb.append("</html>");
            //return newFixedLengthResponse(sb.toString());
            return null;
        }

        private String toString(Map<String, ? extends Object> map) {
            if (map.size() == 0) {
                return "";
            }
            return unsortedList(map);
        }

        private String unsortedList(Map<String, ? extends Object> map) {
            StringBuilder sb = new StringBuilder();
            sb.append("<ul>");
            for (Map.Entry<String, ? extends Object> entry : map.entrySet()) {
                listItem(sb, entry);
            }
            sb.append("</ul>");
            return sb.toString();
        }
    }

    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    /*
    
    @WebSocket
    public static class WsServer {

        private static final Queue<Session> sessions = new ConcurrentLinkedQueue<>();

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
                        bb.rewind();
                        re.sendBytes(bb);
                    }
                } catch (Throwable ex) {
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


*/

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
        DebugWebSocketServer server = null;
        try {
            //TODO Can you put the ws on a specific URI?
            server = new DebugWebSocketServer(0, dataOwner, rxAdOut, ldataCall, summaryCall, rosterCall);
            server.start((int) dataOwner.options.getOrDefault("TcpPutComm.WS_TIMEOUT", 30000));
            
            //Spark.awaitInitialization();
            int port = server.getListeningPort();
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
                        System.err.println("//TODO Don't forget to do the broadcasting!");
                        //server.broadcast(dataOwner.serialize(roster));
                        break;
                    case 1: // txLSummaryIn
                        Summary lSummary = txLSummaryIn.read();
                        System.err.println("//TODO Don't forget to do the broadcasting!");
                        //server.broadcast(dataOwner.serialize(lSummary));
                        break;
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            System.out.println("TcpPutComm shutting down");
            dataOwner.errOnce("TcpPutComm //TODO Handle poison");
            dataOwner.errOnce("TcpPutComm //TODO Remove comms");
            try {
                server.stop();
            } catch (Throwable t) {
            }
            //Spark.stop();
            //Spark.awaitStop();
            //ws.shutdown();
        }
    }
}
