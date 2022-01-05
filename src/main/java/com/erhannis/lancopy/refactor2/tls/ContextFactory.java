package com.erhannis.lancopy.refactor2.tls;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;
import org.apache.commons.codec.digest.DigestUtils;
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
    }
    

    public static Context authenticatedContext(String protocol, String keystore, String truststore) throws GeneralSecurityException, IOException {
        //TODO Optionize some of these things?  Passwords, 
        
        Context ctx = new Context();
        
        ctx.sslContext = SSLContext.getInstance(protocol);
        
        // Keystore
        KeyStore ks = KeyStore.getInstance("PKCS12");
        File ksFile =  new File(keystore);
        Path ksPath = ksFile.toPath();
        if (!Files.exists(ksPath)) {
            System.out.println("Generating key...");
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(4096);
            KeyPair kp = kpg.generateKeyPair();
            Key pub = kp.getPublic();
            Key pvt = kp.getPrivate();
            
            ks.load(null, "password".toCharArray());
            //X509Certificate cert = generateCertificate("CN=Unknown, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown", kp, 1000, "SHA384withRSA");
            X509Certificate cert = generateCertificate("CN="+UUID.randomUUID()+", OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown", kp, 1000, "SHA384withRSA");
            ks.setKeyEntry("node", pvt, "password".toCharArray(), new Certificate[]{cert});
            FileOutputStream fos = new FileOutputStream(ksFile);
            ks.store(fos, "password".toCharArray());
            fos.flush();
            fos.close();
        }

        // Truststore
        KeyStore ts = KeyStore.getInstance("PKCS12");
        File tsFile =  new File(truststore);
        Path tsPath = tsFile.toPath();
        if (!Files.exists(tsPath)) {
            System.out.println("Generating initial truststore...");
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(4096);
            KeyPair kp = kpg.generateKeyPair();
            
            ts.load(null, "password".toCharArray());
            // Like, I'm tempted to store our own public key, but that'd mean automatically trusting communications which claim to come from OURSELF, which feels weeeeird....
            // And if I just leave the truststore empty, the code that uses it throws a weird exception.
            X509Certificate cert = generateCertificate("CN=Unknown, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown", kp, 1000, "SHA384withRSA");
            // Note that it's setKeyEntry for keystores and setCertificateEntry for truststores.  You can also use: ts.setEntry("dummy", new KeyStore.TrustedCertificateEntry(cert), null);
            ts.setCertificateEntry("dummy", cert);
            FileOutputStream fos = new FileOutputStream(tsFile);
            ts.store(fos, "password".toCharArray());
            fos.flush();
            fos.close();
        }
        
        try (InputStream keystoreFile = Files.newInputStream(new File(keystore).toPath()) ; InputStream truststoreFile = Files.newInputStream(tsPath)) {
            // (Re)load ks/ts
            ks.load(keystoreFile, "password".toCharArray());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, "password".toCharArray());
            ts.load(truststoreFile, "password".toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);

            ctx.sha256Fingerprint = DigestUtils.sha256Hex(ks.getCertificate("node").getEncoded());
            
            X509TrustManager tm = new FallbackX509TrustManager(tmf) {
                @Override
                public void failedClientTrusted(CertificateException e, X509Certificate[] chain, String authType) throws CertificateException {
                    System.out.println("failedClientTrusted " + e + "\n" + authType + " " + Arrays.toString(chain));
                    handleCertFailure(e, chain, authType);
                }

                @Override
                public void failedServerTrusted(CertificateException e, X509Certificate[] chain, String authType) throws CertificateException {
                    System.out.println("failedServerTrusted " + e + "\n" + authType + " " + Arrays.toString(chain));
                    handleCertFailure(e, chain, authType);
                }                

                private void handleCertFailure(CertificateException e, X509Certificate[] chain, String authType) throws CertificateException {
                    Throwable cause = e.getCause();
                    boolean askAccept = false;
                    if (cause instanceof sun.security.provider.certpath.SunCertPathBuilderException) {
                        System.out.println("This certificate id has not been recorded.");
                        System.out.println(DigestUtils.sha256Hex(chain[0].getEncoded()));
                        System.out.println("Trust it and record it? (y/N)");
                        askAccept = true;
                    } else if (CertPathValidatorException.BasicReason.INVALID_SIGNATURE == (((java.security.cert.CertPathValidatorException)cause).getReason())) {
                        //TODO Is this the only reason/exception we care about?
                        System.out.println("THIS CERTIFICATE IS DIFFERENT FROM THE ONE ON RECORD.");
                        System.out.println(DigestUtils.sha256Hex(chain[0].getEncoded()));
                        System.out.println("Trust it and overwrite the old one? (y/N)");
                        askAccept = true;
                    } else {
                        throw e;
                    }
                    if (askAccept) {
                        int response = 'n';
                        try {
                            //response = System.in.read();
                            ERROR;
                        } catch (IOException ex) {
                            Logger.getLogger(ContextFactory.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        if (response == 'y') {
                            System.out.println("accepted");
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
                            throw e;
                        }
                    }
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
}
