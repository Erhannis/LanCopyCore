/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.security;

import com.erhannis.lancopy.DataOwner;
import com.erhannis.mathnstuff.MeUtils;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.BinaryKeysetReader;
import com.google.crypto.tink.BinaryKeysetWriter;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.StreamingAead;
import com.google.crypto.tink.aead.AesGcmKeyManager;
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingKeyManager;
import com.google.crypto.tink.streamingaead.StreamingAeadKeyTemplates;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.security.GeneralSecurityException;

/**
 * //TODO Permit mixing of networks, encrypted with plaintext?
 *
 * @author erhannis
 */
public class Processing {

    /**
     * Do processing for outgoing message.<br/>
     * Assuming encryption enabled: prepend target public key, sign with own private key, encrypt with
     * target public key.
     *
     * @param dataOwner
     * @param message
     * @param targetPublicKey
     * @return
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public static byte[] outgoing(DataOwner dataOwner, byte[] message, byte[] targetPublicKey) throws GeneralSecurityException, IOException {
        boolean encryption = (Boolean) dataOwner.options.getOrDefault("Security.ENCRYPTION", true);
        if (encryption) {
            KeysetHandle targetKey = CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(targetPublicKey));
            //TODO Cache these somewhere?  Index them by ID?
            AsymmetricEncryption.PublicContext targetPublicContext = new AsymmetricEncryption.PublicContext(targetKey);
            message = Bytes.concat(
                    Ints.toByteArray(targetPublicKey.length),
                    targetPublicKey,
                    Ints.toByteArray(message.length),
                    message
                );
            byte[] sig = dataOwner.privateContext.privateKeySign.sign(message);
            message = Bytes.concat(
                    message,
                    Ints.toByteArray(sig.length),
                    sig
                );
            message = AsymmetricEncryption.encryptC(targetPublicContext, message);
            return message;
        } else {
            return message;
        }
    }

    /**
     * Do processing for incoming message.<br/>
     * Assuming encryption enabled: decrypt with own private key, check addressee matches own public key, verify with origin
     * public key.
     *
     * @param dataOwner
     * @param message
     * @param originPublicKey
     * @return
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public static byte[] incoming(DataOwner dataOwner, byte[] message, AsymmetricEncryption.PublicContext originPublicContext) throws GeneralSecurityException, IOException {
        boolean encryption = (Boolean) dataOwner.options.getOrDefault("Security.ENCRYPTION", true);
        if (encryption) {
            //KeysetHandle originKey = CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(originPublicKey));
            //TODO Cache these somewhere?  Index them by ID?
            //AsymmetricEncryption.PublicContext originPublicContext = new AsymmetricEncryption.PublicContext(originKey);
            message = AsymmetricEncryption.decryptC(dataOwner.privateContext, message);
            ByteArrayInputStream bais = new ByteArrayInputStream(message);
            int addresseeLength = Ints.fromByteArray(MeUtils.readNBytes(bais, 4));
            byte[] addressee = MeUtils.readNBytes(bais, addresseeLength);
            System.err.println("//TODO SECURITY Check addressee");
            int messageLength = Ints.fromByteArray(MeUtils.readNBytes(bais, 4));
            message = MeUtils.readNBytes(bais, messageLength);
            int sigLength = Ints.fromByteArray(MeUtils.readNBytes(bais, 4));
            byte[] sig = MeUtils.readNBytes(bais, sigLength);
            if (bais.available() > 0) {
                throw new GeneralSecurityException("Unexpected remaining bytes in message!");
            }
            originPublicContext.publicKeyVerify.verify(sig, message);
            return message;
        } else {
            return message;
        }
    }

    public static InputStream outgoing(DataOwner dataOwner, InputStream message, AsymmetricEncryption.PublicContext targetPublicContext) throws GeneralSecurityException, IOException {
        //TODO SECURITY Bigger key size?
        KeysetHandle instanceKey = KeysetHandle.generateNew(AesGcmHkdfStreamingKeyManager.aes128GcmHkdf1MBTemplate());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CleartextKeysetHandle.write(instanceKey, BinaryKeysetWriter.withOutputStream(baos));
        targetPublicContext.hybridEncrypt.encrypt(baos.toByteArray(), null);
        StreamingAead sa = instanceKey.getPrimitive(StreamingAead.class);
        
        return null;
    }

    public static InputStream incoming(DataOwner dataOwner, InputStream message, AsymmetricEncryption.PublicContext originPublicContext) throws GeneralSecurityException, IOException {
        return null;
    }
}
