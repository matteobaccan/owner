/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.handlers;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.io.ByteArrayInputStream;

/**
 * The asymmetric cipher: RSA-OAEP over AES-256/GCM, behind the name <code>rsa-oaep</code>.
 *
 * <h2>What it is for, and why it is not a variation on the other one</h2>
 * <p>
 * With {@link AesGcmHandler} whoever can write a secret into the file can read every other secret in it,
 * because there is one passphrase and it does both. With a key pair those two stop being the same
 * permission:
 * </p>
 * <ul>
 *   <li>a developer, or a CI job, holds the <b>public</b> key and can add a value to a configuration
 *   <b>without being able to read the ones already there</b>;</li>
 *   <li>the deployment holds the <b>private</b> key and can read them, but the key never has to travel to
 *   anybody who only needs to write.</li>
 * </ul>
 * <p>
 * That is what a keystore buys in Spring Cloud Config, and what <code>sops</code> and <code>age</code> are
 * built around. It is a different permission model, not a different algorithm.
 * </p>
 * <pre>
 *     // the deployment: only the private key
 *     ConfigFactory.registerValueHandler(
 *             new RsaHandler(RsaHandler.privateKeyFrom(Paths.get("/etc/app/app.key"))));
 *
 *     // whoever writes a value: only the public key
 *     new RsaHandler(RsaHandler.publicKeyFrom(Paths.get("app.pub"))).markerFor("s3cr3t");
 * </pre>
 * <p>
 * A handler holding only one half says so when it is asked for the other, which is the point rather than
 * a limitation.
 * </p>
 *
 * <h2>The construction is hybrid, because RSA cannot encrypt a value</h2>
 * <p>
 * RSA-2048 with OAEP-SHA256 takes at most 190 bytes and then falls off a cliff, which covers most
 * configuration values and not a certificate, a connection string or a private key of somebody else's. So
 * a fresh AES-256 key is drawn <b>per value</b>, the value is encrypted with it under GCM, and the key -
 * 32 bytes, which fits with room to spare even under RSA-1024 - is what RSA wraps.
 * </p>
 * <pre>
 *     base64( fingerprint(4) | wrapped key length(2) | wrapped key(n) | iv(12) | ciphertext | tag(16) )
 * </pre>
 * <p>
 * The whole header is passed to GCM as additional authenticated data, so none of it can be edited on its
 * own. There is no salt and no iteration count here: <b>there is no passphrase to derive from</b>, which
 * is also why nothing is shared between the values of one run and why this handler needs no key cache.
 * </p>
 * <p>
 * <b>The fingerprint is four bytes of SHA-256 over the RSA modulus</b>, which both halves of a key pair
 * expose. It is not a security measure - a public key is public - it is a diagnosis: encrypting against
 * the wrong public key is the mistake this design invites, since the person making it cannot decrypt what
 * they wrote and so cannot notice. Without it the deployment fails with "could not be decrypted" and no
 * hint; with it, it says the token was written for another key.
 * </p>
 * <p>
 * <b>OAEP is given its parameters explicitly</b>, SHA-256 for the digest and SHA-256 for MGF1. Naming the
 * transformation alone - <code>RSA/ECB/OAEPWithSHA-256AndMGF1Padding</code> - leaves MGF1 on SHA-1 in the
 * JDK, which is self-consistent but not what the name says, and does not interoperate with an
 * <code>openssl</code> that was told SHA-256.
 * </p>
 *
 * <h2>Rotation, and serialization</h2>
 * <p>
 * As for the symmetric handler, a rotation is two names registered at once - <code>rsa-2025</code> beside
 * <code>rsa-2024</code> - and proceeds one value at a time. And <b>the private key is transient</b>: a
 * Config object is serializable and a handler is reachable from one, so writing it out would put a private
 * key in a file nobody chose to protect. The public key is not transient, because it is public.
 * </p>
 *
 * @author Matteo Baccan
 * @see AesGcmHandler
 * @since 2.0.0
 */
public class RsaHandler implements ValueHandler, Encrypting {

    private static final long serialVersionUID = 8145517432160871157L;

    /** The name this handler answers to unless another is given. */
    public static final String DEFAULT_NAME = "rsa-oaep";

    /**
     * The smallest modulus this handler will use, in bits.
     * <p>
     * RSA-1024 is not refused because it cannot wrap 32 bytes - it can - but because it has been below
     * the recommended size since 2010, and a configuration is read for years.
     * </p>
     */
    public static final int MINIMUM_KEY_BITS = 2048;

