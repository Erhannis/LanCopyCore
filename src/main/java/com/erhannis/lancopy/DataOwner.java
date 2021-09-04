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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jmdns.ServiceInfo;
import okhttp3.OkHttpClient;

/**
 *
 * @author erhannis
 */
public class DataOwner {
    /**
     * This is the protocol UUID.  Wherever the idea of LanCopy needs to be (or can be) identified by a UUID, this is the one to use.
     */
    public static final UUID LANCOPY_SERVICE = UUID.fromString("66e8b86b-5868-4b8e-8f6f-d2845616d72c");
    
    public final UUID ID = UUID.randomUUID();

    public final Options options;

    static private final ThreadLocal<Kryo> kryos = new ThreadLocal<Kryo>() {
        protected Kryo initialValue() {
            Kryo kryo = new Kryo();

            kryo.setReferences(true);
            kryo.register(Advertisement.class);
            kryo.register(Comm.class);
            kryo.register(TcpComm.class); //TODO Move these elsewhere?
            kryo.register(Summary.class);
            kryo.register(ArrayList.class);
            kryo.register(UUID.class, new UUIDSerializer());
            UnmodifiableCollectionsSerializer.registerSerializers(kryo);
            //kryo.register(java.util.Collections.UnmodifiableRandomAccessList.class);

            return kryo;
        }
    };
    public final OkHttpClient ohClient = new OkHttpClient();

    public DataOwner() {
        this.options = Options.demandOptions(OptionsFrame.DEFAULT_OPTIONS_FILENAME);
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
