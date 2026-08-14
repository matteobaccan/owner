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
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The cipher, and the properties the AES/ECB example this library used to publish did not have.
 *
 * @author Matteo Baccan
 */
public class AesGcmHandlerTest {

    private static final String PASSPHRASE = "a passphrase of no particular length at all";

    private AesGcmHandler handler() {
        return new AesGcmHandler(PASSPHRASE);
    }

    @Test
    public void aValueSurvivesTheRoundTrip() {
        AesGcmHandler handler = handler();
        assertEquals("s3cr3t", handler.resolve(handler.encrypt("s3cr3t")));
    }

    @Test
    public void theEmptyStringSurvivesToo() {
        AesGcmHandler handler = handler();
        assertEquals("", handler.resolve(handler.encrypt("")));
    }

    @Test
    public void soDoesTextThatIsNotAscii() {
        AesGcmHandler handler = handler();
        String value = "passphrase con àccenti, 日本語 and an emoji 🔐";
        assertEquals(value, handler.resolve(handler.encrypt(value)));
    }

    /**
     * The defect that started the whole thing. The published example was <code>Cipher.getInstance("AES")</code>,
     * which is AES/ECB, so the same plaintext always gave the same cipher text and a properties file
     * disclosed which of its secrets were equal.
     */
    @Test
    public void twoEqualSecretsDoNotProduceEqualCipherText() {
        AesGcmHandler handler = handler();
        assertNotEquals(handler.encrypt("same"), handler.encrypt("same"));
    }

    @Test
    public void andNotEvenWhenTheyShareASalt() {
        String[] tokens = handler().encryptAll("same", "same");
        assertNotEquals(tokens[0], tokens[1]);
    }

    @Test
    public void valuesEncryptedInOneRunShareTheirSalt() {
        String[] tokens = handler().encryptAll("one", "two");
        assertArrayHeaderMatches(tokens[0], tokens[1]);
    }

    /** The first 20 bytes are the iteration count and the salt; the 12 after them are the per-value IV. */
    private static void assertArrayHeaderMatches(String first, String second) {
        byte[] a = Base64.getDecoder().decode(first);
        byte[] b = Base64.getDecoder().decode(second);
        for (int i = 0; i < 20; i++)
            assertEquals("byte " + i + " of the header", a[i], b[i]);
    }

    @Test
    public void aValuesEncryptedInOneRunStillReadBack() {
        AesGcmHandler handler = handler();
        String[] tokens = handler.encryptAll("one", "two", "three");
        assertEquals("one", handler.resolve(tokens[0]));
        assertEquals("two", handler.resolve(tokens[1]));
        assertEquals("three", handler.resolve(tokens[2]));
    }

