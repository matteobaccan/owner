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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Turns a value into the marker that goes in a configuration file.
 * <pre>
 *     java -cp owner.jar org.aeonbits.owner.handlers.AesGcmTool
 * </pre>
 * <p>
 * It prints one <code>${$aes-gcm::...}</code> per value on standard output, and everything else -
 * prompts, warnings, the summary - on standard error, so that redirecting the output collects markers and
 * nothing else.
 * </p>
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
 * What may be an argument is what is not secret: which handler name to write, and how many iterations to
 * derive with.
 * </p>
 *
 * <h2>One run, one salt</h2>
 * <p>
 * Every value given in one run is encrypted under the same salt and its own IV. That is what makes reading
 * the file back cost one key derivation instead of one per property, and it is the reason to encrypt a
 * whole file's worth of values in one go rather than a value at a time.
 * </p>
 *
 * @author Matteo Baccan
 * @see AesGcmHandler
 * @since 2.0.0
 */
public final class AesGcmTool {

    /** Where the passphrase is read from when it is not typed. */
    static final String PASSPHRASE_VARIABLE = "OWNER_PASSPHRASE";

    private static final String USAGE = ""
            + "Usage: java -cp owner.jar org.aeonbits.owner.handlers.AesGcmTool [options]%n"
            + "%n"
            + "Reads values from standard input, one per line, and writes one marker per line on%n"
            + "standard output. All the values of one run share a salt, so reading them back costs%n"
            + "one key derivation between them.%n"
            + "%n"
            + "Options:%n"
            + "  --name NAME          the handler name to write in the marker (default: %s).%n"
            + "                       Two names with two passphrases is how a key rotation is done.%n"
            + "  --iterations COUNT   PBKDF2 iterations, at least %d (default: %d).%n"
            + "  --help               this text.%n"
            + "%n"
            + "The passphrase is never an argument. It is taken from %s, or asked for on the%n"
            + "terminal without echo. Neither are the values: a command line is visible in ps and%n"
            + "kept in the shell history.%n"
            + "%n"
            + "  $ printf 's3cr3t\\nhunter2\\n' | OWNER_PASSPHRASE=... \\%n"
            + "      java -cp owner.jar org.aeonbits.owner.handlers.AesGcmTool > markers.txt%n";

    private AesGcmTool() {
    }

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
            err.format(USAGE, AesGcmHandler.DEFAULT_NAME, AesGcmHandler.MINIMUM_ITERATIONS,
                    AesGcmHandler.DEFAULT_ITERATIONS, PASSPHRASE_VARIABLE);
            return 2;
        }
        if (options.help) {
            out.format(USAGE, AesGcmHandler.DEFAULT_NAME, AesGcmHandler.MINIMUM_ITERATIONS,
                    AesGcmHandler.DEFAULT_ITERATIONS, PASSPHRASE_VARIABLE);
            return 0;
        }

        char[] passphrase;
        try {
            passphrase = passphrase();
        } catch (IllegalStateException e) {
            err.println(e.getMessage());
            return 1;
        }

        try {
            List<String> values = readValues(err);
            if (values.isEmpty()) {
                err.println("Nothing to encrypt: no value arrived on standard input.");
                return 1;
            }
            return encrypt(options, passphrase, values, out, err);
        } catch (IOException e) {
            err.println("Could not read the values: " + e.getMessage());
            return 1;
        } finally {
            Arrays.fill(passphrase, '\0');
        }
    }

    /**
     * Encrypts every value under one salt and prints the markers.
     * <p>
     * Separated from {@link #run} so that what the tool does can be exercised without a terminal, an
     * environment variable or a process to exit.
     * </p>
     */
    static int encrypt(Options options, char[] passphrase, List<String> values, PrintStream out,
                       PrintStream err) {
        AesGcmHandler handler;
        try {
            handler = new AesGcmHandler(options.name, passphrase, options.iterations);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 1;
        }
        String[] tokens = handler.encryptAll(values.toArray(new String[0]));
        for (String token : tokens)
            out.println(handler.marker(token));
        err.format("%d value%s encrypted as '%s', under one salt, at %,d iterations.%n",
                tokens.length, tokens.length == 1 ? "" : "s", options.name, options.iterations);
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

        char[] typed = console.readPassword("Passphrase: ");
        char[] again = console.readPassword("Again: ");
        try {
            if (typed == null || typed.length == 0)
                throw new IllegalStateException("The passphrase is empty.");
            if (!Arrays.equals(typed, again))
                throw new IllegalStateException("The two do not match. Nothing was written.");
            return typed.clone();
        } finally {
            Arrays.fill(typed == null ? new char[0] : typed, '\0');
            Arrays.fill(again == null ? new char[0] : again, '\0');
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
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
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
            } else if ("--name".equals(arg)) {
                options.name = valueOf(args, ++i, "--name");
            } else if ("--iterations".equals(arg)) {
                options.iterations = iterationsOf(valueOf(args, ++i, "--iterations"));
            } else if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Unknown option: " + arg);
            } else {
                throw new IllegalArgumentException(
                        "A value is not a command-line argument: it would be kept in the shell history "
                                + "and visible in ps to every user on this machine. Values are read from "
                                + "standard input, one per line.");
            }
        }
        return options;
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
        String name = AesGcmHandler.DEFAULT_NAME;
        int iterations = AesGcmHandler.DEFAULT_ITERATIONS;
        boolean help;
    }
}
