/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.crypto.CryptoConfigTest;
import org.aeonbits.owner.util.LogCapture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A value that refers to an encrypted property through a variable gets the <b>encrypted</b> text.
 * <pre>
 *     crypto.password = tzH7IKLCVc0AC72fh5DiZA==
 *     jdbc.url        = jdbc:h2:mem:test?password=${crypto.password}
 * </pre>
 * <code>password()</code> answers with the secret and <code>jdbcUrl()</code> answers with the cipher text,
 * because the properties hold the cipher text and decryption is chosen by the <code>@EncryptedValue</code>
 * on a <i>method</i>, while a variable names a <i>key</i>. Found by probing issue #287, which reports the
 * same thing about a converter; nobody had reported this half.
 *
 * @see PropertiesManager#reportReferencesToEncryptedValues
 */
public class CipherReferenceTest {

    /** The cipher text of "This is my key." under the decryptor these tests share. */
    private static final String CIPHERTEXT = "tzH7IKLCVc0AC72fh5DiZA==";
    private static final String SECRET = CryptoConfigTest.PASSWORD_EXPECTED;

    private LogCapture capture;

    @Before
    public void collectTheWarnings() {
        capture = LogCapture.ofLibrary(Level.WARNING);
    }

    @After
    public void putTheLoggerBack() {
        capture.close();
    }

    private static Properties values() {
        Properties p = new Properties();
        p.setProperty("crypto.password", CIPHERTEXT);
        p.setProperty("jdbc.url", "jdbc:h2:mem:test?password=${crypto.password}");
        return p;
    }

    @Config.DecryptorClass(CryptoConfigTest.Decryptor1.class)
    public interface RefersToASecret extends Config {
        @Key("crypto.password")
        @EncryptedValue
        String password();

        @Key("jdbc.url")
        String jdbcUrl();
    }

    /**
     * The defect itself, stated rather than assumed. This is not the behaviour being endorsed — it is what
     * the warning exists to point at, and the test is what would notice if it ever changed.
     */
    @Test
    public void theReferringValueIsBuiltWithTheCipherText() {
        RefersToASecret cfg = ConfigFactory.create(RefersToASecret.class, values());

        assertEquals("the method that declares it decrypts", SECRET, cfg.password());
        assertEquals("the value that refers to it does not",
                "jdbc:h2:mem:test?password=" + CIPHERTEXT, cfg.jdbcUrl());
    }

    @Test
    public void itIsSaidOnceAndNamesBothKeysAndNoValue() {
        ConfigFactory.create(RefersToASecret.class, values());

        String said = capture.messagesFrom(Level.WARNING);
        assertTrue(said, said.contains("jdbc.url"));
        assertTrue(said, said.contains("crypto.password"));
        assertTrue(said, said.contains("@EncryptedValue"));
        assertEquals("one line, not one per property", 1, capture.linesFrom(Level.WARNING).size());

        assertFalse("the cipher text is not in the message", said.contains(CIPHERTEXT));
        assertFalse("and neither is the secret", said.contains(SECRET));
    }

    @Test
    public void aReferenceCarryingADefaultCountsToo() {
        Properties p = values();
        p.setProperty("jdbc.url", "jdbc:h2:mem:test?password=${crypto.password:none}");

        ConfigFactory.create(RefersToASecret.class, p);

        assertTrue(capture.messagesFrom(Level.WARNING), capture.messagesFrom(Level.WARNING).contains("crypto.password"));
    }

    @Test
    public void underStrictItIsRefused() {
        Factory strict = ConfigFactory.newInstance();
        strict.setProperty("owner.strict", "true");

        try {
            strict.create(RefersToASecret.class, values());
            fail("expected the reference to an encrypted value to be refused");
        } catch (UnsupportedOperationException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("crypto.password"));
            assertTrue(refused.getMessage(), refused.getMessage().contains("owner.strict"));
            assertFalse("no value in the message", refused.getMessage().contains(CIPHERTEXT));
        }
    }

    // ------------------------------------------------------ what it leaves alone

    @Config.DecryptorClass(CryptoConfigTest.Decryptor1.class)
    public interface NoReference extends Config {
        @Key("crypto.password")
        @EncryptedValue
        String password();

        @Key("jdbc.url")
        String jdbcUrl();
    }

    /** An encrypted property nobody refers to is the ordinary case, and says nothing. */
    @Test
    public void anEncryptedValueOnItsOwnIsNotWorthAWord() {
        Properties p = new Properties();
        p.setProperty("crypto.password", CIPHERTEXT);
        p.setProperty("jdbc.url", "jdbc:h2:mem:test");

        assertEquals(SECRET, ConfigFactory.create(NoReference.class, p).password());
        assertTrue(capture.messagesFrom(Level.WARNING), capture.linesFrom(Level.WARNING).isEmpty());
    }

    public interface NothingEncrypted extends Config {
        @Key("a")
        String a();

        @Key("b")
        String b();
    }

    /**
     * A configuration with no encrypted property at all does not pay for the search: the set of encrypted
     * keys is empty and the scan returns before looking at a single value.
     */
    @Test
    public void aConfigurationWithoutEncryptionIsNotSearched() {
        Properties p = new Properties();
        p.setProperty("a", "plain");
        p.setProperty("b", "refers to ${a}");

        assertEquals("refers to plain", ConfigFactory.create(NothingEncrypted.class, p).b());
        assertTrue(capture.messagesFrom(Level.WARNING), capture.linesFrom(Level.WARNING).isEmpty());
    }

    /**
     * The encrypted property referring to itself is not a case: it is the key being read, not a value
     * quietly built out of a cipher text, and the method that reads it decrypts as it always did.
     */
    @Test
    public void theEncryptedPropertyItselfIsNotReported() {
        Properties p = new Properties();
        p.setProperty("crypto.password", CIPHERTEXT);
        p.setProperty("jdbc.url", "jdbc:h2:mem:test");

        ConfigFactory.create(NoReference.class, p);
        assertTrue(capture.messagesFrom(Level.WARNING), capture.linesFrom(Level.WARNING).isEmpty());
    }
}
