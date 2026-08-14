/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.handlers;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Turns values into the markers that go in a configuration file, with either of the two ciphers.
 * <pre>
 *     java -cp owner.jar org.aeonbits.owner.handlers.EncryptTool [options]
 * </pre>
 * <p>
 * It prints one marker per value on standard output, and everything else - prompts, warnings, the summary
 * - on standard error, so that redirecting the output collects markers and nothing else.
 * </p>
 * <pre>
 *     $ printf 's3cr3t\nhunter2\n' | OWNER_PASSPHRASE=... \
 *           java -cp owner.jar org.aeonbits.owner.handlers.EncryptTool &gt; markers.txt
 *
 *     $ java -cp owner.jar org.aeonbits.owner.handlers.EncryptTool \
 *           --public-key app.pub &lt; values.txt
 * </pre>
 *
 * <h2>Nothing secret is an argument</h2>
 * <p>
 * Not the passphrase and not the values. A command line lands in the shell history and is visible in
 * <code>ps</code> to every user on the machine, which is the one way of handing over a secret that leaks
 * it to people who were never near the file. So:
 * </p>
 * <ul>
 *   <li>the <b>passphrase</b> comes from <code>OWNER_PASSPHRASE</code>, or is asked for on the terminal
 *   without echo - and asked for twice, because a passphrase mistyped here writes a file nobody can
 *   read;</li>
 *   <li>the <b>values</b> come from standard input, one per line, whether typed or piped.</li>
 * </ul>
 * <p>
 * What may be an argument is what is not secret: which cipher to use, the name to write in the marker,
 * how many iterations to derive with, and <b>the path of a public key</b> - which is public.
 * </p>
 *
 * <h2>One run, one salt — for the symmetric cipher</h2>
 * <p>
 * With {@link AesGcmHandler}, every value of one run is encrypted under the same salt and its own IV,
 * which makes reading them back cost one key derivation instead of one per property. That is the reason
 * to encrypt a whole file's worth of values in one go rather than a value at a time.
 * {@link RsaHandler} shares nothing between values, because there is no passphrase to derive from and so
 * nothing to amortise.
 * </p>
 *
 * @author Matteo Baccan
 * @see AesGcmHandler
 * @see RsaHandler
 * @since 2.0.0
 */
public final class EncryptTool {

    /** Where the passphrase is read from when it is not typed. */
    static final String PASSPHRASE_VARIABLE = "OWNER_PASSPHRASE";

    /** What <code>--handler</code> accepts, which is the name of a cipher and not of a marker. */
    static final String SYMMETRIC = "aes-gcm";
    static final String ASYMMETRIC = "rsa-oaep";

    private static final String USAGE = ""
            + "Usage: java -cp owner.jar org.aeonbits.owner.handlers.EncryptTool [options]%n"
            + "%n"
            + "Reads values from standard input, one per line, and writes one marker per line on%n"
            + "standard output.%n"
            + "%n"
            + "Options:%n"
            + "  --handler CIPHER     %s (default) or %s. Giving --public-key%n"
            + "                       chooses the second one on its own.%n"
            + "  --public-key FILE    the PEM public key to encrypt to, for %s. A path is not%n"
            + "                       secret and a public key is not either, so both may be here.%n"
            + "  --name NAME          the handler name to write in the marker (default: the cipher's%n"
            + "                       own). Two names with two keys is how a rotation is done.%n"
            + "  --iterations COUNT   PBKDF2 iterations for %s, at least %d (default: %d).%n"
            + "  --help               this text.%n"
            + "%n"
            + "The passphrase is never an argument. It is taken from %s, or asked for on the%n"
            + "terminal without echo. Neither are the values: a command line is visible in ps and%n"
            + "kept in the shell history.%n"
            + "%n"
            + "  $ printf 's3cr3t\\nhunter2\\n' | OWNER_PASSPHRASE=... \\%n"
            + "      java -cp owner.jar org.aeonbits.owner.handlers.EncryptTool > markers.txt%n"
            + "%n"
            + "  $ java -cp owner.jar org.aeonbits.owner.handlers.EncryptTool \\%n"
            + "      --public-key app.pub < values.txt%n";

    private EncryptTool() {
    }

    /**
     * The entry point, and the only thing in this jar that has one.
     *
     * @param args the options; never a passphrase and never a value, both of which are read from
     *             standard input for the reason given above.
     */
    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * The whole tool except the exit, so that it can be run without ending the JVM.
     *
     * @return the exit status: 0 when every value was encrypted, 1 when something went wrong, 2 when the
     *         command line was not understood.
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        Options options;
        try {
            options = parse(args);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            usage(err);
            return 2;
        }
        if (options.help) {
            usage(out);
            return 0;
        }

