/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.messaging.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.*;
import java.util.*;

/**
 * Shared SSL/TLS helper for all broker types.
 *
 * <p>Supports two certificate formats:</p>
 * <ul>
 *   <li><b>PEM</b> — .pem / .crt / .key files (industry standard for cloud-native deployments)</li>
 *   <li><b>JKS/PKCS12</b> — Java keystore files (.jks / .p12)</li>
 * </ul>
 *
 * <p>PEM mode fields (in the "ssl" config block):</p>
 * <pre>
 *   format: "PEM"
 *   ca_cert_path:     Path to CA certificate (or cert chain) .pem / .crt
 *   client_cert_path: Path to client certificate .pem / .crt
 *   client_key_path:  Path to client private key .pem / .key
 *   protocol:         TLS version (default "TLSv1.3")
 * </pre>
 *
 * <p>JKS/PKCS12 mode fields:</p>
 * <pre>
 *   format: "JKS" or "PKCS12"
 *   keystore_path, keystore_password, keystore_type
 *   truststore_path, truststore_password, truststore_type
 *   protocol: TLS version (default "TLSv1.3")
 * </pre>
 */
public final class SslHelper {

    private static final Logger log = LoggerFactory.getLogger(SslHelper.class);

    private SslHelper() {}

    /**
     * Create an SSLContext from the given ssl configuration map.
     * Automatically detects PEM vs JKS/PKCS12 based on the "format" field.
     */
    public static SSLContext createSslContext(Map<String, Object> ssl) throws Exception {
        String format = String.valueOf(ssl.getOrDefault("format", "JKS")).toUpperCase();
        String protocol = String.valueOf(ssl.getOrDefault("protocol", "TLSv1.3"));

        return switch (format) {
            case "PEM" -> createFromPem(ssl, protocol);
            case "JKS", "PKCS12", "P12" -> createFromKeystore(ssl, protocol, format);
            default -> throw new IllegalArgumentException("Unsupported SSL format: " + format +
                    ". Use PEM, JKS, or PKCS12.");
        };
    }

    // ─── PEM-based SSL Context ────────────────────────────────────────────────

    /**
     * Build an SSLContext from PEM files (CA cert, client cert, client key).
     */
    private static SSLContext createFromPem(Map<String, Object> ssl, String protocol) throws Exception {
        String caCertPath     = (String) ssl.get("ca_cert_path");
        String clientCertPath = (String) ssl.get("client_cert_path");
        String clientKeyPath  = (String) ssl.get("client_key_path");

        // ── TrustManager (CA certificate / cert chain) ──
        TrustManager[] trustManagers = null;
        if (caCertPath != null) {
            X509Certificate[] caCerts = loadCertificateChain(caCertPath);
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            for (int i = 0; i < caCerts.length; i++) {
                trustStore.setCertificateEntry("ca-" + i, caCerts[i]);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            trustManagers = tmf.getTrustManagers();
            log.info("PEM TrustManager loaded {} CA certificate(s) from {}", caCerts.length, caCertPath);
        }

        // ── KeyManager (client cert + private key for mTLS) ──
        KeyManager[] keyManagers = null;
        if (clientCertPath != null && clientKeyPath != null) {
            X509Certificate[] clientCerts = loadCertificateChain(clientCertPath);
            PrivateKey privateKey = loadPrivateKey(clientKeyPath);

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            char[] emptyPass = "".toCharArray();
            keyStore.setKeyEntry("client", privateKey, emptyPass, clientCerts);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, emptyPass);
            keyManagers = kmf.getKeyManagers();
            log.info("PEM KeyManager loaded client cert from {} and key from {}", clientCertPath, clientKeyPath);
        }

        SSLContext sslContext = SSLContext.getInstance(protocol);
        sslContext.init(keyManagers, trustManagers, new SecureRandom());
        log.info("SSLContext created with protocol {} (PEM mode)", protocol);
        return sslContext;
    }

    // ─── JKS / PKCS12 based SSL Context ───────────────────────────────────────

