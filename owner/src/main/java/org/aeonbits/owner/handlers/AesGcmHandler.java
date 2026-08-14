/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.handlers;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The cipher this library ships: AES-256/GCM over a passphrase, behind the name <code>aes-gcm</code>.
 * <pre>
 *     ConfigFactory.registerValueHandler(new AesGcmHandler(passphrase));
 * </pre>
 * <pre>
 *     db.password = ${$aes-gcm::AAM0IHNhbHQ...}
 * </pre>
 * <p>
 * The passphrase never comes from the properties, which would be circular. It arrives here, from wherever
 * the application already keeps it, and the configuration is created afterwards.
 * </p>
 *
 * <h2>The token</h2>
 * <pre>
 *     base64( iterations(4) | salt(16) | iv(12) | ciphertext | tag(16) )
 * </pre>
 * <p>
 * Everything decryption depends on travels with the value, and the first 32 bytes are passed to GCM as
 * additional authenticated data, so the header is covered by the tag and cannot be edited on its own.
 * Base64 is the standard alphabet with padding: <code>+ / =</code> all survive inside <code>${...}</code>,
 * and the one character a payload may not contain - <code>}</code> - base64 never produces.
 * </p>
 * <p>
 * <b>Why the iteration count is in the token.</b> It is an output and not a knob: the tool writes it, no
 * syntax offers it to whoever edits the file, and a token that carries one below {@link #MINIMUM_ITERATIONS}
 * is refused rather than honoured, so it cannot be used to weaken anything. What it buys is that raising
 * {@link #DEFAULT_ITERATIONS} as guidance moves never orphans a file already written - which is the whole
 * cost a fixed count would have had, since a token gives the reader nothing to derive with otherwise.
 * </p>
 *
 * <h2>What the construction is for</h2>
 * <ul>
 *   <li><b>A random IV per value</b>, so that two equal secrets do not produce equal cipher text. This is
 *   what the AES/ECB example this library used to publish got wrong, and it is visible from outside: a
 *   file discloses which of its secrets are the same.</li>
 *   <li><b>GCM</b>, so that an edited value fails loudly instead of decrypting to something else.</li>
 *   <li><b>PBKDF2 over a passphrase of any length</b>, which is what removes the padding-to-16-characters
 *   the old example forced on a key.</li>
 * </ul>
 *
 * <h2>Deriving once, not per value</h2>
 * <p>
 * A per-value salt would mean a per-value key derivation, and at 210,000 iterations ten encrypted
 * properties would be ten derivations every time a configuration is created. The way out is not a weaker
 * salt: the encrypting tool uses <b>one random salt per invocation</b>, so the values it writes in one run
 * share it, and this class caches the derived key by salt and count. A salt shared by the values of one
 * file is still unique to that file and that deployment, which is what a salt is for, while the IV stays
 * per value, which is what stops equal secrets looking equal.
 * </p>
 *
 * <h2>Rotation</h2>
 * <p>
 * A handler is registered under a name, so two of them can be registered at once:
 * </p>
 * <pre>
 *     ConfigFactory.registerValueHandler(new AesGcmHandler("aes-gcm-2025", current));
 *     ConfigFactory.registerValueHandler(new AesGcmHandler("aes-gcm-2024", previous));
 * </pre>
 * <p>
 * and a rotation becomes an edit to the file, one value at a time, with both keys readable meanwhile.
 * </p>
 *
 * <h2>Serialization</h2>
 * <p>
 * A Config object is serializable, and this handler is reachable from one. <b>The passphrase and the
 * derived keys are transient</b>: writing them out would put a secret on disk in a file nobody chose to
 * protect. A handler that comes back from deserialization therefore cannot decrypt, and says so.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
public class AesGcmHandler implements ValueHandler, Encrypting {

    private static final long serialVersionUID = 5091737290176155516L;

    /** The name this handler answers to unless another is given. */
    public static final String DEFAULT_NAME = "aes-gcm";

    /**
     * What a value is encrypted with today: OWASP's current guidance for PBKDF2-HMAC-SHA256.
     * <p>
     * Measured before being chosen, since the Java 8 baseline is the case that had to survive it: one
     * derivation at this count costs 38 ms on JDK 24 and 49 ms on JDK 17, and it happens once per salt -
     * which is once per file, not once per value.
     * </p>
     */
    public static final int DEFAULT_ITERATIONS = 210_000;

    /**
     * The lowest count a token may carry and still be read.
     * <p>
     * The count travels in the token so that the default can be raised without orphaning what is already
     * written. This is the other half of that: a token asking to be read with a derisory count is refused,
     * so the field cannot be edited into a weakness.
     * </p>
     */
    public static final int MINIMUM_ITERATIONS = 100_000;

    private static final String KDF = "PBKDF2WithHmacSHA256";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int HEADER_BYTES = 4 + SALT_BYTES + IV_BYTES;
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /** How many derived keys to keep. One file uses one salt; the cap is there so a wrong one cannot grow. */
    private static final int CACHE_SIZE = 16;

    private final String name;
    private final int iterations;

    /** See the note on serialization: a passphrase is not written out with the object holding it. */
    private final transient char[] passphrase;

    private final transient SecureRandom random;

    /** Derived keys by count and salt, so the values of one file cost one derivation between them. */
    private final transient Map<String, SecretKey> keys;

    /**
     * @param passphrase the passphrase, of any length, copied rather than kept: the caller stays free to
     *                   blank its own array afterwards.
     */
    public AesGcmHandler(char[] passphrase) {
        this(DEFAULT_NAME, passphrase, DEFAULT_ITERATIONS);
    }

    /**
     * @param passphrase the passphrase. A <code>String</code> cannot be blanked once it exists, so prefer
     *                   {@link #AesGcmHandler(char[])} when the passphrase is read rather than written.
     */
    public AesGcmHandler(String passphrase) {
        this(DEFAULT_NAME, passphrase == null ? null : passphrase.toCharArray(), DEFAULT_ITERATIONS);
    }

    /**
     * @param name       the name values refer to this handler by, which is where a key rotation goes.
     * @param passphrase the passphrase, of any length.
     */
    public AesGcmHandler(String name, char[] passphrase) {
        this(name, passphrase, DEFAULT_ITERATIONS);
    }

    /**
     * @param name       the name values refer to this handler by.
     * @param passphrase the passphrase, of any length.
     * @param iterations the count to <b>encrypt</b> with. Decryption uses the count in the token, so this
     *                   changes what is written from now on and not what can be read.
     * @throws IllegalArgumentException if the passphrase is null or empty, or the count is below
     *                                  {@link #MINIMUM_ITERATIONS}.
     */
    public AesGcmHandler(String name, char[] passphrase, int iterations) {
        if (passphrase == null || passphrase.length == 0)
            throw new IllegalArgumentException("the passphrase can't be null or empty");
        if (iterations < MINIMUM_ITERATIONS)
            throw new IllegalArgumentException(String.format(
                    "%d iterations is below the %d this handler accepts. The count is what makes a "
                            + "passphrase expensive to guess, and a low one is worth less than it looks.",
                    iterations, MINIMUM_ITERATIONS));
        this.name = name;
        this.iterations = iterations;
        this.passphrase = passphrase.clone();
        this.random = new SecureRandom();
        this.keys = new LinkedHashMap<String, SecretKey>(CACHE_SIZE, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, SecretKey> eldest) {
                return size() > CACHE_SIZE;
            }
        };
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * Decrypts a token, which is the payload of the marker naming this handler.
     *
     * @throws IllegalArgumentException if the token is not one, was encrypted with another passphrase, has
     *                                  been edited, or asks to be read with too low an iteration count.
     */
    @Override
    public String resolve(String payload) {
        requireUsable();
        byte[] token = decode(payload);
        if (token.length < HEADER_BYTES + TAG_BITS / 8)
            throw notAToken("it is " + token.length + " bytes, too short to hold a header and a tag");

        ByteBuffer buffer = ByteBuffer.wrap(token);
        int tokenIterations = buffer.getInt();
        if (tokenIterations < MINIMUM_ITERATIONS)
            throw notAToken(String.format("it asks to be read with %d iterations, below the %d this "
                    + "handler accepts", tokenIterations, MINIMUM_ITERATIONS));
        byte[] salt = new byte[SALT_BYTES];
        buffer.get(salt);
        byte[] iv = new byte[IV_BYTES];
        buffer.get(iv);

        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, keyFor(salt, tokenIterations), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(token, 0, HEADER_BYTES);
            byte[] plain = cipher.doFinal(token, HEADER_BYTES, token.length - HEADER_BYTES);
            return new String(plain, UTF_8);
        } catch (GeneralSecurityException e) {
            // deliberately vague about which of the two it was: telling a wrong passphrase apart from an
            // edited value tells whoever is editing which one they got closer to
            throw new IllegalArgumentException("A value handled by '" + name + "' could not be decrypted. "
                    + "Either the passphrase is not the one it was encrypted with, or the value has been "
                    + "changed since - GCM answers with a failure rather than with different text, which "
                    + "is the point of it.", e);
        }
    }

    /**
     * Encrypts a value, producing the token that goes inside a marker. Each call draws a fresh salt and a
     * fresh IV.
     * <p>
     * A tool encrypting several values in one run should reuse one salt across them, which is what
     * {@link #encryptAll} is for: it is the difference between one key derivation and one per value.
     * </p>
     *
     * @param plainText the value to encrypt.
     * @return the base64 token.
     */
    public String encrypt(String plainText) {
        return encryptWith(newSalt(), plainText);
    }

    /**
     * Encrypts several values under <b>one salt</b>, so that reading them back costs one key derivation
     * between them. Each value still gets its own IV.
     *
     * @param plainTexts the values to encrypt.
     * @return the base64 tokens, in the same order.
     */
    @Override
    public String[] encryptAll(String... plainTexts) {
        byte[] salt = newSalt();
        String[] tokens = new String[plainTexts.length];
        for (int i = 0; i < plainTexts.length; i++)
            tokens[i] = encryptWith(salt, plainTexts[i]);
        return tokens;
    }

    /**
     * The whole marker for a value, which is what gets pasted into a configuration file:
     * <code>${$aes-gcm::...}</code>.
     *
     * @param plainText the value to encrypt.
     * @return the marker, ready to be written as a property value.
     */
    public String markerFor(String plainText) {
        return marker(encrypt(plainText));
    }

    /** The marker wrapping an already encrypted token. */
    @Override
    public String marker(String token) {
        return "${$" + name + "::" + token + "}";
    }

    /**
     * Refuses a handler that came back from deserialization, which is one without a passphrase - see the
     * note on serialization. Saying it here is the difference between an explanation and a
     * <code>NullPointerException</code> somewhere inside the key derivation.
     */
    private void requireUsable() {
        if (passphrase == null)
            throw new IllegalArgumentException("This " + getClass().getSimpleName() + " came back from "
                    + "deserialization, and a passphrase is deliberately not serialized with the object "
                    + "holding it. Register a handler built with the passphrase before reading the "
                    + "configuration.");
    }

    private String encryptWith(byte[] salt, String plainText) {
        requireUsable();
        if (plainText == null)
            throw new IllegalArgumentException("there is nothing to encrypt: the value is null");
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        byte[] header = ByteBuffer.allocate(HEADER_BYTES).putInt(iterations).put(salt).put(iv).array();
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, keyFor(salt, iterations), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(header);
            byte[] sealed = cipher.doFinal(plainText.getBytes(UTF_8));
            byte[] token = ByteBuffer.allocate(header.length + sealed.length).put(header).put(sealed).array();
            return Base64.getEncoder().encodeToString(token);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Could not encrypt a value with '" + name + "': "
                    + e.getMessage(), e);
        }
    }

    private byte[] newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return salt;
    }

    /**
     * The key for a salt and a count, derived once. Synchronized rather than concurrent because the map is
     * also an LRU and because the thing being guarded is the derivation, which happens once per file.
     */
    private SecretKey keyFor(byte[] salt, int count) throws GeneralSecurityException {
        String cacheKey = count + ":" + Base64.getEncoder().encodeToString(salt);
        synchronized (keys) {
            SecretKey cached = keys.get(cacheKey);
            if (cached != null)
                return cached;
        }
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, count, KEY_BITS);
        try {
            SecretKey derived = new SecretKeySpec(
                    SecretKeyFactory.getInstance(KDF).generateSecret(spec).getEncoded(), "AES");
            synchronized (keys) {
                keys.put(cacheKey, derived);
            }
            return derived;
        } finally {
            spec.clearPassword();
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
                "A value refers to '%s' but what follows the :: is not a token this handler wrote: %s.",
                name, why));
    }

    /** The passphrase is not part of what a handler is, and two of them are told apart by name. */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        AesGcmHandler that = (AesGcmHandler) other;
        return iterations == that.iterations && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + iterations;
    }

    /** Never the passphrase, which is the whole reason this is written rather than inherited. */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[name=" + name + ", iterations=" + iterations + "]";
    }
}
