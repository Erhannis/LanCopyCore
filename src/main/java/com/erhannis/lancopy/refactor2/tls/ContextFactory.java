package com.erhannis.lancopy.refactor2.tls;

import com.erhannis.mathnstuff.MeUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import jcsp.lang.ChannelOutputInt;
import org.apache.commons.codec.digest.DigestUtils;
import org.spongycastle.cert.X509CertificateHolder;
import org.spongycastle.cert.X509v3CertificateBuilder;
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter;
import org.spongycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.operator.ContentSigner;
import org.spongycastle.operator.OperatorCreationException;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;
import sun.security.x509.AlgorithmId;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateVersion;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

// Derived from https://github.com/marianobarrios/tls-channel/tree/master/src/test/scala/tlschannel/example
public class ContextFactory {
    public static class Context {
        public SSLContext sslContext;
        public String sha256Fingerprint;
        public String id;
    }
    

    public static synchronized Context authenticatedContext(String protocol, String keystore, String truststore, Function<String, Boolean> trustCallback, ChannelOutputInt showLocalFingerprintOut) throws GeneralSecurityException, IOException, OperatorCreationException, InvalidNameException {
        //TODO Optionize some of these things?  Passwords, 
        
        Context ctx = new Context();
        
        ctx.sslContext = SSLContext.getInstance(protocol);
        
        SecureRandom sr = new SecureRandom();
        
        // Keystore
        KeyStore ks = KeyStore.getInstance("PKCS12");
        File ksFile =  new File(keystore);
        if (!ksFile.exists()) {
            System.out.println("Generating key...");
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(4096);
            KeyPair kp = kpg.generateKeyPair();
            Key pub = kp.getPublic();
            Key pvt = kp.getPrivate();
            
            ks.load(null, "password".toCharArray());
            //X509Certificate cert = generateCertificate("CN=Unknown, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown", kp, 1000, "SHA384withRSA");
            //X509Certificate cert = generateCertificate("CN="+UUID.randomUUID()+", OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown", kp, 1000, "SHA384withRSA");
            X509Certificate cert = generateCertificateObject("CN="+UUID.randomUUID()+", OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown", kp, sr, new Date(System.currentTimeMillis()-(1000L*60*60*24)), new Date(System.currentTimeMillis()+(1000L*60*60*24*365*2)), "SHA384withRSA");
            ks.setKeyEntry("node", pvt, "password".toCharArray(), new Certificate[]{cert});
            FileOutputStream fos = new FileOutputStream(ksFile);
            ks.store(fos, "password".toCharArray());
            fos.flush();
            fos.close();
        }

        // Truststore
        KeyStore ts = KeyStore.getInstance("PKCS12");
        File tsFile =  new File(truststore);
        if (!tsFile.exists()) {
            System.out.println("Generating initial truststore...");
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(4096);
            KeyPair kp = kpg.generateKeyPair();
            
            ts.load(null, "password".toCharArray());
            // Like, I'm tempted to store our own public key, but that'd mean automatically trusting communications which claim to come from OURSELF, which feels weeeeird....
            // And if I just leave the truststore empty, the code that uses it throws a weird exception.
            //X509Certificate cert = generateCertificate("CN=Unknown, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown", kp, 1000, "SHA384withRSA");
            X509Certificate cert = generateCertificateObject("CN=Unknown, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown", kp, sr, new Date(System.currentTimeMillis()-(1000L*60*60*24)), new Date(System.currentTimeMillis()+(1000L*60*60*24*365*2)), "SHA384withRSA");
            // Note that it's setKeyEntry for keystores and setCertificateEntry for truststores.  You can also use: ts.setEntry("dummy", new KeyStore.TrustedCertificateEntry(cert), null);
            ts.setCertificateEntry("dummy", cert);
            FileOutputStream fos = new FileOutputStream(tsFile);
            ts.store(fos, "password".toCharArray());
            fos.flush();
            fos.close();
        }
        
        try (InputStream keystoreFile = new FileInputStream(ksFile) ; InputStream truststoreFile = new FileInputStream(tsFile)) {
            // (Re)load ks/ts
            ks.load(keystoreFile, "password".toCharArray());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, "password".toCharArray());
            ts.load(truststoreFile, "password".toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);
            
            Certificate cert = ks.getCertificate("node");
            if (cert instanceof X509Certificate) {
                String dn = ((X509Certificate)cert).getSubjectX500Principal().getName();
                LdapName ldapDN = new LdapName(dn);
                for (Rdn rdn: ldapDN.getRdns()) {
                    System.out.println(rdn.getType() + " -> " + rdn.getValue());
                    if ("CN".equals(rdn.getType())) {
                        ctx.id = ""+rdn.getValue();
                        break;
                    }
                }
                if (ctx.id == null) {
                    System.err.println("CN not found for 'node', failed to recover ID");
                }
            } else {
                System.err.println("Failed to find cert for 'node', failed to recover ID");
                ctx.id = null;
            }


            ctx.sha256Fingerprint = MeUtils.bytesToHex(DigestUtils.sha256(ks.getCertificate("node").getEncoded()));
            
            HashMap<String, Boolean> trustCache = new HashMap<>();
            
            X509TrustManager tm = new FallbackX509TrustManager(tmf) {
                @Override
                public void failedClientTrusted(CertificateException e, X509Certificate[] chain, String authType) throws CertificateException {
                    System.out.println("failedClientTrusted " + e + "\n" + authType + " " + Arrays.toString(chain));
                    handleCertFailure(e, chain, authType);
                }

                @Override
                public void failedServerTrusted(CertificateException e, X509Certificate[] chain, String authType) throws CertificateException {
                    System.out.println("failedServerTrusted " + e + "\n" + authType + " " + Arrays.toString(chain));
                    if (handleCertFailure(e, chain, authType)) {
                        System.out.println("handleCertFailure -> true");
                        showLocalFingerprintOut.write(1);
                    } else {
                        System.out.println("handleCertFailure -> false");
                    }
                }                

                /**
                 * 
                 * @param e
                 * @param chain
                 * @param authType
                 * @return whether the user was prompted
                 * @throws CertificateException if the user rejected the cert
                 */
                private boolean handleCertFailure(CertificateException e, X509Certificate[] chain, String authType) throws CertificateException {
                    synchronized (trustCache) {
                        Throwable cause = e.getCause();
                        String fingerprint = MeUtils.bytesToHex(DigestUtils.sha256(chain[0].getEncoded()));
                        if (trustCache.containsKey(fingerprint)) {
                            if (trustCache.get(fingerprint)) {
                                return false;
                            } else {
                                throw e;
                            }
                        }
                        boolean askAccept = false;
                        boolean processed = false;
                        StringBuilder sb = new StringBuilder();
                        processing: {
                            //TODO Are there other reasons we care about?
                            try {
                                if (cause instanceof sun.security.provider.certpath.SunCertPathBuilderException) {
                                    sb.append("This certificate id has not been recorded.\n");
                                    sb.append(fingerprint+"\n");
                                    sb.append("Trust it and record it?");
                                    askAccept = true;
                                    processed = true;
                                    break processing;
                                }
                            } catch (NoClassDefFoundError t) {
                                // Probably on Android or something
                            }
                            try {
                                if (CertPathValidatorException.BasicReason.INVALID_SIGNATURE == (((java.security.cert.CertPathValidatorException)cause).getReason())) {
                                    sb.append("THIS CERTIFICATE IS DIFFERENT FROM THE ONE ON RECORD.\n");
                                    sb.append(fingerprint+"\n");
                                    sb.append("Trust it and overwrite the old one?");
                                    askAccept = true;
                                    processed = true;
                                    break processing;
                                } else {
                                    // Probably on a particular VERSION of Android...
                                }
                            } catch (NoClassDefFoundError t) {
                                // There's probably a hundred variations of Android, and who wants to bet they all do something weird and different here
                            }
                            {
                                if (cause instanceof java.security.cert.CertPathValidatorException) {
                                    sb.append("SECURITY WARNING - most likely unfamiliar ID and certificate.\n");
                                    sb.append("Error message: " + cause.getMessage() + "\n");
                                    sb.append("Fingerprint:\n");
                                    sb.append(fingerprint+"\n");
                                    sb.append("Trust it and record it?");
                                    askAccept = true;
                                    processed = true;
                                    break processing;
                                } else {
                                    throw e;
                                }
                            }
                        }
                        if (!processed) {
                            throw e;
                        }
                        if (askAccept) {
                            if (trustCallback.apply(sb.toString())) {
                                System.out.println("accepted");
                                trustCache.put(fingerprint, true);
                                try {
                                    ts.setCertificateEntry(chain[0].getSubjectX500Principal().getName(), chain[0]);
                                    try (FileOutputStream fos = new FileOutputStream(truststore)) {
                                        ts.store(fos, "password".toCharArray());
                                        fos.flush();
                                        fos.close();
                                        System.out.println("stored");
                                    } catch (NoSuchAlgorithmException ex) {
                                        Logger.getLogger(ContextFactory.class.getName()).log(Level.SEVERE, null, ex);
                                    } catch (IOException ex) {
                                        Logger.getLogger(ContextFactory.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                } catch (KeyStoreException ex) {
                                    Logger.getLogger(ContextFactory.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            } else {
                                System.out.println("rejected");
                                trustCache.put(fingerprint, false);
                                throw e;
                            }
                            return true;
                        }
                    }
                    return false;
                }
            };
            
            //FallbackX509ExtendedKeyManager km = new FallbackX509ExtendedKeyManager(kmf);
            
            //sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            //sslContext.init(kmf.getKeyManagers(), (TrustManager[]) MeUtils.concatArrays(tmf.getTrustManagers(), new TrustManager[] {tm}), null);
            ctx.sslContext.init(kmf.getKeyManagers(), new TrustManager[] {tm}, null);
            return ctx;//((sun.security.ssl.SunX509KeyManagerImpl)(kmf.getKeyManagers()[0]))
        }
    }

    // https://stackoverflow.com/a/5488964/513038
    /**
     * Create a self-signed X.509 Certificate
     *
     * @param dn the X.509 Distinguished Name, eg "CN=Test, L=London, C=GB"
     * @param pair the KeyPair
     * @param days how many days from now the Certificate is valid for
     * @param algorithm the signing algorithm, eg "SHA1withRSA"
     */
    /*
    public static X509Certificate generateCertificate(String dn, KeyPair pair, int days, String algorithm) throws GeneralSecurityException, IOException {
        PrivateKey privkey = pair.getPrivate();
        X509CertInfo info = new X509CertInfo();
        Date from = new Date();
        Date to = new Date(from.getTime() + days * 86400000l);
        CertificateValidity interval = new CertificateValidity(from, to);
        BigInteger sn = new BigInteger(64, new SecureRandom());
        X500Name owner = new X500Name(dn);

        info.set(X509CertInfo.VALIDITY, interval);
        info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(sn));
        info.set(X509CertInfo.SUBJECT, owner);
        info.set(X509CertInfo.ISSUER, owner);
        info.set(X509CertInfo.KEY, new CertificateX509Key(pair.getPublic()));
        info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
        AlgorithmId algo = new AlgorithmId(AlgorithmId.md5WithRSAEncryption_oid);
        info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algo));

        // Sign the cert to identify the algorithm that's used.
        X509CertImpl cert = new X509CertImpl(info);
        cert.sign(privkey, algorithm);

        // Update the algorith, and resign.
        algo = (AlgorithmId) cert.get(X509CertImpl.SIG_ALG);
        info.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, algo);
        cert = new X509CertImpl(info);
        cert.sign(privkey, algorithm);
        return cert;
    }
    */
    
    // https://stackoverflow.com/a/59331561/513038
    private static final Provider provider = new BouncyCastleProvider();
    public static X509Certificate generateCertificateObject(String fqdn, KeyPair keypair, SecureRandom random, Date notBefore, Date notAfter, String algorithm) throws CertificateException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, SignatureException, OperatorCreationException {
        PrivateKey key = keypair.getPrivate();

        // Prepare the information required for generating an X.509 certificate.
        org.spongycastle.asn1.x500.X500Name owner = new org.spongycastle.asn1.x500.X500Name(fqdn);
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(owner, new BigInteger(64, random), notBefore, notAfter, owner, keypair.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder(algorithm).build(key); // ex: SHA256WithRSAEncryption
        X509CertificateHolder certHolder = builder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter().setProvider(provider).getCertificate(certHolder);
        cert.verify(keypair.getPublic());
        
        return cert;
    }
}
