/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.security;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.BinaryKeysetReader;
import com.google.crypto.tink.BinaryKeysetWriter;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.HybridDecrypt;
import com.google.crypto.tink.HybridEncrypt;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.KeyTemplate;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.PublicKeySign;
import com.google.crypto.tink.PublicKeyVerify;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.AeadKeyTemplates;
import com.google.crypto.tink.aead.AesGcmKeyManager;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.crypto.tink.hybrid.EciesAeadHkdfPrivateKeyManager;
import com.google.crypto.tink.hybrid.HybridKeyTemplates;
import com.google.crypto.tink.proto.EcPointFormat;
import com.google.crypto.tink.proto.EllipticCurveType;
import com.google.crypto.tink.proto.HashType;
import com.google.crypto.tink.proto.OutputPrefixType;
import com.google.crypto.tink.signature.SignatureConfig;
import com.google.crypto.tink.streamingaead.StreamingAeadConfig;
import com.google.crypto.tink.subtle.AesGcmJce;
import com.google.crypto.tink.subtle.Bytes;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.Security;

/**
 *
 * @author erhannis
 */
public class AsymmetricEncryption {
  public static class PublicContext {
    public KeysetHandle publicCKey;
    public HybridEncrypt hybridEncrypt;
    public PublicKeyVerify publicKeyVerify;

    public PublicContext() {
    }
    
    public PublicContext(KeysetHandle publicKey) throws GeneralSecurityException {
        this.publicCKey = publicKey;
        this.hybridEncrypt = publicKey.getPrimitive(HybridEncrypt.class);
        this.publicKeyVerify = publicKey.getPrimitive(PublicKeyVerify.class);
    }
    
    @Override
    public String toString() {
      return "Pu"+publicCKey; //TODO ???
    }
  }

  public static class PrivateContext extends PublicContext {
    public KeysetHandle privateCKey;
    public HybridDecrypt hybridDecrypt;
    public PublicKeySign privateKeySign;

    public PrivateContext() {
    }
    
    public PrivateContext(KeysetHandle privateKey) throws GeneralSecurityException {
        super(privateKey.getPublicKeysetHandle());
        this.privateCKey = privateKey;
        this.hybridDecrypt = privateCKey.getPrimitive(HybridDecrypt.class);
        this.privateKeySign = privateCKey.getPrimitive(PublicKeySign.class);
    }
    
    @Override
    public String toString() {
      return "Pr"+privateCKey; //TODO ???
    }
  }

  public static final Charset UTF8 = Charset.forName("UTF-8");

  private static final KeysetHandle MASTER;

  static {
    try {
      AeadConfig.register();
      StreamingAeadConfig.register();
      HybridConfig.register();
      SignatureConfig.register();
      MASTER = getMaster();
    } catch (IOException | GeneralSecurityException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static KeysetHandle generateMaster() throws GeneralSecurityException, IOException {
    KeysetHandle master = KeysetHandle.generateNew(AesGcmKeyManager.aes256GcmTemplate());
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    CleartextKeysetHandle.write(master, BinaryKeysetWriter.withOutputStream(baos));
    //System.out.println("master: " + new String(baos.toByteArray(), UTF8).replaceAll("\n", "").replaceAll(" ", ""));
    return master;
  }

  //TODO It'd probably be a little better to save a generated master key per installation...but not by much, since obtaining the private keystore means the computer's compromised, anyway
  private static KeysetHandle getMaster() throws GeneralSecurityException, IOException {
    KeysetHandle master = CleartextKeysetHandle.read(JsonKeysetReader.withString("{\"primaryKeyId\":350367924,\"key\":[{\"keyData\":{\"typeUrl\":\"type.googleapis.com/google.crypto.tink.AesGcmKey\",\"keyMaterialType\":\"SYMMETRIC\",\"value\":\"GiDKv904epqf87+9LnKwW/Ll0iDkoo6O4jwFRp/AGJHcYw==\"},\"outputPrefixType\":\"TINK\",\"keyId\":350367924,\"status\":\"ENABLED\"}]}"));
    return master;
  }

  public static byte[] saveMaster(KeysetHandle master) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    CleartextKeysetHandle.write(master, BinaryKeysetWriter.withOutputStream(baos));
    return baos.toByteArray();
  }

  public static KeysetHandle loadMaster(byte[] bytes) throws GeneralSecurityException, IOException {
    return CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(bytes));
  }

  /**
   * Writes LSB bytes.length, then writes bytes
   *
   * @param baos
   * @param bytes
   * @throws IOException
   */
  private static void writeBytes(ByteArrayOutputStream baos, byte[] bytes) throws IOException {
    baos.write(Bytes.intToByteArray(4, bytes.length));
    baos.write(bytes);
  }

  /**
   * Reads LSB int L, then reads and returns byte[L]
   *
   * @param bais
   * @return
   */
  private static byte[] readBytes(ByteArrayInputStream bais) throws IOException {
    byte[] length = new byte[4];
    bais.read(length);
    byte[] data = new byte[Bytes.byteArrayToInt(length)];
    bais.read(data);
    return data;
  }

  public static PrivateContext loadPrivate(byte[] bytes) throws IOException, GeneralSecurityException {
    return loadPrivate(MASTER, bytes);
  }

