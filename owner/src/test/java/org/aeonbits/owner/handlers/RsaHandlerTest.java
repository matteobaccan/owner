/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.handlers;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The asymmetric cipher, and above all the property it exists for: whoever can write a value cannot read
 * the ones already there.
 *
 * @author Matteo Baccan
 */
public class RsaHandlerTest {

    /** Generated once: a 2048-bit key pair is not cheap, and every test here wants the same one. */
    private static KeyPair pair;
    private static KeyPair another;

    @BeforeClass
    public static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pair = generator.generateKeyPair();
        another = generator.generateKeyPair();
    }

    private RsaHandler both() {
        return new RsaHandler(pair.getPublic(), pair.getPrivate());
    }

    @Test
    public void aValueSurvivesTheRoundTrip() {
        RsaHandler handler = both();
        assertEquals("s3cr3t", handler.resolve(handler.encrypt("s3cr3t")));
    }

    @Test
    public void theEmptyStringAndTextThatIsNotAsciiSurviveToo() {
        RsaHandler handler = both();
        assertEquals("", handler.resolve(handler.encrypt("")));
        String value = "passphrase con àccenti, 日本語 and an emoji 🔐";
        assertEquals(value, handler.resolve(handler.encrypt(value)));
    }

    /**
     * The whole reason this handler exists: the public key writes, the private key reads, and neither
     * does the other's job.
     */
    @Test
    public void whoeverCanWriteAValueCannotReadOne() {
        RsaHandler writer = new RsaHandler(pair.getPublic());
        RsaHandler reader = new RsaHandler(pair.getPrivate());

        String token = writer.encrypt("s3cr3t");
        assertEquals("the deployment reads it", "s3cr3t", reader.resolve(token));

        try {
            writer.resolve(token);
            fail("a handler holding only the public key must not be able to read");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("only a public key"));
        }
    }

    @Test
    public void andWhoeverCanReadOneIsToldWhyItCannotWrite() {
        try {
            new RsaHandler(pair.getPrivate()).encrypt("s3cr3t");
            fail("a handler holding only the private key has nothing to encrypt to");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("only a private key"));
        }
    }

    /**
     * Two values encrypted to the same public key must not look alike: a fresh AES key and a fresh IV per
     * value is what makes that true, and RSA-OAEP is randomised as well.
     */
    @Test
    public void twoEqualSecretsDoNotProduceEqualCipherText() {
        RsaHandler handler = both();
        assertNotEquals(handler.encrypt("same"), handler.encrypt("same"));
    }

    /**
     * The mistake this design invites: encrypting against a public key that is not the deployment's.
     * Whoever makes it cannot decrypt what they wrote, so they cannot notice — which is why the token
     * carries a fingerprint and the failure names both key pairs.
     */
    @Test
    public void aTokenWrittenForAnotherKeyPairSaysSoRatherThanFailingToDecrypt() {
        String token = new RsaHandler(another.getPublic()).encrypt("s3cr3t");
        try {
            new RsaHandler(pair.getPrivate()).resolve(token);
            fail("that token belongs to another key pair");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("written for the key pair"));
            assertFalse("and not the generic failure",
                    expected.getMessage().contains("could not be decrypted"));
        }
    }

    @Test
    public void anEditedValueIsRefusedRatherThanDecryptedToSomethingElse() {
        RsaHandler handler = both();
        byte[] token = Base64.getDecoder().decode(handler.encrypt("s3cr3t"));
        token[token.length - 1] ^= 0x01;
        try {
            handler.resolve(Base64.getEncoder().encodeToString(token));
            fail("an edited token should not decrypt");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("could not be decrypted"));
        }
    }

    /** The wrapped key is inside the authenticated header, so editing it fails on the tag as well. */
    @Test
    public void anEditedWrappedKeyIsRefusedToo() {
        RsaHandler handler = both();
        byte[] token = Base64.getDecoder().decode(handler.encrypt("s3cr3t"));
        token[40] ^= 0x01; // inside the wrapped key
        try {
            handler.resolve(Base64.getEncoder().encodeToString(token));
            fail("an edited wrapped key should not decrypt");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("could not be decrypted"));
        }
    }

    @Test
    public void whatIsNotATokenIsSaidToBeOne() {
        RsaHandler handler = both();
        for (String notAToken : new String[]{"", "not base64 at all !!", "c2hvcnQ="}) {
            try {
                handler.resolve(notAToken);
                fail("'" + notAToken + "' is not a token");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("not a token"));
            }
        }
    }

    /** RSA can encrypt about 190 bytes; the hybrid construction is what removes that ceiling. */
    @Test
    public void aValueLongerThanRsaCouldEverEncryptStillWorks() {
        StringBuilder large = new StringBuilder();
        for (int i = 0; i < 500; i++)
            large.append("a certificate is longer than a password. ");
        RsaHandler handler = both();
        assertEquals(large.toString(), handler.resolve(handler.encrypt(large.toString())));
    }

    /**
     * The 1024-bit key below is the subject of the test and not a mistake in it: what is being checked is
     * that {@link RsaHandler} refuses one. A scanner cannot tell a weak key being used from a weak key
     * being rejected, so the suppression says which this is rather than leaving a high-severity alert
     * standing on a security feature, where a real one would then be one row among the noise.
     */
    @Test
    public void aKeyTooSmallToBeWorthUsingIsRefused() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024); // codeql[java/insufficient-key-size]
        try {
            new RsaHandler(generator.generateKeyPair().getPublic());
            fail("1024 bits stopped being recommended in 2010");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("1024"));
        }
    }

    @Test
    public void twoKeysThatAreNotAPairAreRefused() {
        try {
            new RsaHandler(pair.getPublic(), another.getPrivate());
            fail("those are not two halves of one key pair");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("key pair"));
        }
    }

    @Test
    public void aHandlerWithNeitherKeyIsRefused() {
        try {
            new RsaHandler(null, null);
            fail("a handler with neither key can do nothing");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("neither key"));
        }
    }

    // --- reading key material -----------------------------------------------------------------------

    private static String pem(String label, byte[] der) {
        StringBuilder text = new StringBuilder("-----BEGIN ").append(label).append("-----\n");
        String base64 = Base64.getEncoder().encodeToString(der);
        for (int i = 0; i < base64.length(); i += 64)
            text.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        return text.append("-----END ").append(label).append("-----\n").toString();
    }

    @Test
    public void aPemPublicKeyIsRead() {
        String written = pem("PUBLIC KEY", pair.getPublic().getEncoded());
        assertEquals(pair.getPublic(), RsaHandler.publicKeyFrom(written));
    }

    @Test
    public void aPemPrivateKeyIsRead() {
        String written = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
        RsaHandler reader = new RsaHandler(RsaHandler.privateKeyFrom(written));
        assertEquals("s3cr3t", reader.resolve(new RsaHandler(pair.getPublic()).encrypt("s3cr3t")));
    }

    @Test
    public void bareBase64WithoutTheHeaderLinesIsReadToo() {
        String bare = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        assertEquals(pair.getPublic(), RsaHandler.publicKeyFrom(bare));
    }

    /** The one format the JDK cannot read, refused with the command that converts it. */
    @Test
    public void aPkcs1PrivateKeyIsRefusedWithTheCommandThatConvertsIt() {
        try {
            RsaHandler.privateKeyFrom(pem("RSA PRIVATE KEY", pair.getPrivate().getEncoded()));
            fail("PKCS#1 is not readable by the JDK");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("PKCS#1"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("openssl pkcs8 -topk8"));
        }
    }

    @Test
    public void whatIsNotAKeyIsRefusedWithHowToMakeOne() {
        try {
            RsaHandler.publicKeyFrom(pem("PUBLIC KEY", "not a key at all".getBytes()));
            fail("that is not an X.509 public key");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("openssl"));
        }
        try {
            RsaHandler.privateKeyFrom("   ");
            fail("there is nothing there");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("empty"));
        }
    }

    // --- the rest -----------------------------------------------------------------------------------

    @Test
    public void neitherToStringNorAnErrorEverShowsKeyMaterial() {
        RsaHandler handler = both();
        String secretMaterial = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        assertFalse(handler.toString(), handler.toString().contains(secretMaterial));
        assertTrue(handler.toString(), handler.toString().contains("canDecrypt=true"));
    }

    /** A Config object is serializable and a handler is reachable from one: the private key must not go. */
    @Test
    public void aPrivateKeyIsNotWrittenOutWithTheObjectHoldingIt() throws Exception {
        RsaHandler handler = both();
        String token = handler.encrypt("s3cr3t");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(handler);
        }

        RsaHandler back;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            back = (RsaHandler) in.readObject();
        }
        try {
            back.resolve(token);
            fail("a deserialized handler has no private key and should say so");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("deserialization"));
        }
        assertEquals("but the public key is public, so it can still write",
                "s3cr3t", handler.resolve(back.encrypt("s3cr3t")));
    }

    public interface Encrypted extends Config {
        String password();

        String url();
    }

    /** End to end: what a CI job writes with the public key, and what the deployment reads. */
    @Test
    public void aConfigurationReadsAValueWrittenByWhoeverCouldNotHaveReadIt() {
        RsaHandler writer = new RsaHandler(pair.getPublic());
        ConfigFactory.registerValueHandler(new RsaHandler(pair.getPrivate()));

        Encrypted cfg = ConfigFactory.create(Encrypted.class, new HashMap<String, String>() {{
            put("password", writer.markerFor("s3cr3t"));
            put("url", "jdbc:h2:mem:test?password=${password}");
        }});
        assertEquals("s3cr3t", cfg.password());
        assertEquals("jdbc:h2:mem:test?password=s3cr3t", cfg.url());
    }

    @Test
    public void twoKeyPairsCanBeReadableAtOnceWhileARotationIsUnderWay() {
        ConfigFactory.registerValueHandler(new RsaHandler("rsa-2024", null, another.getPrivate()));
        ConfigFactory.registerValueHandler(new RsaHandler("rsa-2025", null, pair.getPrivate()));

        Rotating cfg = ConfigFactory.create(Rotating.class, new HashMap<String, String>() {{
            put("old", new RsaHandler("rsa-2024", another.getPublic(), null).markerFor("still readable"));
            put("new", new RsaHandler("rsa-2025", pair.getPublic(), null).markerFor("already moved"));
        }});
        assertEquals("still readable", cfg.old());
        assertEquals("already moved", cfg.moved());
    }

    public interface Rotating extends Config {
        @Key("old")
        String old();

        @Key("new")
        String moved();
    }
}