    private static final String WRAP_CIPHER = "RSA/ECB/OAEPPadding";
    private static final String DATA_CIPHER = "AES/GCM/NoPadding";
    private static final int DATA_KEY_BITS = 256;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int FINGERPRINT_BYTES = 4;
    private static final int PREFIX_BYTES = FINGERPRINT_BYTES + 2;
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /** The name values refer to this handler by. */
    private final String name;

    /** The key values are encrypted to, or <code>null</code> in a handler that only reads. */
    private final PublicKey publicKey;

    /** See the note on serialization: a private key is not written out with the object holding it. */
    private final transient PrivateKey privateKey;

    /**
     * Transient, and therefore created on demand rather than in the constructor: a serialized
     * <code>SecureRandom</code> carries its state, so two JVMs reading the same serialized object would
     * draw the same IVs. Deliberately not final, because a deserialized handler still holds its public
     * key and may legitimately encrypt.
     */
    private transient SecureRandom random;

    /** Four bytes of SHA-256 over the modulus, computed once from whichever half is present. */
    private final byte[] fingerprint;

    /**
     * A handler that can only write: the case of whoever adds a value without being able to read one.
     *
     * @param publicKey the key values are encrypted to.
     */
    public RsaHandler(PublicKey publicKey) {
        this(DEFAULT_NAME, publicKey, null);
    }

    /**
     * A handler that can only read: the case of a deployment, and the reason a key pair is used at all.
     *
     * @param privateKey the key values are decrypted with.
     */
    public RsaHandler(PrivateKey privateKey) {
        this(DEFAULT_NAME, null, privateKey);
    }

    /**
     * A handler that can do both, which is convenient in a test and rarely what a deployment wants.
     *
     * @param publicKey  the key values are encrypted to.
     * @param privateKey the key values are decrypted with.
     */
    public RsaHandler(PublicKey publicKey, PrivateKey privateKey) {
        this(DEFAULT_NAME, publicKey, privateKey);
    }

