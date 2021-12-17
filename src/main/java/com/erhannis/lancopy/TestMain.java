/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy;

import com.erhannis.lancopy.security.AsymmetricEncryption;
import com.google.crypto.tink.BinaryKeysetReader;
import com.google.crypto.tink.BinaryKeysetWriter;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.subtle.Bytes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 *
 * @author erhannis
 */
public class TestMain {
    public static void main(String[] args) throws GeneralSecurityException, IOException {
        if (1==1) {
            KeysetHandle kh1 = AsymmetricEncryption.generateContext().publicCKey;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CleartextKeysetHandle.write(kh1, BinaryKeysetWriter.withOutputStream(baos));
            byte[] bs1 = baos.toByteArray();
            KeysetHandle kh2 = CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(bs1));
            baos = new ByteArrayOutputStream();
            CleartextKeysetHandle.write(kh2, BinaryKeysetWriter.withOutputStream(baos));
            byte[] bs2 = baos.toByteArray();
            KeysetHandle kh3 = CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(bs2));
            baos = new ByteArrayOutputStream();
            CleartextKeysetHandle.write(kh3, BinaryKeysetWriter.withOutputStream(baos));
            byte[] bs3 = baos.toByteArray();
            System.out.println(Arrays.toString(bs1) + "\n" + Arrays.toString(bs2) + "\n" + Arrays.toString(bs3));
            if (1==1) return;
        }
        if (1==1) {
            AsymmetricEncryption.PrivateContext ctx = AsymmetricEncryption.generateContext();
            KeysetHandle master2 = AsymmetricEncryption.generateMaster();
            byte[] m2bytes = AsymmetricEncryption.saveMaster(master2);
            master2 = AsymmetricEncryption.loadMaster(m2bytes);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CleartextKeysetHandle.write(ctx.publicCKey, JsonKeysetWriter.withOutputStream(baos));
            byte[] publicTx = baos.toByteArray();
            System.out.println(new String(publicTx));

            byte[] publicBytes = AsymmetricEncryption.savePublic(master2, ctx);
            AsymmetricEncryption.PublicContext publicCtx = AsymmetricEncryption.loadPublic(master2, publicBytes);
            byte[] plaintext = "I am the very model of a modern major something something, I something something vegetable mineral animal historical than musical, about the kings of england in the order categorical!  ...And now you know a wondrous thing, of 42 english what is the meaning of life?  how many roads must a man walk down flaming 40 foot tall letters in the sky with diamonds are a girl's best friend for their multitude of industrial uses.".getBytes(AsymmetricEncryption.UTF8);
            byte[] ciphertext = AsymmetricEncryption.encryptC(ctx, plaintext);
            KeysetHandle master1 = AsymmetricEncryption.generateMaster();
            byte[] m1bytes = AsymmetricEncryption.saveMaster(master1);
            master1 = AsymmetricEncryption.loadMaster(m1bytes);
            byte[] privateBytes = AsymmetricEncryption.savePrivate(master1, ctx);
            AsymmetricEncryption.PrivateContext privateCtx = AsymmetricEncryption.loadPrivate(master1, privateBytes);
            byte[] decrypted = AsymmetricEncryption.decryptC(privateCtx, ciphertext);
            if (1==1) return;
        }
     }
}