    @Test
    public void anotherPassphraseCannotRead() {
        String token = handler().encrypt("s3cr3t");
        try {
            new AesGcmHandler("a different passphrase entirely").resolve(token);
            fail("a token should not be readable with another passphrase");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("could not be decrypted"));
        }
    }

    /** What GCM is for: an edited value fails rather than decrypting to something else. */
    @Test
    public void anEditedValueIsRefusedRatherThanDecryptedToSomethingElse() {
        AesGcmHandler handler = handler();
        byte[] token = Base64.getDecoder().decode(handler.encrypt("s3cr3t"));
        token[token.length - 1] ^= 0x01;
        try {
            handler.resolve(Base64.getEncoder().encodeToString(token));
            fail("an edited token should not decrypt");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("could not be decrypted"));
        }
    }

    /** The header is passed to GCM as additional data, so editing the salt fails on the tag as well. */
    @Test
    public void anEditedHeaderIsRefusedToo() {
        AesGcmHandler handler = handler();
        byte[] token = Base64.getDecoder().decode(handler.encrypt("s3cr3t"));
        token[8] ^= 0x01; // inside the salt
        try {
            handler.resolve(Base64.getEncoder().encodeToString(token));
            fail("an edited salt should not decrypt");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("could not be decrypted"));
        }
    }

    @Test
    public void aTokenAskingForTooFewIterationsIsRefused() {
        AesGcmHandler handler = handler();
        byte[] token = Base64.getDecoder().decode(handler.encrypt("s3cr3t"));
        token[0] = 0;
        token[1] = 0;
        token[2] = 0;
        token[3] = 1; // one iteration
        try {
            handler.resolve(Base64.getEncoder().encodeToString(token));
            fail("a token asking to be read with one iteration should be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("iterations"));
        }
    }

    /**
     * The other end of the same guard, and the one that is about availability rather than strength.
     * <p>
     * The count is read out of the token and the key derived from it <b>before</b> GCM can authenticate
     * anything, and because the count is inside the authenticated header an edited one can never
     * authenticate — so the derivation is guaranteed waste, at whatever price the edit names. Half of all
     * four-byte corruptions are negative and refused by the minimum; the other half average around a
     * billion, which was minutes of CPU inside <code>ConfigFactory.create</code> before this refusal
     * existed. The assertion on the clock is the point of the test: it is not that it fails, it is that it
     * fails <em>at once</em>.
     * </p>
     */
    @Test
    public void aTokenAskingForAnAbsurdCountIsRefusedBeforeAnyKeyIsDerived() {
        AesGcmHandler handler = handler();
        byte[] token = Base64.getDecoder().decode(handler.encrypt("s3cr3t"));
        int absurd = AesGcmHandler.MAXIMUM_ITERATIONS + 1;
        token[0] = (byte) (absurd >>> 24);
        token[1] = (byte) (absurd >>> 16);
        token[2] = (byte) (absurd >>> 8);
        token[3] = (byte) absurd;

        long start = System.nanoTime();
        try {
            handler.resolve(Base64.getEncoder().encodeToString(token));
            fail("a token asking for more iterations than the ceiling should be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("above the"));
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertTrue("refused after " + millis + " ms, so a key was derived first", millis < 1_000);
    }

    /** A count nobody can read back is a count nobody should be able to write. */
    @Test
    public void aCountAboveTheCeilingIsRefusedAtConstructionToo() {
        try {
            new AesGcmHandler("aes-gcm", PASSPHRASE.toCharArray(), AesGcmHandler.MAXIMUM_ITERATIONS + 1);
            fail("this would write tokens it could not read back");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("above the"));
        }
    }

    @Test
    public void aCountBelowTheMinimumIsRefusedAtConstruction() {
        try {
            new AesGcmHandler("aes-gcm", PASSPHRASE.toCharArray(), 1000);
            fail("1000 iterations is not a cost worth paying for");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("iterations"));
        }
    }

    /**
     * Raising the count has to leave what is already written readable, which is why the count travels in
     * the token.
     */
    @Test
    public void aTokenWrittenWithOneCountIsReadByAHandlerConfiguredWithAnother() {
        String token = new AesGcmHandler("aes-gcm", PASSPHRASE.toCharArray(), 100_000).encrypt("s3cr3t");
        AesGcmHandler later = new AesGcmHandler("aes-gcm", PASSPHRASE.toCharArray(), 400_000);
        assertEquals("s3cr3t", later.resolve(token));
    }

    @Test
    public void whatIsNotATokenIsSaidToBeOne() {
        for (String notAToken : new String[]{"", "not base64 at all !!", "c2hvcnQ="}) {
            try {
                handler().resolve(notAToken);
                fail("'" + notAToken + "' is not a token");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("not a token"));
            }
        }
    }

    @Test
    public void anEmptyPassphraseIsRefused() {
        try {
            new AesGcmHandler("");
            fail("an empty passphrase is not a passphrase");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("passphrase"));
        }
    }

    /**
     * The <code>String</code> constructor exists because a passphrase read from the environment is one,
     * and <code>System.getenv</code> answers <code>null</code> when the variable is not set. That is the
     * shape the mistake arrives in, so it has to be refused where it is made rather than turned into a
     * <code>NullPointerException</code> inside the key derivation.
     */
    @Test
    public void aPassphraseThatIsNullIsRefusedInBothConstructors() {
        try {
            new AesGcmHandler((String) null);
            fail("null is not a passphrase");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("passphrase"));
        }
        try {
            new AesGcmHandler((char[]) null);
            fail("null is not a passphrase");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("passphrase"));
        }
    }

    /** There is nothing to encrypt, and the empty token that would come back is not the answer. */
    @Test
    public void encryptingNullIsRefusedRatherThanEncryptingTheWordNull() {
        try {
            handler().encrypt(null);
            fail("null is not a value to encrypt");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("nothing to encrypt"));
        }
    }

    @Test
    public void thePassphraseIsCopiedSoTheCallerCanBlankItsOwn() {
        char[] mine = PASSPHRASE.toCharArray();
        AesGcmHandler handler = new AesGcmHandler(mine);
        String token = handler.encrypt("s3cr3t");
        java.util.Arrays.fill(mine, '\0');
        assertEquals("s3cr3t", handler.resolve(token));
    }

    @Test
    public void neitherToStringNorAnErrorEverShowsThePassphrase() {
        AesGcmHandler handler = handler();
        assertFalse(handler.toString(), handler.toString().contains(PASSPHRASE));
        try {
            handler.resolve("not base64 at all !!");
            fail("should have refused");
        } catch (IllegalArgumentException expected) {
            assertFalse(expected.getMessage(), expected.getMessage().contains(PASSPHRASE));
        }
    }

    /**
     * The derived keys are cached by salt so that one file costs one derivation, and the cache is capped so
     * that a handler fed many salts cannot grow without bound. What the cap must not do is lose a value:
     * the seventeenth salt evicts the first, and the token written under the first still reads back - it is
     * derived again. Sixteen is the cap in {@link AesGcmHandler}, so seventeen salts is the smallest number
     * that evicts anything.
     * <p>
     * At the minimum count rather than the default, because this is the one test here that pays for a
     * key derivation eighteen times and the property under test is the eviction, not the cost of it.
     * </p>
     */
    @Test
    public void anEvictedKeyIsDerivedAgainRatherThanLost() {
        AesGcmHandler handler = new AesGcmHandler("aes-gcm", PASSPHRASE.toCharArray(),
                AesGcmHandler.MINIMUM_ITERATIONS);
        String[] tokens = new String[17];
        for (int i = 0; i < tokens.length; i++)
            tokens[i] = handler.encrypt("value " + i); // a fresh salt each time, so a fresh cache entry
        assertEquals("value 0", handler.resolve(tokens[0]));
        assertEquals("value 16", handler.resolve(tokens[16]));
    }

    /**
     * Two handlers are the same handler when they answer to the same name and write at the same count. The
     * passphrase is deliberately not part of it: registering a name again with another passphrase is how a
     * key rotation is done, and it has to replace what was there rather than sit beside it.
     */
    @Test
    public void aHandlerIsToldApartByItsNameAndItsCountAndNeverByItsPassphrase() {
        AesGcmHandler one = new AesGcmHandler("aes-gcm-2025", "one passphrase".toCharArray());
        AesGcmHandler other = new AesGcmHandler("aes-gcm-2025", "quite another".toCharArray());
        assertEquals(one, other);
        assertEquals(one.hashCode(), other.hashCode());

        assertNotEquals(one, new AesGcmHandler("aes-gcm-2024", "one passphrase".toCharArray()));
        assertNotEquals(one, new AesGcmHandler("aes-gcm-2025", "one passphrase".toCharArray(), 400_000));
        // asserted on the boolean rather than through assertNotEquals: comparing a handler with a
        // String is the contract being checked, and an assertion framework reads it as a mistake
        assertFalse("a handler is equal to nothing else", one.equals(null));
        assertFalse("nor to something of another type", one.equals("not a handler at all"));
    }

    /** A Config object is serializable and a handler is reachable from one: the passphrase must not go. */
    @Test
    public void aPassphraseIsNotWrittenOutWithTheObjectHoldingIt() throws Exception {
        AesGcmHandler handler = handler();
        String token = handler.encrypt("s3cr3t");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(handler);
        }
        assertFalse("the passphrase reached the serialized form",
                new String(bytes.toByteArray(), "ISO-8859-1").contains(PASSPHRASE));

        AesGcmHandler back;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            back = (AesGcmHandler) in.readObject();
        }
        try {
            back.resolve(token);
            fail("a deserialized handler has no passphrase and should say so");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("deserialization"));
        }
    }

    public interface Encrypted extends Config {
        String password();

        String url();
    }

    /** The whole point, end to end: what a user writes in a file and what the configuration answers. */
    @Test
    public void aConfigurationReadsAnEncryptedValueAndTheValuesThatReferToIt() {
        AesGcmHandler handler = handler();
        ConfigFactory.registerValueHandler(handler);
        Encrypted cfg = ConfigFactory.create(Encrypted.class, new java.util.HashMap<String, String>() {{
            put("password", handler.markerFor("s3cr3t"));
            put("url", "jdbc:h2:mem:test?password=${password}");
        }});
        assertEquals("s3cr3t", cfg.password());
        assertEquals("jdbc:h2:mem:test?password=s3cr3t", cfg.url());
    }

    @Test
    public void twoKeysCanBeReadableAtOnceWhileARotationIsUnderWay() {
        AesGcmHandler previous = new AesGcmHandler("aes-gcm-2024", "the old passphrase".toCharArray());
        AesGcmHandler current = new AesGcmHandler("aes-gcm-2025", "the new passphrase".toCharArray());
        ConfigFactory.registerValueHandler(previous);
        ConfigFactory.registerValueHandler(current);

        Rotating cfg = ConfigFactory.create(Rotating.class, new java.util.HashMap<String, String>() {{
            put("old", previous.markerFor("still readable"));
            put("new", current.markerFor("already moved"));
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