    private static SSLContext createFromKeystore(Map<String, Object> ssl, String protocol, String format)
            throws Exception {

        // ── TrustManager ──
        TrustManager[] trustManagers = null;
        String truststorePath = (String) ssl.get("truststore_path");
        if (truststorePath != null) {
            String truststorePassword = String.valueOf(ssl.getOrDefault("truststore_password", ""));
            String truststoreType = String.valueOf(ssl.getOrDefault("truststore_type", format));
            KeyStore trustStore = KeyStore.getInstance(truststoreType);
            try (InputStream is = Files.newInputStream(Path.of(truststorePath))) {
                trustStore.load(is, truststorePassword.toCharArray());
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            trustManagers = tmf.getTrustManagers();
            log.info("Keystore TrustManager loaded from {} (type={})", truststorePath, truststoreType);
        }

        // ── KeyManager ──
        KeyManager[] keyManagers = null;
        String keystorePath = (String) ssl.get("keystore_path");
        if (keystorePath != null) {
            String keystorePassword = String.valueOf(ssl.getOrDefault("keystore_password", ""));
            String keystoreType = String.valueOf(ssl.getOrDefault("keystore_type", format));
            KeyStore keyStore = KeyStore.getInstance(keystoreType);
            try (InputStream is = Files.newInputStream(Path.of(keystorePath))) {
                keyStore.load(is, keystorePassword.toCharArray());
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keystorePassword.toCharArray());
            keyManagers = kmf.getKeyManagers();
            log.info("Keystore KeyManager loaded from {} (type={})", keystorePath, keystoreType);
        }

        SSLContext sslContext = SSLContext.getInstance(protocol);
        sslContext.init(keyManagers, trustManagers, new SecureRandom());
        log.info("SSLContext created with protocol {} ({} mode)", protocol, format);
        return sslContext;
    }

    // ─── PEM File Parsers ─────────────────────────────────────────────────────

    /**
     * Load one or more X.509 certificates from a PEM file (supports cert chains).
     */
    public static X509Certificate[] loadCertificateChain(String path) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (InputStream is = Files.newInputStream(Path.of(path))) {
            Collection<? extends Certificate> certs = cf.generateCertificates(is);
            return certs.toArray(new X509Certificate[0]);
        }
    }

    /**
     * Load a PKCS#8 private key from a PEM file.
     * Handles both "BEGIN PRIVATE KEY" (PKCS#8) and "BEGIN RSA PRIVATE KEY" (PKCS#1).
     */
    public static PrivateKey loadPrivateKey(String path) throws Exception {
        String pem = Files.readString(Path.of(path)).trim();

        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            // PKCS#1 format — strip headers, decode, wrap in PKCS#8 envelope
            String base64 = pem
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] derBytes = Base64.getDecoder().decode(base64);
            // Wrap PKCS#1 in PKCS#8 envelope
            byte[] pkcs8Bytes = wrapPkcs1InPkcs8(derBytes);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8Bytes);
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);

        } else if (pem.contains("BEGIN PRIVATE KEY")) {
            // PKCS#8 format — standard
            String base64 = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] derBytes = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(derBytes);
            // Try RSA first, then EC
            try {
                return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
            } catch (InvalidKeySpecException e) {
                return KeyFactory.getInstance("EC").generatePrivate(keySpec);
            }

        } else if (pem.contains("BEGIN EC PRIVATE KEY")) {
            // SEC1 EC format
            String base64 = pem
                    .replace("-----BEGIN EC PRIVATE KEY-----", "")
                    .replace("-----END EC PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] derBytes = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(derBytes);
            return KeyFactory.getInstance("EC").generatePrivate(keySpec);

        } else {
            throw new IllegalArgumentException("Unsupported private key format in " + path);
        }
    }

    /**
     * Wraps a PKCS#1 RSA key in PKCS#8 ASN.1 envelope.
     */
    private static byte[] wrapPkcs1InPkcs8(byte[] pkcs1Bytes) {
        // RSA OID: 1.2.840.113549.1.1.1
        byte[] oid = {0x06, 0x09, 0x2A, (byte)0x86, 0x48, (byte)0x86, (byte)0xF7, 0x0D, 0x01, 0x01, 0x01};
        byte[] nullParam = {0x05, 0x00};

        // AlgorithmIdentifier SEQUENCE
        byte[] algId = wrapInSequence(concat(oid, nullParam));

        // Wrap pkcs1 key in OCTET STRING
        byte[] keyOctet = wrapInOctetString(pkcs1Bytes);

        // Version INTEGER 0
        byte[] version = {0x02, 0x01, 0x00};

        // Final PKCS#8 SEQUENCE
        return wrapInSequence(concat(version, concat(algId, keyOctet)));
    }

    private static byte[] wrapInSequence(byte[] data) {
        return wrapInTag((byte) 0x30, data);
    }

    private static byte[] wrapInOctetString(byte[] data) {
        return wrapInTag((byte) 0x04, data);
    }

    private static byte[] wrapInTag(byte tag, byte[] data) {
        byte[] length = encodeDerLength(data.length);
        byte[] result = new byte[1 + length.length + data.length];
        result[0] = tag;
        System.arraycopy(length, 0, result, 1, length.length);
        System.arraycopy(data, 0, result, 1 + length.length, data.length);
        return result;
    }

    private static byte[] encodeDerLength(int length) {
        if (length < 128) return new byte[]{(byte) length};
        if (length < 256) return new byte[]{(byte) 0x81, (byte) length};
        return new byte[]{(byte) 0x82, (byte) (length >> 8), (byte) (length & 0xFF)};
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