        char[] passphrase = null;
        Encrypting handler;
        try {
            if (options.isSymmetric())
                passphrase = passphrase();
            handler = handlerFor(options, passphrase);
        } catch (IllegalStateException | IllegalArgumentException e) {
            err.println(e.getMessage());
            return 1;
        } finally {
            if (passphrase != null)
                Arrays.fill(passphrase, '\0');
        }

        try {
            List<String> values = readValues(err);
            if (values.isEmpty()) {
                err.println("Nothing to encrypt: no value arrived on standard input.");
                return 1;
            }
            return encrypt(handler, options, values, out, err);
        } catch (IOException e) {
            err.println("Could not read the values: " + e.getMessage());
            return 1;
        }
    }

    private static void usage(PrintStream to) {
        to.format(USAGE, SYMMETRIC, ASYMMETRIC, ASYMMETRIC, SYMMETRIC,
                AesGcmHandler.MINIMUM_ITERATIONS, AesGcmHandler.DEFAULT_ITERATIONS, PASSPHRASE_VARIABLE);
    }

    /**
     * Builds the cipher the options ask for, out of the key material each one needs.
     * <p>
     * Kept apart from {@link #encrypt} so that what the tool <i>does</i> can be exercised without key
     * material, a terminal or an environment variable — and so that the two ciphers meet in exactly one
     * place.
     * </p>
     */
    static Encrypting handlerFor(Options options, char[] passphrase) {
        if (options.isSymmetric())
            return new AesGcmHandler(options.markerName(), passphrase, options.iterations);
        if (options.publicKey == null)
            throw new IllegalArgumentException(ASYMMETRIC + " encrypts to a public key, so --public-key "
                    + "is not optional here. It is the half that may be handed to whoever writes values, "
                    + "which is the arrangement a key pair exists for.");
        return new RsaHandler(options.markerName(),
                RsaHandler.publicKeyFrom(Paths.get(options.publicKey)), null);
    }

    /** Encrypts every value and prints the markers. */
    static int encrypt(Encrypting handler, Options options, List<String> values, PrintStream out,
                       PrintStream err) {
        String[] tokens;
        try {
            tokens = handler.encryptAll(values.toArray(new String[0]));
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 1;
        }
        for (String token : tokens)
            out.println(handler.marker(token));
        err.format("%d value%s encrypted as '%s'%s.%n", tokens.length, tokens.length == 1 ? "" : "s",
                options.markerName(), options.isSymmetric()
                        ? String.format(", under one salt, at %,d iterations", options.iterations)
                        : ", each with its own key");
        return 0;
    }

    /**
     * The passphrase, from the environment or from the terminal.
     * <p>
     * Asked for twice when it is typed, and only then: an environment variable was set on purpose and
     * confirming it would be theatre, while a passphrase mistyped at a prompt produces a file that cannot
     * be read and no sign of why until it is.
     * </p>
     * <p>
     * Nothing is printed from here, which is why it takes no stream: the prompts belong to the console
     * that reads them, and going through one of ours would echo the passphrase back.
     * </p>
     */
    private static char[] passphrase() {
        String fromEnvironment = System.getenv(PASSPHRASE_VARIABLE);
        if (fromEnvironment != null && !fromEnvironment.isEmpty())
            return fromEnvironment.toCharArray();

        Console console = terminal();
        if (console == null)
            throw new IllegalStateException(String.format(
                    "There is no terminal to ask for the passphrase on, and %s is not set. Set it, and "
                            + "keep it out of the command line: an argument is visible in ps and stays in "
                            + "the shell history.", PASSPHRASE_VARIABLE));

        return ConsolePassphrase.ask(console);
    }

    /**
     * The passphrase, given what was typed twice, or the reason it is neither.
     * <p>
     * Here rather than beside the two <code>readPassword</code> calls that produce them, and
     * deliberately: a terminal cannot be had under a test runner, so whatever is left in
     * {@link ConsolePassphrase} is unmeasured. "These two agree and are not empty" is a rule worth
     * pinning down and needs no terminal to state, so it is on this side of that line.
     * </p>
     * <p>
     * Neither array survives the call, the one the answer is copied from included.
     * </p>
     *
     * @param typed what was typed the first time, or <code>null</code> at end of input.
     * @param again what was typed the second time.
     * @return the passphrase, as a copy.
     * @throws IllegalStateException if the first is empty, or the two differ.
     */
    static char[] confirmed(char[] typed, char[] again) {
        char[] first = typed == null ? new char[0] : typed;
        char[] second = again == null ? new char[0] : again;
        try {
            if (first.length == 0)
                throw new IllegalStateException("The passphrase is empty.");
            if (!Arrays.equals(first, second))
                throw new IllegalStateException("The two do not match. Nothing was written.");
            return first.clone();
        } finally {
            Arrays.fill(first, ' ');
            Arrays.fill(second, ' ');
        }
    }

    /**
     * The console, but <b>only when it is a terminal a person is at</b>, and <code>null</code> otherwise.
     * <p>
     * <code>System.console()</code> used to mean exactly that, and until JDK 21 this method could have
     * been one call. It changed: since JDK 22 a Console is returned even when the streams are redirected,
     * so that a JLine-backed one can be used in a pipeline. Asking the old question of the new JDK gets
     * the wrong answer here, and dangerously - <code>readPassword</code> on a redirected stream reads the
     * <i>values</i> being piped in and takes the first one for the passphrase, silently.
     * </p>
     * <p>
     * <code>Console.isTerminal()</code> is the new question, and it does not exist on the Java 8 baseline,
     * so it is asked by reflection when it is there and assumed true when it is not - which is what it was
     * on every JDK that lacks the method.
     * </p>
     */
    private static Console terminal() {
        Console console = System.console();
        if (console == null)
            return null;
        try {
            Object isTerminal = Console.class.getMethod("isTerminal").invoke(console);
            return Boolean.TRUE.equals(isTerminal) ? console : null;
        } catch (NoSuchMethodException olderThanJdk22) {
            return console;
        } catch (Exception e) {
            return console;
        }
    }

    /**
     * The values, one per line, from standard input. A blank line is skipped rather than encrypted: the
     * empty string is a value somebody may legitimately want, but never one they typed by pressing enter.
     */
    private static List<String> readValues(PrintStream err) throws IOException {
        if (terminal() != null)
            err.println("Values, one per line. End with an empty line.");
        List<String> values = new ArrayList<>();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isEmpty())
                break;
            values.add(line);
        }
        return values;
    }

    /**
     * Reads the command line, which may hold only what is not secret.
     *
     * @throws IllegalArgumentException on anything unrecognised, and on a bare argument - which is nearly
     *                                  always somebody passing the value itself, and is worth saying out
     *                                  loud rather than silently ignoring.
     */
    static Options parse(String[] args) {
        Options options = new Options();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--help".equals(arg) || "-h".equals(arg)) {
                options.help = true;
            } else if ("--handler".equals(arg)) {
                options.handler = cipherNamed(valueOf(args, ++i, "--handler"));
            } else if ("--public-key".equals(arg)) {
                options.publicKey = valueOf(args, ++i, "--public-key");
                if (options.handler == null)
                    options.handler = ASYMMETRIC;
            } else if ("--name".equals(arg)) {
                options.name = valueOf(args, ++i, "--name");
            } else if ("--iterations".equals(arg)) {
                options.iterations = iterationsOf(valueOf(args, ++i, "--iterations"));
                options.iterationsWereAsked = true;
            } else if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Unknown option: " + arg);
            } else {
                throw new IllegalArgumentException(
                        "A value is not a command-line argument: it would be kept in the shell history "
                                + "and visible in ps to every user on this machine. Values are read from "
                                + "standard input, one per line.");
            }
        }
        if (options.handler == null)
            options.handler = SYMMETRIC;
        if (options.iterationsWereAsked && !options.isSymmetric())
            throw new IllegalArgumentException("--iterations belongs to " + SYMMETRIC + ", which derives "
                    + "a key from a passphrase. " + ASYMMETRIC + " encrypts to a key and derives nothing.");
        if (options.publicKey != null && options.isSymmetric())
            throw new IllegalArgumentException("--public-key belongs to " + ASYMMETRIC + ". " + SYMMETRIC
                    + " has one passphrase that both writes and reads.");
        return options;
    }

    private static String cipherNamed(String asked) {
        if (SYMMETRIC.equals(asked) || ASYMMETRIC.equals(asked))
            return asked;
        throw new IllegalArgumentException("--handler is " + SYMMETRIC + " or " + ASYMMETRIC
                + ", not '" + asked + "'");
    }

    private static String valueOf(String[] args, int index, String option) {
        if (index >= args.length)
            throw new IllegalArgumentException(option + " needs a value");
        return args[index];
    }

    private static int iterationsOf(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--iterations is a number, not '" + text + "'");
        }
    }

    /** What the command line may say, which is everything about the marker except what goes inside it. */
    static final class Options {
        String handler;
        String name;
        String publicKey;
        int iterations = AesGcmHandler.DEFAULT_ITERATIONS;
        boolean iterationsWereAsked;
        boolean help;

        boolean isSymmetric() {
            return !ASYMMETRIC.equals(handler);
        }

        /** The name written in the marker: the one asked for, or the cipher's own. */
        String markerName() {
            if (name != null)
                return name;
            return isSymmetric() ? AesGcmHandler.DEFAULT_NAME : RsaHandler.DEFAULT_NAME;
        }
    }
}