    /**
     * A handler under a name of your own, holding either half of a key pair or both.
     *
     * @param name       the name values refer to this handler by, which is where a key rotation goes.
     * @param publicKey  the key values are encrypted to, or <code>null</code> to only decrypt.
     * @param privateKey the key values are decrypted with, or <code>null</code> to only encrypt.
     * @throws IllegalArgumentException if both are null, if either is not an RSA key, if they do not
     *                                  belong to the same key pair, or if the modulus is below
     *                                  {@link #MINIMUM_KEY_BITS}.
     */
    public RsaHandler(String name, PublicKey publicKey, PrivateKey privateKey) {
        if (publicKey == null && privateKey == null)
            throw new IllegalArgumentException("a handler with neither key can do nothing: pass the "
                    + "public key to encrypt, the private key to decrypt, or both");
        BigInteger modulus = modulusOf(publicKey != null ? publicKey : privateKey);
        if (publicKey != null && privateKey != null && !modulus.equals(modulusOf(privateKey)))
            throw new IllegalArgumentException("the public and the private key are not two halves of one "
                    + "key pair: their moduli differ");
        if (modulus.bitLength() < MINIMUM_KEY_BITS)
            throw new IllegalArgumentException(String.format(
                    "an RSA key of %d bits is below the %d this handler accepts. A configuration is read "
                            + "for years, and this size stopped being recommended in 2010.",
                    modulus.bitLength(), MINIMUM_KEY_BITS));
        this.name = name;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.fingerprint = fingerprintOf(modulus);
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * Decrypts a token, which is the payload of the marker naming this handler.
     *
     * @throws IllegalArgumentException if this handler holds no private key, if the token was written for
     *                                  another key pair, if it is not a token, or if it has been edited.
     */
    @Override
    public String resolve(String payload) {
        if (privateKey == null)
            throw new IllegalArgumentException("The handler registered as '" + name + "' holds only a "
                    + "public key, so it can write a value and not read one. Register it with the private "
                    + "key where the configuration is read. If it came back from deserialization, the "
                    + "private key was deliberately left behind.");

        byte[] token = decode(payload);
        if (token.length < PREFIX_BYTES)
            throw notAToken("it is " + token.length + " bytes, too short to hold a header");

        ByteBuffer buffer = ByteBuffer.wrap(token);
        byte[] theirs = new byte[FINGERPRINT_BYTES];
        buffer.get(theirs);
        if (!java.util.Arrays.equals(theirs, fingerprint))
            throw notAToken(String.format("it was written for the key pair %s and this handler holds %s. "
                            + "Encrypting against the wrong public key is silent, because whoever does it "
                            + "cannot read back what they wrote",
                    hex(theirs), hex(fingerprint)));

        int wrappedLength = buffer.getShort() & 0xffff;
        int headerBytes = PREFIX_BYTES + wrappedLength + IV_BYTES;
        if (wrappedLength == 0 || token.length < headerBytes + TAG_BITS / 8)
            throw notAToken("the wrapped key does not fit in what follows it");
        byte[] wrapped = new byte[wrappedLength];
        buffer.get(wrapped);
        byte[] iv = new byte[IV_BYTES];
        buffer.get(iv);

        try {
            Cipher unwrap = Cipher.getInstance(WRAP_CIPHER);
            unwrap.init(Cipher.DECRYPT_MODE, privateKey, oaep());
            SecretKey dataKey = new SecretKeySpec(unwrap.doFinal(wrapped), "AES");

            Cipher cipher = Cipher.getInstance(DATA_CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, dataKey, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(token, 0, headerBytes);
            return new String(cipher.doFinal(token, headerBytes, token.length - headerBytes), UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("A value handled by '" + name + "' could not be decrypted. "
                    + "The fingerprint matched, so the key pair is the right one: either the value has "
                    + "been changed since it was written, or it was not written by this handler.", e);
        }
    }

    /**
     * Encrypts a value, producing the token that goes inside a marker.
     *
     * @param plainText the value to encrypt.
     * @return the base64 token.
     * @throws IllegalArgumentException if this handler holds no public key.
     */
    public String encrypt(String plainText) {
        if (publicKey == null)
            throw new IllegalArgumentException("The handler '" + name + "' holds only a private key, so "
                    + "it can read a value and not write one. Whoever writes values needs the public key, "
                    + "which is the arrangement a key pair exists for.");
        if (plainText == null)
            throw new IllegalArgumentException("there is nothing to encrypt: the value is null");

        try {
            SecureRandom source = random();
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(DATA_KEY_BITS, source);
            SecretKey dataKey = generator.generateKey();

            Cipher wrap = Cipher.getInstance(WRAP_CIPHER);
            wrap.init(Cipher.ENCRYPT_MODE, publicKey, oaep());
            byte[] wrapped = wrap.doFinal(dataKey.getEncoded());

            byte[] iv = new byte[IV_BYTES];
            source.nextBytes(iv);
            byte[] header = ByteBuffer.allocate(PREFIX_BYTES + wrapped.length + IV_BYTES)
                    .put(fingerprint).putShort((short) wrapped.length).put(wrapped).put(iv).array();

            Cipher cipher = Cipher.getInstance(DATA_CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, dataKey, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(header);
            byte[] sealed = cipher.doFinal(plainText.getBytes(UTF_8));

            byte[] token = ByteBuffer.allocate(header.length + sealed.length).put(header).put(sealed).array();
            return Base64.getEncoder().encodeToString(token);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Could not encrypt a value with '" + name + "': "
                    + e.getMessage(), e);
        }
    }

    /**
     * Encrypts several values. Unlike the symmetric handler there is nothing to share between them: every
     * value gets its own AES key and its own IV, because there is no passphrase to derive from and
     * therefore no cost to amortise.
     *
     * @param plainTexts the values to encrypt.
     * @return the base64 tokens, in the same order.
     */
    @Override
    public String[] encryptAll(String... plainTexts) {
        String[] tokens = new String[plainTexts.length];
        for (int i = 0; i < plainTexts.length; i++)
            tokens[i] = encrypt(plainTexts[i]);
        return tokens;
    }

    /**
     * The whole marker for a value, which is what gets pasted into a configuration file.
     *
     * @param plainText the value to encrypt.
     * @return the marker, ready to be written as a property value.
     */
    public String markerFor(String plainText) {
        return marker(encrypt(plainText));
    }

    /**
     * The marker wrapping an already encrypted token.
     *
     * @param token the token.
     * @return the marker.
     */
    @Override
    public String marker(String token) {
        return "${$" + name + "::" + token + "}";
    }

    /**
     * Reads a public key from PEM, in any of the three shapes somebody is likely to have: a
     * <code>PUBLIC KEY</code> block, a <code>CERTIFICATE</code> block - which is what a keystore exports -
     * or the base64 of either with the header lines missing.
     *
     * @param pem the PEM text.
     * @return the public key.
     * @throws IllegalArgumentException if it is not a key this can read, with what to run to convert it.
     */
    public static PublicKey publicKeyFrom(String pem) {
        if (pem != null && pem.contains("BEGIN CERTIFICATE")) {
            try {
                return CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(pem.getBytes(UTF_8)))
                        .getPublicKey();
            } catch (GeneralSecurityException e) {
                throw new IllegalArgumentException("that certificate could not be read: " + e.getMessage(), e);
            }
        }
        byte[] der = derFrom(pem, "public key");
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(
                    "that is not an X.509 public key. openssl writes one with:\n"
                            + "    openssl rsa -in app.key -pubout -out app.pub", e);
        }
    }

    /**
     * Reads a public key from a PEM file.
     *
     * @param pem the file.
     * @return the public key.
     */
    public static PublicKey publicKeyFrom(Path pem) {
        return publicKeyFrom(read(pem));
    }

    /**
     * Reads a private key from PKCS#8 PEM - a <code>PRIVATE KEY</code> block, which is what
     * <code>openssl genpkey</code> writes.
     * <p>
     * The older PKCS#1 shape, <code>BEGIN RSA PRIVATE KEY</code>, is refused rather than half-read,
     * because the JDK has no <code>KeySpec</code> for it and writing an ASN.1 reader to accept one file
     * format is not a thing to put in a configuration library. The message says how to convert it, which
     * is one command.
     * </p>
     *
     * @param pem the PEM text.
     * @return the private key.
     * @throws IllegalArgumentException if it is not a key this can read.
     */
    public static PrivateKey privateKeyFrom(String pem) {
        if (pem != null && pem.contains("BEGIN RSA PRIVATE KEY"))
            throw new IllegalArgumentException(
                    "that is a PKCS#1 key, which the JDK cannot read. Convert it once with:\n"
                            + "    openssl pkcs8 -topk8 -nocrypt -in app.key -out app-pkcs8.key");
        byte[] der = derFrom(pem, "private key");
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(
                    "that is not a PKCS#8 private key. openssl writes one with:\n"
                            + "    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out app.key", e);
        }
    }

