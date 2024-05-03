/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

import com.erhannis.lancopy.refactor.Advertisement;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.lancopy.refactor.Summary;
import com.erhannis.lancopy.refactor.tcp.TcpComm;
import com.erhannis.lancopy.refactor2.messages.DataChunkMessage;
import com.erhannis.lancopy.refactor2.messages.DataRequestMessage;
import com.erhannis.lancopy.refactor2.messages.DataStartMessage;
import com.erhannis.lancopy.refactor2.messages.IdentificationMessage;
import com.erhannis.lancopy.refactor2.messages.Message;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelConnectionDataMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelConnectionErrorMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelConnectionMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelConnectionRequestMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelConnectionSuccessMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelErrorMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelRequestMessage;
import com.erhannis.lancopy.refactor2.messages.tunnel.TunnelSuccessMessage;
import com.erhannis.lancopy.refactor2.tls.ContextFactory;
import com.erhannis.mathnstuff.MeUtils;
import com.erhannis.mathnstuff.components.OptionsFrame;
import com.erhannis.mathnstuff.utils.Options;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import de.javakaffee.kryoserializers.UUIDSerializer;
import de.javakaffee.kryoserializers.UnmodifiableCollectionsSerializer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.InvalidNameException;
import jcsp.lang.ChannelOutputInt;
import org.apache.commons.codec.digest.DigestUtils;
import org.spongycastle.operator.OperatorCreationException;

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
        private final HashMap<Kryo, HashSet<Integer>> registeredIds = new HashMap<>();
        
        private void checkCollision(Kryo kryo, Registration r) {
            if (!registeredIds.computeIfAbsent(kryo, (k) -> new HashSet<Integer>()).add(r.getId())) {
                throw new RuntimeException("ERR Kryo ID collision: "+r.getId());
            }
        }
        
        private void kryoRegister(Kryo kryo, Class clazz) {
            kryoRegister(kryo, clazz, false);
        }

        private void kryoRegister(Kryo kryo, Class clazz, Serializer s) {
            Registration r;
            String name = clazz.getTypeName();
            r = kryo.register(clazz, s);
            System.out.println("Kryo registered " + name + " as " + r.getId());
            checkCollision(kryo, r);
        }

        private void kryoRegister(Kryo kryo, Class clazz, boolean hashId) {
            Registration r;
            String name = clazz.getTypeName();
            if (hashId) {
                byte[] hash = DigestUtils.sha256(name);
                int id = Math.abs(MeUtils.colorBytesToInt(hash[0], hash[1], hash[2], hash[3])); //CHECK I'm not SURE this isn't bugged
                r = kryo.register(clazz, id);
            } else {
                r = kryo.register(clazz);
            }
            System.out.println("Kryo registered " + name + " as " + r.getId());
            checkCollision(kryo, r);
        }
    
        protected Kryo initialValue() {
            Kryo kryo = new Kryo();

            //PERIODIC Update this list whenever you add new message types.  Add to end of list to preserve backwards compatibility.
            kryo.setReferences(true);
            kryoRegister(kryo, Advertisement.class);
            kryoRegister(kryo, Comm.class);
            kryoRegister(kryo, TcpComm.class); //TODO Move these elsewhere?
            kryoRegister(kryo, Summary.class);
            kryoRegister(kryo, Message.class);
            kryoRegister(kryo, IdentificationMessage.class);
            kryoRegister(kryo, DataRequestMessage.class);
            kryoRegister(kryo, DataStartMessage.class);
            kryoRegister(kryo, DataChunkMessage.class);
            kryoRegister(kryo, ArrayList.class);
            kryoRegister(kryo, UUID.class, new UUIDSerializer());
            kryoRegister(kryo, byte[].class);
            UnmodifiableCollectionsSerializer.registerSerializers(kryo); // Doesn't get logged like the others
            //kryo.register(java.util.Collections.UnmodifiableRandomAccessList.class);
            
            kryoRegister(kryo, TunnelRequestMessage.TunnelDirection.class, true);
            kryoRegister(kryo, TunnelRequestMessage.TunnelProtocol.class, true);
            kryoRegister(kryo, TunnelRequestMessage.class, true);
            kryoRegister(kryo, TunnelSuccessMessage.class, true);
            kryoRegister(kryo, TunnelErrorMessage.class, true);
            kryoRegister(kryo, TunnelConnectionMessage.class, true);
            kryoRegister(kryo, TunnelConnectionRequestMessage.class, true);
            kryoRegister(kryo, TunnelConnectionSuccessMessage.class, true);
            kryoRegister(kryo, TunnelConnectionErrorMessage.class, true);
            kryoRegister(kryo, TunnelConnectionDataMessage.class, true);

            return kryo;
        }
    };
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
