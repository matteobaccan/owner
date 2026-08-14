/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.handlers;

import org.junit.After;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
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

    @Test
    public void helpIsSpelledBothWays() {
        assertTrue(EncryptTool.parse(new String[]{"--help"}).help);
        assertTrue(EncryptTool.parse(new String[]{"-h"}).help);
    }

    /** A handler that cannot encrypt says so on standard error and exits one, without a stack trace. */
    @Test
    public void aHandlerThatCannotEncryptIsReportedAndNothingIsWritten() {
        EncryptTool.Options options = EncryptTool.parse(new String[]{"--handler", "rsa-oaep"});
        int status = EncryptTool.encrypt(new RsaHandler(pair.getPrivate()), options,
                Arrays.asList("s3cr3t"), new PrintStream(out), new PrintStream(err));
        assertEquals(1, status);
        assertEquals("nothing was written", "", stdout());
        assertTrue(stderr(), stderr().contains("only a private key"));
        assertFalse("and not the value it was asked to encrypt", stderr().contains("s3cr3t"));
    }

    // --- the whole tool, from a command line to markers ----------------------------------------------

    /** One key pair for the asymmetric runs: generating one is the slowest thing in this class. */
    private static KeyPair pair;

    @BeforeClass
    public static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pair = generator.generateKeyPair();
    }

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final InputStream originalIn = System.in;

    @After
    public void restoreStandardInput() {
        System.setIn(originalIn);
    }

    private File publicKeyFile() throws Exception {
        File file = folder.newFile("app.pub");
        StringBuilder pem = new StringBuilder("-----BEGIN PUBLIC KEY-----\n");
        String base64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        for (int i = 0; i < base64.length(); i += 64)
            pem.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        pem.append("-----END PUBLIC KEY-----\n");
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(pem.toString().getBytes("UTF-8"));
        }
        return file;
    }

    private int run(String... args) {
        return EncryptTool.run(args, new PrintStream(out), new PrintStream(err));
    }

    private static void standardInputHolding(String text) {
        System.setIn(new ByteArrayInputStream(text.getBytes()));
    }

    /**
     * The documented asymmetric invocation, end to end and in one process: a path to a public key on the
     * command line, the values on standard input, and markers the deployment's private key reads back.
     * This is the run that needs no passphrase, which is what makes it the one that can be driven here.
     */
    @Test
    public void aRunWithAPublicKeyTurnsPipedValuesIntoMarkersTheDeploymentCanRead() throws Exception {
        standardInputHolding("s3cr3t\nhunter2\n");
        assertEquals(0, run("--public-key", publicKeyFile().getAbsolutePath()));

        String[] markers = stdout().trim().split("\\R");
        assertEquals(2, markers.length);
        RsaHandler deployment = new RsaHandler(pair.getPrivate());
        assertEquals("s3cr3t", deployment.resolve(payloadOf(markers[0])));
        assertEquals("hunter2", deployment.resolve(payloadOf(markers[1])));
        assertTrue(markers[0], markers[0].startsWith("${$rsa-oaep::"));
        assertTrue(stderr(), stderr().contains("2 values encrypted"));
        assertTrue("and says what it did rather than talking about a salt it does not have",
                stderr().contains("each with its own key"));
    }

    @Test
    public void aBlankLineEndsTheValuesAndTheNameOnTheCommandLineIsTheOneWritten() throws Exception {
        standardInputHolding("s3cr3t\n\nnever read\n");
        assertEquals(0, run("--public-key", publicKeyFile().getAbsolutePath(), "--name", "rsa-2025"));
        assertEquals("one marker and no more", 1, stdout().trim().split("\\R").length);
        assertTrue(stdout(), stdout().startsWith("${$rsa-2025::"));
    }

    @Test
    public void nothingOnStandardInputIsSaidRatherThanReportedAsSuccess() throws Exception {
        standardInputHolding("");
        assertEquals(1, run("--public-key", publicKeyFile().getAbsolutePath()));
        assertEquals("", stdout());
        assertTrue(stderr(), stderr().contains("Nothing to encrypt"));
    }

    /** Standard input can fail rather than end, and that is a message and a status, not a stack trace. */
    @Test
    public void standardInputThatCannotBeReadIsReportedAsSuch() throws Exception {
        System.setIn(new FailingInputStream());
        assertEquals(1, run("--public-key", publicKeyFile().getAbsolutePath()));
        assertEquals("", stdout());
        assertTrue(stderr(), stderr().contains("Could not read the values"));
    }

    /** A stream that fails instead of ending, to prove the failure is caught rather than thrown out. */
    static final class FailingInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("the pipe went away");
        }
    }

    @Test
    public void theAsymmetricCipherWithoutAKeyExitsOneAndSaysWhichOptionIsMissing() {
        standardInputHolding("s3cr3t\n");
        assertEquals(1, run("--handler", "rsa-oaep"));
        assertEquals("", stdout());
        assertTrue(stderr(), stderr().contains("--public-key"));
    }

    /**
     * The hazard the tool is built around, and the reason it asks <code>Console.isTerminal()</code> rather
     * than only <code>System.console() != null</code>. Under redirected streams - which is what a pipeline
     * is, and what this test is - a JDK 22 and later hands back a Console all the same, and
     * <code>readPassword</code> on it would read the <b>first piped value</b> and take it for the
     * passphrase. Silently: the value would then be missing from the output and the file would be
     * encrypted under a passphrase nobody chose.
     * <p>
     * So with no passphrase in the environment and no terminal to ask on, the tool must refuse and name
     * the variable, and above all must not have written anything.
     * </p>
     */
    @Test
    public void withStreamsRedirectedAndNoEnvironmentVariableThePipedValueIsNotTakenForThePassphrase() {
        Assume.assumeTrue("this machine has " + EncryptTool.PASSPHRASE_VARIABLE + " set",
                System.getenv(EncryptTool.PASSPHRASE_VARIABLE) == null);
        standardInputHolding("s3cr3t\nhunter2\n");

        assertEquals(1, run());
        assertEquals("nothing was encrypted", "", stdout());
        assertTrue(stderr(), stderr().contains(EncryptTool.PASSPHRASE_VARIABLE));
        assertFalse("and the value that was piped in was not taken for anything",
                stderr().contains("s3cr3t"));
    }

    /**
     * The symmetric run, which is the invocation the documentation leads with, and the only one that can be
     * driven nowhere else: the passphrase comes from the environment, and an environment belongs to a
     * process. So this one is a process - the command from the class javadoc, run as written.
     * <p>
     * What it pins down is the whole of it and not only the cipher: that <code>OWNER_PASSPHRASE</code> is
     * honoured, that {@link EncryptTool#main} exits <b>0</b> rather than merely returning, that the markers
     * arrive on standard output where a redirect collects them and the summary does not, and that what
     * comes out reads back under the same passphrase.
     * </p>
     */
    @Test
    public void thePassphraseComesFromTheEnvironmentAndTheMarkersFromStandardOutput() throws Exception {
        File java = new File(new File(System.getProperty("java.home"), "bin"),
                System.getProperty("os.name", "").toLowerCase().startsWith("win") ? "java.exe" : "java");
        Assume.assumeTrue("no java to run: " + java, java.canExecute());

        ProcessBuilder builder = new ProcessBuilder(java.getAbsolutePath(),
                "-cp", System.getProperty("java.class.path"), EncryptTool.class.getName(),
                "--iterations", String.valueOf(AesGcmHandler.MINIMUM_ITERATIONS));
        builder.environment().put(EncryptTool.PASSPHRASE_VARIABLE, PASSPHRASE);
        Process process = builder.start();

        try (OutputStream toIt = process.getOutputStream()) {
            toIt.write("s3cr3t\nhunter2\n".getBytes("UTF-8"));
        }
        String markers = read(process.getInputStream());
        String summary = read(process.getErrorStream());
        assertEquals("it should have said it was happy: " + summary, 0, process.waitFor());

        String[] lines = markers.trim().split("\\R");
        assertEquals("standard output holds markers and nothing else", 2, lines.length);
        AesGcmHandler handler = new AesGcmHandler(PASSPHRASE);
        assertEquals("s3cr3t", handler.resolve(payloadOf(lines[0])));
        assertEquals("hunter2", handler.resolve(payloadOf(lines[1])));
        assertTrue(summary, summary.contains("2 values encrypted"));
        assertFalse("the passphrase is never printed", (markers + summary).contains(PASSPHRASE));
    }

    private static String read(InputStream from) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try (InputStream in = from) {
            for (int got; (got = in.read(buffer)) != -1; )
                bytes.write(buffer, 0, got);
        }
        return new String(bytes.toByteArray(), "UTF-8");
    }

    // --- the decision a terminal produces, which needs no terminal to check ---------------------------

    /**
     * <code>ConsolePassphrase</code> is excluded from the coverage measurement because it cannot be
     * reached without a person at a keyboard. What it produces is judged here, on this side of that line:
     * two typings agree and are not empty, or they are refused with the reason.
     */
    @Test
    public void twoTypingsThatAgreeArePassphrase() {
        assertArrayEquals("hunter2".toCharArray(),
                EncryptTool.confirmed("hunter2".toCharArray(), "hunter2".toCharArray()));
    }

    @Test
    public void twoTypingsThatDifferAreRefusedWithoutWritingAnything() {
        try {
            EncryptTool.confirmed("hunter2".toCharArray(), "hunter3".toCharArray());
            fail("a mistyped passphrase writes a file nobody can read");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("do not match"));
        }
    }

    @Test
    public void anEmptyPassphraseIsRefusedAndSoIsEndOfInput() {
        for (char[][] typed : new char[][][]{
                {new char[0], new char[0]},
                {null, null}}) {
            try {
                EncryptTool.confirmed(typed[0], typed[1]);
                fail("that is not a passphrase");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("empty"));
            }
        }
    }

    /** Neither array survives the call, including the one the answer is copied from. */
    @Test
    public void whatWasTypedIsBlankedOnTheWayOut() {
        char[] typed = "hunter2".toCharArray();
        char[] again = "hunter2".toCharArray();
        char[] passphrase = EncryptTool.confirmed(typed, again);

        assertArrayEquals("hunter2".toCharArray(), passphrase);
        assertArrayEquals(new char[7], typed);
        assertArrayEquals(new char[7], again);
    }
}
