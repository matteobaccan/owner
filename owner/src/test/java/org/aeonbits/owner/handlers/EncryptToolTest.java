/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.handlers;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The tool: what it accepts on a command line, what it refuses there, and that what it writes reads back.
 *
 * @author Matteo Baccan
 */
public class EncryptToolTest {

    private static final String PASSPHRASE = "a passphrase";

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private String stdout() {
        return new String(out.toByteArray());
    }

    private String stderr() {
        return new String(err.toByteArray());
    }

    private int encrypt(EncryptTool.Options options, String... values) {
        Encrypting handler = EncryptTool.handlerFor(options, PASSPHRASE.toCharArray());
        return EncryptTool.encrypt(handler, options, Arrays.asList(values),
                new PrintStream(out), new PrintStream(err));
    }

    @Test
    public void whatItWritesIsWhatTheHandlerReads() {
        assertEquals(0, encrypt(EncryptTool.parse(new String[0]), "s3cr3t"));

        String marker = stdout().trim();
        assertTrue(marker, marker.startsWith("${$aes-gcm::"));
        assertTrue(marker, marker.endsWith("}"));
        assertEquals("s3cr3t", new AesGcmHandler(PASSPHRASE).resolve(payloadOf(marker)));
    }

    @Test
    public void oneMarkerPerValueInTheOrderTheyArrived() {
        assertEquals(0, encrypt(EncryptTool.parse(new String[0]), "one", "two", "three"));

        String[] markers = stdout().trim().split("\\R");
        assertEquals(3, markers.length);
        AesGcmHandler handler = new AesGcmHandler(PASSPHRASE);
        assertEquals("one", handler.resolve(payloadOf(markers[0])));
        assertEquals("two", handler.resolve(payloadOf(markers[1])));
        assertEquals("three", handler.resolve(payloadOf(markers[2])));
    }

    /** The reason to encrypt a whole file in one run: one salt, therefore one key derivation. */
    @Test
    public void everyValueOfOneRunSharesTheSalt() {
        encrypt(EncryptTool.parse(new String[0]), "one", "two");

        String[] markers = stdout().trim().split("\\R");
        byte[] first = Base64.getDecoder().decode(payloadOf(markers[0]));
        byte[] second = Base64.getDecoder().decode(payloadOf(markers[1]));
        for (int i = 0; i < 20; i++)
            assertEquals("byte " + i + ", the count and the salt", first[i], second[i]);
        assertFalse("the IV is per value",
                Arrays.equals(Arrays.copyOfRange(first, 20, 32), Arrays.copyOfRange(second, 20, 32)));
    }

    private static String payloadOf(String marker) {
        return marker.substring(marker.indexOf("::") + 2, marker.length() - 1);
    }

    @Test
    public void theMarkersAreOnStandardOutputAndEverythingElseIsNot() {
        encrypt(EncryptTool.parse(new String[0]), "s3cr3t");
        assertTrue(stdout(), stdout().startsWith("${$"));
        assertEquals("standard output holds markers and nothing else", 1, stdout().trim().split("\\R").length);
        assertTrue(stderr(), stderr().contains("1 value encrypted"));
    }

    @Test
    public void aNameOfItsOwnGoesInTheMarker() {
        encrypt(EncryptTool.parse(new String[]{"--name", "aes-gcm-2025"}), "s3cr3t");
        assertTrue(stdout(), stdout().startsWith("${$aes-gcm-2025::"));
    }

    @Test
    public void aCountOfItsOwnIsWrittenIntoTheToken() {
        encrypt(EncryptTool.parse(new String[]{"--iterations", "150000"}), "s3cr3t");

        byte[] token = Base64.getDecoder().decode(payloadOf(stdout().trim()));
        int written = ((token[0] & 0xff) << 24) | ((token[1] & 0xff) << 16)
                | ((token[2] & 0xff) << 8) | (token[3] & 0xff);
        assertEquals(150_000, written);
        assertEquals("s3cr3t", new AesGcmHandler(PASSPHRASE).resolve(payloadOf(stdout().trim())));
    }

