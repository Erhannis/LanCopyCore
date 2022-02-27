/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

import com.erhannis.lancopy.data.Data;
import com.erhannis.lancopy.refactor.Advertisement;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.lancopy.refactor.Summary;
import com.erhannis.lancopy.refactor.tcp.TcpComm;
import com.erhannis.lancopy.refactor2.messages.DataChunkMessage;
import com.erhannis.lancopy.refactor2.messages.DataRequestMessage;
import com.erhannis.lancopy.refactor2.messages.DataStartMessage;
import com.erhannis.lancopy.refactor2.messages.IdentificationMessage;
import com.erhannis.lancopy.refactor2.messages.Message;
import com.erhannis.lancopy.refactor2.tls.ContextFactory;
import com.erhannis.mathnstuff.components.OptionsFrame;
import com.erhannis.mathnstuff.utils.Observable;
import com.erhannis.mathnstuff.utils.ObservableMap;
import com.erhannis.mathnstuff.utils.Options;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import de.javakaffee.kryoserializers.UUIDSerializer;
import de.javakaffee.kryoserializers.UnmodifiableCollectionsSerializer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jmdns.ServiceInfo;
import javax.naming.InvalidNameException;
import jcsp.lang.ChannelOutputInt;
import okhttp3.OkHttpClient;
import org.spongycastle.operator.OperatorCreationException;
import sun.security.x509.X500Name;

/**
 *
 * @author erhannis
 */
public class DataOwner {
    /**
     * This is the protocol UUID.  Wherever the idea of LanCopy needs to be (or can be) identified by a UUID, this is the one to use.
     */
    public static final UUID LANCOPY_SERVICE = UUID.fromString("66e8b86b-5868-4b8e-8f6f-d2845616d72c");
    
    public final UUID ID;//UUID.randomUUID();

    public final Options options;
    
    public final SecureRandom rand;

    static private final ThreadLocal<Kryo> kryos = new ThreadLocal<Kryo>() {
        protected Kryo initialValue() {
            Kryo kryo = new Kryo();

            kryo.setReferences(true);
            kryo.register(Advertisement.class);
            kryo.register(Comm.class);
            kryo.register(TcpComm.class); //TODO Move these elsewhere?
            kryo.register(Summary.class);
            kryo.register(Message.class);
            kryo.register(IdentificationMessage.class);
            kryo.register(DataRequestMessage.class);
            kryo.register(DataStartMessage.class);
            kryo.register(DataChunkMessage.class);
            kryo.register(ArrayList.class);
            kryo.register(UUID.class, new UUIDSerializer());
            kryo.register(byte[].class);
            UnmodifiableCollectionsSerializer.registerSerializers(kryo);
            //kryo.register(java.util.Collections.UnmodifiableRandomAccessList.class);

            return kryo;
        }
    };
    public final OkHttpClient ohClient;
    public final boolean encrypted;
    public final ContextFactory.Context tlsContext;

    public DataOwner(ChannelOutputInt showLocalFingerprintOut, Function<String, Boolean> trustCallback) {
        this(OptionsFrame.DEFAULT_OPTIONS_FILENAME, showLocalFingerprintOut, trustCallback);
    }

    public DataOwner(String optionsPath, ChannelOutputInt showLocalFingerprintOut, Function<String, Boolean> trustCallback) {
        this(Options.demandOptions(optionsPath), showLocalFingerprintOut, trustCallback);
    }
    
    public DataOwner(Options options, ChannelOutputInt showLocalFingerprintOut, Function<String, Boolean> trustCallback) {
        this.rand = new SecureRandom();
        this.options = options;
        this.ohClient = new OkHttpClient.Builder()
                .connectTimeout((Integer) options.getOrDefault("OkHttp.CONNECT_TIMEOUT", 35000), TimeUnit.MILLISECONDS)
                .writeTimeout((Integer) options.getOrDefault("OkHttp.WRITE_TIMEOUT", 35000), TimeUnit.MILLISECONDS)
                .readTimeout((Integer) options.getOrDefault("OkHttp.READ_TIMEOUT", 35000), TimeUnit.MILLISECONDS)
                .build();
        if ((Boolean)options.getOrDefault("Security.ENCRYPTION", true)) {
            this.encrypted = true;
            String keystorePath = (String) options.getOrDefault("Security.KEYSTORE_PATH", "lancopy.ks");
            String truststorePath = (String) options.getOrDefault("Security.TRUSTSTORE_PATH", "lancopy.ts");
            String protocol = (String) options.getOrDefault("Security.PROTOCOL", "TLSv1.3");
            
            ContextFactory.Context ctx = null;
            try {
                ctx = ContextFactory.authenticatedContext(protocol, keystorePath, truststorePath, trustCallback, showLocalFingerprintOut);
            } catch (GeneralSecurityException ex) {
                Logger.getLogger(DataOwner.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(DataOwner.class.getName()).log(Level.SEVERE, null, ex);
            } catch (OperatorCreationException ex) {
                Logger.getLogger(DataOwner.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InvalidNameException ex) {
                Logger.getLogger(DataOwner.class.getName()).log(Level.SEVERE, null, ex);
            }
            this.tlsContext = ctx;
            this.ID = UUID.fromString(ctx.id);
        } else {
            this.ID = UUID.randomUUID(); //TODO Optionize or something?
            this.encrypted = false;
            this.tlsContext = null;
        }
    }

    public byte[] serialize(Object o) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Output output = new Output(baos);
        kryos.get().writeClassAndObject(output, o);
        output.close();
        return baos.toByteArray();
    }
    
    public Object deserialize(byte[] b) {
        ByteArrayInputStream bais = new ByteArrayInputStream(b);
        Input input = new Input(bais);
        Object o = kryos.get().readClassAndObject(input);
        input.close();
        return o;
    }

    //TODO Not really a great place for this
    private HashSet<String> msgs = new HashSet<>();

    public void errOnce(String msg) {
        if (msgs.add(msg)) {
            System.err.println(msg);
        }
    }
}