    /**
     * Reads a private key from a PKCS#8 PEM file.
     *
     * @param pem the file.
     * @return the private key.
     */
    public static PrivateKey privateKeyFrom(Path pem) {
        return privateKeyFrom(read(pem));
    }

    /**
     * OAEP with SHA-256 on both sides of it. Given explicitly rather than named in the transformation,
     * because <code>OAEPWithSHA-256AndMGF1Padding</code> leaves MGF1 on SHA-1 in the JDK.
     */
    /** See the field: created on first use, so that deserializing never inherits a random's state. */
    private synchronized SecureRandom random() {
        if (random == null)
            random = new SecureRandom();
        return random;
    }

    private static OAEPParameterSpec oaep() {
        return new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT);
    }

    private static BigInteger modulusOf(Key key) {
        if (!(key instanceof RSAKey))
            throw new IllegalArgumentException(key.getAlgorithm() + " is not RSA. This handler is the "
                    + "asymmetric one; for a passphrase use AesGcmHandler.");
        return ((RSAKey) key).getModulus();
    }

    private static byte[] fingerprintOf(BigInteger modulus) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(modulus.toByteArray());
            byte[] shortened = new byte[FINGERPRINT_BYTES];
            System.arraycopy(digest, 0, shortened, 0, FINGERPRINT_BYTES);
            return shortened;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is required of every JDK and is missing", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte b : bytes)
            text.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        return text.toString();
    }

    /** The DER inside a PEM block, header lines and whitespace removed; also accepts bare base64. */
    private static byte[] derFrom(String pem, String what) {
        if (pem == null || pem.trim().isEmpty())
            throw new IllegalArgumentException("there is no " + what + " here: the text is empty");
        StringBuilder base64 = new StringBuilder();
        for (String line : pem.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("-----"))
                continue;
            base64.append(trimmed);
        }
        try {
            return Base64.getDecoder().decode(base64.toString());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("that " + what + " is not base64, so it is not PEM either");
        }
    }

    private static String read(Path pem) {
        try {
            return new String(Files.readAllBytes(pem), UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("could not read " + pem + ": " + e.getMessage(), e);
        }
    }

    private byte[] decode(String payload) {
        try {
            return Base64.getDecoder().decode(payload.trim());
        } catch (IllegalArgumentException e) {
            throw notAToken("it is not base64");
        }
    }

    /** Says what is wrong with the envelope without repeating the payload, which may be a secret. */
    private IllegalArgumentException notAToken(String why) {
        return new IllegalArgumentException(String.format(
                "A value refers to '%s' but what follows the :: is not a token this handler can read: %s.",
                name, why));
    }

    /** Two handlers are told apart by name and by key pair, never by which halves they happen to hold. */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        RsaHandler that = (RsaHandler) other;
        return name.equals(that.name) && java.util.Arrays.equals(fingerprint, that.fingerprint);
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + java.util.Arrays.hashCode(fingerprint);
    }

    /** Never key material. The fingerprint is four bytes of a public modulus, which is public. */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[name=" + name + ", keyPair=" + hex(fingerprint)
                + ", canEncrypt=" + (publicKey != null) + ", canDecrypt=" + (privateKey != null) + "]";
    }
}