    @Test
    public void aCountBelowTheMinimumIsRefusedWithAReasonAndNotAStackTrace() {
        EncryptTool.Options options = EncryptTool.parse(new String[]{"--iterations", "10"});
        try {
            encrypt(options, "s3cr3t");
            fail("10 iterations is not a cost worth paying for");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("iterations"));
        }
        assertEquals("nothing was written", "", stdout());
    }

    /**
     * The one constraint of the whole design that is not negotiable. A value on the command line is in the
     * shell history and in <code>ps</code>, so it is refused rather than quietly accepted.
     */
    @Test
    public void aValueOnTheCommandLineIsRefusedAndSaysWhy() {
        try {
            EncryptTool.parse(new String[]{"s3cr3t"});
            fail("a value is not an argument");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("shell history"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("standard input"));
        }
    }

    @Test
    public void anUnknownOptionIsRefused() {
        try {
            EncryptTool.parse(new String[]{"--passphrase", "hunter2"});
            fail("--passphrase is deliberately not an option");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("--passphrase"));
        }
    }

    @Test
    public void anOptionWithoutItsValueIsRefused() {
        try {
            EncryptTool.parse(new String[]{"--name"});
            fail("--name needs a name");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("--name"));
        }
    }

    @Test
    public void iterationsHasToBeANumber() {
        try {
            EncryptTool.parse(new String[]{"--iterations", "plenty"});
            fail("'plenty' is not a count");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("plenty"));
        }
    }

    @Test
    public void theDefaultsAreTheSymmetricCiphersOwn() {
        EncryptTool.Options options = EncryptTool.parse(new String[0]);
        assertTrue(options.isSymmetric());
        assertEquals(AesGcmHandler.DEFAULT_NAME, options.markerName());
        assertEquals(AesGcmHandler.DEFAULT_ITERATIONS, options.iterations);
        assertFalse(options.help);
    }

    @Test
    public void helpGoesToStandardOutputAndSaysHowToPassThePassphrase() {
        assertEquals(0, EncryptTool.run(new String[]{"--help"}, new PrintStream(out), new PrintStream(err)));
        assertTrue(stdout(), stdout().contains(EncryptTool.PASSPHRASE_VARIABLE));
        assertTrue(stdout(), stdout().contains("never an argument"));
        assertTrue("both ciphers are offered", stdout().contains(EncryptTool.ASYMMETRIC));
    }

    @Test
    public void aCommandLineThatIsNotUnderstoodExitsTwo() {
        assertEquals(2, EncryptTool.run(new String[]{"--nonsense"}, new PrintStream(out), new PrintStream(err)));
        assertTrue(stderr(), stderr().contains("Unknown option"));
    }

    @Test
    public void nothingItPrintsEverContainsThePassphrase() {
        List<String> values = Arrays.asList("s3cr3t", "hunter2");
        EncryptTool.Options options = EncryptTool.parse(new String[0]);
        EncryptTool.encrypt(EncryptTool.handlerFor(options, "the passphrase".toCharArray()), options,
                values, new PrintStream(out), new PrintStream(err));
        assertFalse(stdout(), stdout().contains("the passphrase"));
        assertFalse(stderr(), stderr().contains("the passphrase"));
        assertFalse("nor the values themselves", stdout().contains("s3cr3t"));
    }

    // --- choosing the asymmetric cipher -------------------------------------------------------------

    @Test
    public void aPublicKeyChoosesTheAsymmetricCipherOnItsOwn() {
        EncryptTool.Options options = EncryptTool.parse(new String[]{"--public-key", "app.pub"});
        assertFalse(options.isSymmetric());
        assertEquals(RsaHandler.DEFAULT_NAME, options.markerName());
    }

    @Test
    public void theAsymmetricCipherWithoutAKeyIsRefusedWithTheReasonItNeedsOne() {
        EncryptTool.Options options = EncryptTool.parse(new String[]{"--handler", "rsa-oaep"});
        try {
            EncryptTool.handlerFor(options, null);
            fail("rsa-oaep encrypts to a key");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("--public-key"));
        }
    }

    /** Two options that belong to two different constructions, each refused where it means nothing. */
    @Test
    public void anOptionOfTheWrongCipherIsRefused() {
        try {
            EncryptTool.parse(new String[]{"--handler", "rsa-oaep", "--iterations", "300000"});
            fail("rsa-oaep derives nothing");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("--iterations"));
        }
        try {
            EncryptTool.parse(new String[]{"--handler", "aes-gcm", "--public-key", "app.pub"});
            fail("aes-gcm has one passphrase");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("--public-key"));
        }
    }

    @Test
    public void anUnknownCipherIsRefusedAndTheKnownOnesAreNamed() {
        try {
            EncryptTool.parse(new String[]{"--handler", "rot13"});
            fail("rot13 is not a cipher we ship");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("aes-gcm"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("rsa-oaep"));
        }
    }
}
