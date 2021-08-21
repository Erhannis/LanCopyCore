/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

import com.erhannis.lancopy.data.Data;
import com.erhannis.lancopy.refactor.Advertisement;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.mathnstuff.components.OptionsFrame;
import com.erhannis.mathnstuff.utils.Observable;
import com.erhannis.mathnstuff.utils.ObservableMap;
import com.erhannis.mathnstuff.utils.Options;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import de.javakaffee.kryoserializers.UnmodifiableCollectionsSerializer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jmdns.ServiceInfo;

/**
 *
 * @author erhannis
 */
public class DataOwner {
    public int SUMMARY_LENGTH = 100;

    public final Observable<String> localSummary = new Observable<>();
    public final Observable<Data> localData = new Observable<>();
    public final ObservableMap<String, NodeInfo> remoteNodes = new ObservableMap<>();

    public final String ID = "LanCopy-" + UUID.randomUUID();
    public int PORT;

    public boolean cachedSettingLoopClipboard = false;
    public boolean cachedSettingSaveSettingsOnExit = true;
    public String cachedSettingDefaultSavePath = "";
    public String cachedSettingDefaultOpenPath = "";

    public final Options options;
    public final Kryo kryo = new Kryo();

    public DataOwner() {
        localData.subscribe((data) -> {
            String summary = data.toString();
            summary = summary.substring(0, Math.min(summary.length(), SUMMARY_LENGTH));
            localSummary.set(summary);
        });
        this.options = Options.demandOptions(OptionsFrame.DEFAULT_OPTIONS_FILENAME);

        kryo.register(Advertisement.class);
        kryo.register(Comm.class);
        kryo.register(ArrayList.class);
        UnmodifiableCollectionsSerializer.registerSerializers(kryo);
        //kryo.register(java.util.Collections.UnmodifiableRandomAccessList.class);
    }

    public void observedNode(NodeInfo info) {
        System.out.println("DO observedNode " + info);
        remoteNodes.put(info.id, info);
    }

    public void saveSettings() {
        try {
            Properties props = new Properties();
            File f = new File("settings.xml");
            if (f.exists()) {
                props.loadFromXML(new FileInputStream(f));
            }
            props.setProperty("SUMMARY_LENGTH", "" + SUMMARY_LENGTH);
            props.setProperty("LOOP_CLIPBOARD", "" + cachedSettingLoopClipboard);
            props.setProperty("SAVE_SETTINGS_ON_EXIT", "" + cachedSettingSaveSettingsOnExit);
            props.setProperty("DEFAULT_SAVE_PATH", "" + cachedSettingDefaultSavePath);
            props.setProperty("DEFAULT_OPEN_PATH", "" + cachedSettingDefaultOpenPath);
            props.storeToXML(new FileOutputStream("settings.xml"), "");
        } catch (FileNotFoundException ex) {
            Logger.getLogger(DataOwner.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(DataOwner.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void loadSettings() {
        try {
            Properties props = new Properties();
            File f = new File("settings.xml");
            props.loadFromXML(new FileInputStream(f));
            SUMMARY_LENGTH = Integer.parseInt(props.getProperty("SUMMARY_LENGTH", "" + SUMMARY_LENGTH));
            cachedSettingLoopClipboard = Boolean.parseBoolean(props.getProperty("LOOP_CLIPBOARD", "" + cachedSettingLoopClipboard));
            cachedSettingSaveSettingsOnExit = Boolean.parseBoolean(props.getProperty("SAVE_SETTINGS_ON_EXIT", "" + cachedSettingSaveSettingsOnExit));
            cachedSettingDefaultSavePath = props.getProperty("DEFAULT_SAVE_PATH", "" + cachedSettingDefaultSavePath);
            cachedSettingDefaultOpenPath = props.getProperty("DEFAULT_OPEN_PATH", "" + cachedSettingDefaultOpenPath);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(DataOwner.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(DataOwner.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NumberFormatException ex) {
            Logger.getLogger(DataOwner.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public byte[] serialize(Object o) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Output output = new Output(baos);
        kryo.writeClassAndObject(output, o);
        output.close();
        return baos.toByteArray();
    }

    public Object deserialize(byte[] b) {
        ByteArrayInputStream bais = new ByteArrayInputStream(b);
        Input input = new Input(bais);
        Object o = kryo.readClassAndObject(input);
        input.close();
        return o;
    }
}
