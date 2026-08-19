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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
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

    /**
     * The length of the wrapped key is a field in the token, and it says how much of what follows is the
     * key. It is inside the authenticated header, so it cannot be edited and still decrypt - but it is read
     * <b>before</b> anything is authenticated, because it is what says where the rest begins. So it has to
     * be checked against what is actually there: a length of zero, or one longer than the token, must be
     * refused with a sentence rather than reaching a buffer and coming back as a
     * <code>BufferUnderflowException</code> from inside the library.
     */
    @Test
    public void aWrappedKeyLengthThatDoesNotFitIsRefusedAndNotFollowed() {
        RsaHandler handler = both();
        byte[] original = Base64.getDecoder().decode(handler.encrypt("s3cr3t"));

        for (int[] length : new int[][]{{0, 0}, {0xff, 0xff}}) {
            byte[] token = original.clone();
            token[4] = (byte) length[0]; // the two bytes after the four of the fingerprint
            token[5] = (byte) length[1];
            try {
                handler.resolve(Base64.getEncoder().encodeToString(token));
                fail("a wrapped key of " + (length[0] << 8 | length[1]) + " bytes does not fit here");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("does not fit"));
            }
        }
    }

    @Test
    public void encryptingNullIsRefusedRatherThanEncryptingTheWordNull() {
        try {
            new RsaHandler(pair.getPublic()).encrypt(null);
            fail("null is not a value to encrypt");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("nothing to encrypt"));
        }
    }

    /**
     * The other handler shares a salt between the values of one run; this one shares nothing, because
     * there is no passphrase to derive from. What is being pinned down is that every value still comes
     * back, and that two equal values encrypted together are still not equal on disk.
     */
    @Test
    public void severalValuesEncryptedInOneRunEachGetTheirOwnKey() {
        RsaHandler handler = both();
        String[] tokens = handler.encryptAll("one", "two", "two");
        assertEquals(3, tokens.length);
        assertEquals("one", handler.resolve(tokens[0]));
        assertEquals("two", handler.resolve(tokens[1]));
        assertEquals("two", handler.resolve(tokens[2]));
        assertNotEquals("nothing is shared between the values of one run", tokens[1], tokens[2]);
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
     * that {@link RsaHandler} refuses one. It is the clearest example of why the whole of
     * <code>src/test/java</code> is outside what CodeQL scans - see
     * <code>.github/codeql/codeql-config.yml</code> - since a scanner cannot tell a weak key being used
     * from a weak key being rejected, and this one exists only to prove the second.
     */
    @Test
    public void aKeyTooSmallToBeWorthUsingIsRefused() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
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
            RsaHandler.privateKeyFrom(pem("PRIVATE KEY", "not a key at all".getBytes()));
            fail("that is not a PKCS#8 private key");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("openssl genpkey"));
        }
        try {
            RsaHandler.privateKeyFrom("   ");
            fail("there is nothing there");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("empty"));
        }
    }

    /**
     * <code>null</code> is what a file that was never read hands over, and it reaches both readers before
     * either of them has looked at the text. Both have a <code>contains</code> on the way in - for the
     * certificate shape and for the PKCS#1 one - so both would answer with a
     * <code>NullPointerException</code> if the null were not dealt with first.
     */
    @Test
    public void nullTextIsRefusedAsEmptyRatherThanThrowingFromInsideTheReader() {
        try {
            RsaHandler.publicKeyFrom((String) null);
            fail("there is no key in nothing");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("empty"));
        }
        try {
            RsaHandler.privateKeyFrom((String) null);
            fail("there is no key in nothing");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("empty"));
        }
    }

    /**
     * PEM arrives pasted, and pasted text has blank lines in it. They are skipped like the header lines
     * are, so a key that made the trip through a chat window or a ticket still reads.
     */
    @Test
    public void blankLinesInsideThePemAreSkippedLikeTheHeaderLinesAre() {
        String written = pem("PUBLIC KEY", pair.getPublic().getEncoded());
        String pasted = "\n" + written.replaceFirst("\n", "\n\n   \n");
        assertEquals(pair.getPublic(), RsaHandler.publicKeyFrom(pasted));
    }

    @Test
    public void aPemBodyThatIsNotBase64SaysThatIsWhatIsWrongWithIt() {
        try {
            RsaHandler.publicKeyFrom("-----BEGIN PUBLIC KEY-----\nnot base64 !!!\n-----END PUBLIC KEY-----");
            fail("the body of that block is not base64");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("not base64"));
        }
    }

    /**
     * A key that is not RSA at all - here an elliptic curve one, which is the other thing a
     * <code>PUBLIC KEY</code> block commonly holds - is refused by name, and pointed at the handler that
     * would have taken it. It parses perfectly well; it is simply not what this cipher is.
     */
    @Test
    public void aKeyThatIsNotRsaIsRefusedByNameAndNotByFailingLater() throws Exception {
        java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        try {
            new RsaHandler(generator.generateKeyPair().getPublic());
            fail("an EC key is not an RSA key");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("is not RSA"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("AesGcmHandler"));
        }
    }

    // --- key material in files ----------------------------------------------------------------------

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    /** The shape of the two documented calls: a deployment names a file, not a string it built. */
    @Test
    public void bothKeysAreReadFromAFileOnDisk() throws Exception {
        File publicPem = folder.newFile("app.pub");
        File privatePem = folder.newFile("app.key");
        write(publicPem, pem("PUBLIC KEY", pair.getPublic().getEncoded()));
        write(privatePem, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));

        RsaHandler writer = new RsaHandler(RsaHandler.publicKeyFrom(publicPem.toPath()));
        RsaHandler reader = new RsaHandler(RsaHandler.privateKeyFrom(privatePem.toPath()));
        assertEquals("s3cr3t", reader.resolve(writer.encrypt("s3cr3t")));
    }

    /** A path that is not there is the commonest mistake of the two, and the message has to name it. */
    @Test
    public void aFileThatIsNotThereIsReportedWithItsPath() {
        Path missing = new File(folder.getRoot(), "no-such.key").toPath();
        try {
            RsaHandler.privateKeyFrom(missing);
            fail("that file does not exist");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("no-such.key"));
        }
    }

    // --- a key inside a certificate -----------------------------------------------------------------

    /**
     * What a keystore hands out is a certificate, not a bare public key, and the two are different DER.
     * The fixtures beside this test are the same 3072-bit key written both ways, so what is pinned down is
     * that the certificate path yields <b>the same key</b> as the plain one - which is the only thing that
     * makes a keystore export usable here at all.
     * <p>
     * There is no private key among the fixtures: none is needed to prove this, and a private key is not a
     * thing to keep in a repository even as a throwaway.
     * </p>
     */
    @Test
    public void thePublicKeyInsideACertificateIsTheSameKeyAsTheBareOne() {
        PublicKey fromCertificate = RsaHandler.publicKeyFrom(fixture("keystore-export.crt"));
        PublicKey fromPublicKeyBlock = RsaHandler.publicKeyFrom(fixture("keystore-export.pub"));
        assertEquals(fromPublicKeyBlock, fromCertificate);
        assertEquals("RSA", fromCertificate.getAlgorithm());
        // and it is a key this handler will take, which is the point of reading it
        assertTrue(new RsaHandler(fromCertificate).markerFor("s3cr3t").startsWith("${$rsa-oaep::"));
    }

    @Test
    public void aCertificateBlockThatIsNotOneIsRefusedAsACertificate() {
        try {
            RsaHandler.publicKeyFrom(pem("CERTIFICATE", "not a certificate at all".getBytes()));
            fail("that is not a certificate");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("certificate could not be read"));
        }
    }

    private static String fixture(String name) {
        try (InputStream in = RsaHandlerTest.class.getResourceAsStream(name)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int read; (read = in.read(buffer)) != -1; )
                bytes.write(buffer, 0, read);
            return new String(bytes.toByteArray(), "UTF-8");
        } catch (Exception e) {
            throw new IllegalStateException("the fixture " + name + " is missing", e);
        }
    }

    private static void write(File file, String text) throws Exception {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes("UTF-8"));
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

    /**
     * Which half a handler holds is the thing worth reading off a <code>toString</code>, because holding
     * the wrong one is the failure this design produces: a deployment registered with the public key looks
     * exactly like one registered properly until a value is read.
     */
    @Test
    public void toStringSaysWhichHalfTheHandlerIsHolding() {
        String writer = new RsaHandler(pair.getPublic()).toString();
        assertTrue(writer, writer.contains("canEncrypt=true"));
        assertTrue(writer, writer.contains("canDecrypt=false"));

        String reader = new RsaHandler(pair.getPrivate()).toString();
        assertTrue(reader, reader.contains("canEncrypt=false"));
        assertTrue(reader, reader.contains("canDecrypt=true"));
    }

    /**
     * A handler is its name and its key pair, and <b>not</b> the halves it happens to hold: the writer with
     * the public key and the reader with the private key of one pair are the same handler under two roofs,
     * which is what makes registering a name again a rotation and not a second entry.
     */
    @Test
    public void aHandlerIsToldApartByItsNameAndItsKeyPairAndNeverByWhichHalfItHolds() {
        RsaHandler writer = new RsaHandler("rsa-2025", pair.getPublic(), null);
        RsaHandler reader = new RsaHandler("rsa-2025", null, pair.getPrivate());
        assertEquals(writer, reader);
        assertEquals(writer.hashCode(), reader.hashCode());

        assertNotEquals("another name", writer, new RsaHandler("rsa-2024", pair.getPublic(), null));
        assertNotEquals("another key pair", writer, new RsaHandler("rsa-2025", another.getPublic(), null));
        // hoisted into locals so that the analyser stops reading them as mistyped comparisons and
        // suggesting assertNotEquals, which would call String.equals or skip the call altogether:
        // what is under test is this object's own equals, given those two arguments
        boolean equalToNull = writer.equals(null);
        boolean equalToAString = writer.equals("not a handler at all");
        assertFalse("a handler is equal to nothing else", equalToNull);
        assertFalse("nor to something of another type", equalToAString);
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

    // --- a certificate outside its dates ------------------------------------------------------------

    /**
     * An expired certificate still yields a usable key, and the library says so rather than refusing.
     * <p>
     * The dates on a certificate assert an identity binding, and nothing here is trusting an identity —
     * the caller handed over the file. But a certificate past its date usually means the key pair was
     * rotated, and encrypting to a public key whose private half the deployment no longer holds fails
     * there rather than here. The fixture was written by openssl with explicit dates in 2024.
     * </p>
     */
    @Test
    public void anExpiredCertificateIsUsedAndReported() throws Exception {
        StringBuilder captured = new StringBuilder();
        Logger logger = Logger.getLogger(RsaHandler.class.getName());
        Handler listener = new Handler() {
            @Override public void publish(LogRecord record) { captured.append(record.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(listener);
        try {
            PublicKey key = RsaHandler.publicKeyFrom(read("expired.crt"));
            assertEquals("the key is perfectly usable", "RSA", key.getAlgorithm());
            assertTrue(captured.toString(), captured.toString().contains("expired on"));
            assertTrue(captured.toString(), captured.toString().contains("rotated-last-year"));
        } finally {
            logger.removeHandler(listener);
        }
    }

    private static String read(String name) throws IOException {
        try (InputStream in = RsaHandlerTest.class.getResourceAsStream(name)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n = in.read(buffer); n >= 0; n = in.read(buffer)) bytes.write(buffer, 0, n);
            return new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
