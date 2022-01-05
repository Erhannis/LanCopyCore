/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.erhannis.lancopy.refactor2.tls;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public abstract class FallbackX509TrustManager implements X509TrustManager {

    /*
     * The default X509TrustManager returned by IbmX509. We'll delegate
     * decisions to it, and fall back to the logic in this class if the
     * default X509TrustManager doesn't trust it.
     */
    private X509TrustManager pkixTrustManager = null;

    public FallbackX509TrustManager(TrustManagerFactory tmf) {
        // create a default JSSE X509TrustManager.
        TrustManager tms[] = tmf.getTrustManagers();

        /*
         * Iterate over the returned trustmanagers, look
         * for an instance of X509TrustManager. If found,
         * use that as our default trust manager.
         */
        for (int i = 0; i < tms.length; i++) {
            if (tms[i] instanceof X509TrustManager) {
                pkixTrustManager = (X509TrustManager) tms[i];
                return;
            }
        }

        /*
         * Find some other way to initialize, or else we have to fail the
         * constructor.
         */
        //throw new Exception("Couldn't initialize");
    }

    /**
     * The delegated checkClientTrusted failed; should it be permitted to pass anyway?
     * @param t
     * @param chain
     * @param authType
     * @throws CertificateException 
     */
    public abstract void failedClientTrusted(CertificateException e, X509Certificate[] chain, String authType) throws CertificateException;        

    /**
     * The delegated checkServerTrusted failed; should it be permitted to pass anyway?
     * @param t
     * @param chain
     * @param authType
     * @throws CertificateException 
     */
    public abstract void failedServerTrusted(CertificateException e, X509Certificate[] chain, String authType) throws CertificateException;        
    
    /*
     * Delegate to the default trust manager.
     */
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (pkixTrustManager == null) {
            failedServerTrusted(null, chain, authType);
        } else {
            try {
                pkixTrustManager.checkClientTrusted(chain, authType);
            } catch (CertificateException e) {
                failedClientTrusted(e, chain, authType);
            }
        }
    }

    /*
     * Delegate to the default trust manager.
     */
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (pkixTrustManager == null) {
            failedServerTrusted(null, chain, authType);
        } else {
            try {
                pkixTrustManager.checkServerTrusted(chain, authType);
            } catch (CertificateException e) {
                failedServerTrusted(e, chain, authType);
            }
        }
    }

    public X509Certificate[] getAcceptedIssuers() {
        if (pkixTrustManager != null) {
            //return pkixTrustManager.getAcceptedIssuers();
            // If we return accepted issuers, it prevents new clients from attempting to connect, because they have no matching keys
            return new X509Certificate[0];
        } else {
            return new X509Certificate[0];
        }
    }
}