  public static PrivateContext loadPrivate(KeysetHandle keystoreKey, byte[] bytes) throws IOException, GeneralSecurityException {
    PrivateContext ctx = new PrivateContext();

    Aead master = keystoreKey.getPrimitive(Aead.class);

    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    ctx.privateCKey = KeysetHandle.read(BinaryKeysetReader.withBytes(readBytes(bais)), master);
    ctx.publicCKey = KeysetHandle.read(BinaryKeysetReader.withBytes(readBytes(bais)), master);

    ctx.hybridDecrypt = ctx.privateCKey.getPrimitive(HybridDecrypt.class);
    ctx.hybridEncrypt = ctx.publicCKey.getPrimitive(HybridEncrypt.class);
    
    ctx.privateKeySign = ctx.privateCKey.getPrimitive(PublicKeySign.class);
    ctx.publicKeyVerify = ctx.publicCKey.getPrimitive(PublicKeyVerify.class);

    return ctx;
  }

  public static byte[] savePrivate(PrivateContext ctx) throws GeneralSecurityException, IOException {
    return savePrivate(MASTER, ctx);
  }

  public static byte[] savePrivate(KeysetHandle keystoreKey, PrivateContext ctx) throws GeneralSecurityException, IOException {
    Aead master = keystoreKey.getPrimitive(Aead.class);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ByteArrayOutputStream baos0 = new ByteArrayOutputStream();
    ctx.privateCKey.write(BinaryKeysetWriter.withOutputStream(baos0), master); // This is obnoxiously inside out
    writeBytes(baos, baos0.toByteArray());
    baos0.reset();
    ctx.publicCKey.write(BinaryKeysetWriter.withOutputStream(baos0), master);
    writeBytes(baos, baos0.toByteArray());

    return baos.toByteArray();
  }

  public static PublicContext loadPublic(byte[] bytes) throws IOException, GeneralSecurityException {
    return loadPublic(MASTER, bytes);
  }

  public static PublicContext loadPublic(KeysetHandle keystoreKey, byte[] bytes) throws IOException, GeneralSecurityException {
    PublicContext ctx = new PublicContext();

    Aead master = keystoreKey.getPrimitive(Aead.class);

    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    ctx.publicCKey = KeysetHandle.read(BinaryKeysetReader.withBytes(readBytes(bais)), master);

    ctx.hybridEncrypt = ctx.publicCKey.getPrimitive(HybridEncrypt.class);

    ctx.publicKeyVerify = ctx.publicCKey.getPrimitive(PublicKeyVerify.class);
    
    return ctx;
  }

  public static byte[] savePublic(PublicContext ctx) throws GeneralSecurityException, IOException {
    return savePublic(MASTER, ctx);
  }

  public static byte[] savePublic(KeysetHandle keystoreKey, PublicContext ctx) throws GeneralSecurityException, IOException {
    Aead master = keystoreKey.getPrimitive(Aead.class);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ByteArrayOutputStream baos0 = new ByteArrayOutputStream();
    ctx.publicCKey.write(BinaryKeysetWriter.withOutputStream(baos0), master);
    writeBytes(baos, baos0.toByteArray());

    return baos.toByteArray();
  }

  public static PrivateContext generateContext() throws GeneralSecurityException {
    PrivateContext ctx = new PrivateContext();

    // Classical
    //TODO Increase key sizes
   KeysetHandle privateKeysetHandle = KeysetHandle.generateNew(HybridKeyTemplates.createEciesAeadHkdfKeyTemplate(
            EllipticCurveType.NIST_P256,
            HashType.SHA256,
            EcPointFormat.UNCOMPRESSED,
            AeadKeyTemplates.AES256_GCM, //AesGcmKeyManager.aes128GcmTemplate(),
            OutputPrefixType.TINK,
            new byte[0]));
    KeysetHandle publicKeysetHandle = privateKeysetHandle.getPublicKeysetHandle();
    HybridEncrypt hybridEncrypt = publicKeysetHandle.getPrimitive(HybridEncrypt.class);
    HybridDecrypt hybridDecrypt = privateKeysetHandle.getPrimitive(HybridDecrypt.class);
    PublicKeySign privateKeySign = privateKeysetHandle.getPrimitive(PublicKeySign.class);
    PublicKeyVerify publicKeyVerify = publicKeysetHandle.getPrimitive(PublicKeyVerify.class);

    ctx.privateCKey = privateKeysetHandle;
    ctx.publicCKey = publicKeysetHandle;
    ctx.hybridDecrypt = hybridDecrypt;
    ctx.hybridEncrypt = hybridEncrypt;
    ctx.privateKeySign = privateKeySign;
    ctx.publicKeyVerify = publicKeyVerify;

    return ctx;
  }

  public static byte[] encryptC(PublicContext ctx, byte[] plaintext) throws GeneralSecurityException {
    byte[] ciphertext = ctx.hybridEncrypt.encrypt(plaintext, null);
    return ciphertext;
  }

  public static byte[] decryptC(PrivateContext ctx, byte[] ciphertext) throws GeneralSecurityException {
    byte[] decrypted = ctx.hybridDecrypt.decrypt(ciphertext, null);
    return decrypted;
  }
  
  /**
   * Warning: just copies the references from the PrivateContext to a new PublicContext
   * @param privateContext
   * @return 
   */
  public static PublicContext privateToPublic(PrivateContext privateContext) {
      PublicContext publicContext = new PublicContext();
      publicContext.hybridEncrypt = privateContext.hybridEncrypt;
      publicContext.publicCKey = privateContext.publicCKey;
      publicContext.publicKeyVerify = privateContext.publicKeyVerify;
      return publicContext;
  }
}
