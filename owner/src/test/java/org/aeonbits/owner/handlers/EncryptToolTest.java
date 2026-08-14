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
public class AesGcmToolTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private String stdout() {
        return new String(out.toByteArray());
    }

    private String stderr() {
        return new String(err.toByteArray());
    }

    private int encrypt(AesGcmTool.Options options, String... values) {
        return AesGcmTool.encrypt(options, "a passphrase".toCharArray(), Arrays.asList(values),
                new PrintStream(out), new PrintStream(err));
    }

    @Test
    public void whatItWritesIsWhatTheHandlerReads() {
        assertEquals(0, encrypt(new AesGcmTool.Options(), "s3cr3t"));

        String marker = stdout().trim();
        assertTrue(marker, marker.startsWith("${$aes-gcm::"));
        assertTrue(marker, marker.endsWith("}"));

        String token = marker.substring("${$aes-gcm::".length(), marker.length() - 1);
        assertEquals("s3cr3t", new AesGcmHandler("a passphrase").resolve(token));
    }

    @Test
    public void oneMarkerPerValueInTheOrderTheyArrived() {
        assertEquals(0, encrypt(new AesGcmTool.Options(), "one", "two", "three"));

        String[] markers = stdout().trim().split("\\R");
        assertEquals(3, markers.length);
        AesGcmHandler handler = new AesGcmHandler("a passphrase");
        assertEquals("one", handler.resolve(payloadOf(markers[0])));
        assertEquals("two", handler.resolve(payloadOf(markers[1])));
        assertEquals("three", handler.resolve(payloadOf(markers[2])));
    }

    /** The reason to encrypt a whole file in one run: one salt, therefore one key derivation. */
    @Test
    public void everyValueOfOneRunSharesTheSalt() {
        encrypt(new AesGcmTool.Options(), "one", "two");

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
        encrypt(new AesGcmTool.Options(), "s3cr3t");
        assertTrue(stdout(), stdout().startsWith("${$"));
        assertEquals("standard output holds markers and nothing else", 1, stdout().trim().split("\\R").length);
        assertTrue(stderr(), stderr().contains("1 value encrypted"));
    }

    @Test
    public void aNameOfItsOwnGoesInTheMarker() {
        AesGcmTool.Options options = AesGcmTool.parse(new String[]{"--name", "aes-gcm-2025"});
        encrypt(options, "s3cr3t");
        assertTrue(stdout(), stdout().startsWith("${$aes-gcm-2025::"));
    }

    @Test
    public void aCountOfItsOwnIsWrittenIntoTheToken() {
        AesGcmTool.Options options = AesGcmTool.parse(new String[]{"--iterations", "150000"});
        encrypt(options, "s3cr3t");

        byte[] token = Base64.getDecoder().decode(payloadOf(stdout().trim()));
        int written = ((token[0] & 0xff) << 24) | ((token[1] & 0xff) << 16)
                | ((token[2] & 0xff) << 8) | (token[3] & 0xff);
        assertEquals(150_000, written);
        assertEquals("s3cr3t", new AesGcmHandler("a passphrase").resolve(payloadOf(stdout().trim())));
    }

    @Test
    public void aCountBelowTheMinimumIsRefusedWithAReasonAndNotAStackTrace() {
        AesGcmTool.Options options = AesGcmTool.parse(new String[]{"--iterations", "10"});
        assertEquals(1, encrypt(options, "s3cr3t"));
        assertEquals("nothing was written", "", stdout());
        assertTrue(stderr(), stderr().contains("iterations"));
    }

    /**
     * The one constraint of the whole design that is not negotiable. A value on the command line is in the
     * shell history and in <code>ps</code>, so it is refused rather than quietly accepted.
     */
    @Test
    public void aValueOnTheCommandLineIsRefusedAndSaysWhy() {
        try {
            AesGcmTool.parse(new String[]{"s3cr3t"});
            fail("a value is not an argument");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("shell history"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("standard input"));
        }
    }

    @Test
    public void anUnknownOptionIsRefused() {
        try {
            AesGcmTool.parse(new String[]{"--passphrase", "hunter2"});
            fail("--passphrase is deliberately not an option");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("--passphrase"));
        }
    }

    @Test
    public void anOptionWithoutItsValueIsRefused() {
        try {
            AesGcmTool.parse(new String[]{"--name"});
            fail("--name needs a name");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("--name"));
        }
    }

    @Test
    public void iterationsHasToBeANumber() {
        try {
            AesGcmTool.parse(new String[]{"--iterations", "plenty"});
            fail("'plenty' is not a count");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("plenty"));
        }
    }

    @Test
    public void theDefaultsAreTheHandlersOwn() {
        AesGcmTool.Options options = AesGcmTool.parse(new String[0]);
        assertEquals(AesGcmHandler.DEFAULT_NAME, options.name);
        assertEquals(AesGcmHandler.DEFAULT_ITERATIONS, options.iterations);
        assertFalse(options.help);
    }

    @Test
    public void helpGoesToStandardOutputAndSaysHowToPassThePassphrase() {
        assertEquals(0, AesGcmTool.run(new String[]{"--help"}, new PrintStream(out), new PrintStream(err)));
        assertTrue(stdout(), stdout().contains(AesGcmTool.PASSPHRASE_VARIABLE));
        assertTrue(stdout(), stdout().contains("never an argument"));
    }

    @Test
    public void aCommandLineThatIsNotUnderstoodExitsTwo() {
        assertEquals(2, AesGcmTool.run(new String[]{"--nonsense"}, new PrintStream(out), new PrintStream(err)));
        assertTrue(stderr(), stderr().contains("Unknown option"));
    }

    @Test
    public void nothingItPrintsEverContainsThePassphrase() {
        List<String> values = Arrays.asList("s3cr3t", "hunter2");
        AesGcmTool.encrypt(new AesGcmTool.Options(), "the passphrase".toCharArray(), values,
                new PrintStream(out), new PrintStream(err));
        assertFalse(stdout(), stdout().contains("the passphrase"));
        assertFalse(stderr(), stderr().contains("the passphrase"));
        assertFalse("nor the values themselves", stdout().contains("s3cr3t"));
    }
}
