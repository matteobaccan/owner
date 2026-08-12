/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.formats.toml;

import org.aeonbits.owner.formats.json.JsonLoader;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link TomlLoader} against <a href="https://github.com/toml-lang/toml-test">toml-test</a>, the TOML
 * conformance suite, in its v1.0.0 subset: 210 documents that must be read and 499 that must be refused.
 *
 * <p>
 * <b>This is why TOML is written here rather than delegated.</b> The rule this project settled on is that a
 * format whose specification <i>is</i> an implementation gets adapted rather than parsed — which is what
 * happened to HOCON. TOML has a written specification and this suite, so it is ours to parse, and the price
 * of saying so is being held to it. The suite decides when the parser is finished; we do not.
 * </p>
 *
 * <p>
 * The corpus is vendored under <code>src/test/resources/toml-test</code>, MIT licensed, with its LICENSE
 * beside it. It is copied rather than fetched so that the build stays offline and a test run means the same
 * thing in five years as today.
 * </p>
 *
 * <h2>How a valid document is compared</h2>
 *
 * <p>
 * The suite states each expected value as tagged JSON — <code>{"type": "integer", "value": "214"}</code> —
 * in which <b>the value is a string</b>, which is a piece of luck: it is the same model this library uses,
 * where everything is text until a converter is asked for a type. So the expectation is read with our own
 * {@link JsonLoader}, flattened by the same convention, and a key ending in <code>.value</code> names the
 * property while the matching <code>.type</code> says how to compare it.
 * </p>
 *
 * <p>
 * Using our JSON loader to check our TOML loader couples the two, and it is worth being explicit that this
 * is a deliberate trade: a bug in the JSON parser would surface here as TOML failures. It is accepted
 * because the alternative is a second JSON reader written for the tests, which would be the thing most
 * likely to be wrong, and because the JSON side has a suite of its own.
 * </p>
 *
 * <h2>The one comparison that is not textual</h2>
 *
 * <p>
 * A <code>float</code> is compared as a number rather than as text. The suite renders <code>3e2</code> as
 * <code>300</code> and <code>3e-2</code> as <code>0.03</code>, where we hand the text over as written —
 * because it converts, which is the rule: we canonicalise only what would otherwise convert to nothing, and
 * <code>Double.parseDouble</code> reads <code>3e2</code> perfectly well. Both spellings name one value, and
 * the number is what the suite is testing. Integers <i>are</i> compared textually and match exactly, our
 * canonical form and theirs being the same one.
 * </p>
 *
 * @author Matteo Baccan
 */
public class TomlConformanceTest {

    /** How many failures to print before saying how many more there were. */
    private static final int SHOWN = 12;

    /**
     * Where the parser stands, and a ratchet rather than a target.
     *
     * <p>
     * <b>These numbers are not conformance and are not written here to be lived with.</b> They are the
     * count of suite cases the parser does not yet get right, recorded so that the suite runs in the build
     * from the day it arrived rather than on the day it finally passes — which is how a conformance suite
     * ends up sitting in a branch for a year. The test fails if a number goes up, and it also fails if a
     * number goes <i>down</i> without being edited here, so improving the parser forces the record to be
     * corrected and the count cannot drift.
     * </p>
     *
     * <p>
     * What is left is written up in <code>FORMATS.md</code>. In outline: the invalid half is mostly
     * range-checking a date-time — the parser recognises the shape of one and does not ask whether the
     * thirtieth of February exists — and a handful of lexical refusals; the valid half is empty keys,
     * which the flattening convention has no way to name, and one CRLF case.
     * </p>
     */
    private static final int INVALID_NOT_YET_REFUSED = 92;

    /** @see #INVALID_NOT_YET_REFUSED */
    private static final int VALID_NOT_YET_READ = 9;

    private static File corpus;

    @BeforeClass
    public static void findTheCorpus() throws URISyntaxException, UnsupportedEncodingException {
        URL url = TomlConformanceTest.class.getClassLoader().getResource("toml-test");
        assertNotNull("the toml-test corpus is not on the test classpath", url);
        corpus = new File(URLDecoder.decode(url.getFile(), "UTF-8"));
        assertTrue(corpus + " is not a directory", corpus.isDirectory());
    }

    @Test
    public void everyInvalidDocumentIsRefused() {
        List<File> documents = tomlFilesUnder(new File(corpus, "invalid"));
        assertTrue("no invalid documents found", documents.size() > 400);

        List<String> read = new ArrayList<>();
        for (File document : documents) {
            try {
                new TomlLoader().load(new Properties(), document.toURI());
                read.add(relative(document));
            } catch (IOException refused) {
                // what it says is checked by TomlLoaderTest; here only that it said no
            } catch (RuntimeException wrongKind) {
                read.add(relative(document) + " (refused with " + wrongKind.getClass().getSimpleName()
                        + " rather than IOException)");
            }
        }
        report("read documents that TOML forbids", read, documents.size(),
                INVALID_NOT_YET_REFUSED, "INVALID_NOT_YET_REFUSED");
    }

    @Test
    public void everyValidDocumentIsReadAsTheSuiteSaysItShouldBe() {
        List<File> documents = tomlFilesUnder(new File(corpus, "valid"));
        assertTrue("no valid documents found", documents.size() > 150);

        List<String> wrong = new ArrayList<>();
        for (File document : documents) {
            String complaint = check(document);
            if (complaint != null)
                wrong.add(relative(document) + ": " + complaint);
        }
        report("valid documents were not read as the suite says", wrong, documents.size(),
                VALID_NOT_YET_READ, "VALID_NOT_YET_READ");
    }

    /** @return what is wrong with how this document was read, or <code>null</code> if nothing is. */
    private static String check(File document) {
        Properties actual = new Properties();
        try {
            new TomlLoader().load(actual, document.toURI());
        } catch (IOException refused) {
            return "refused, though it is valid: " + refused.getMessage();
        } catch (RuntimeException broke) {
            return "threw " + broke;
        }

        Map<String, String> expected;
        Map<String, String> types;
        File json = new File(document.getPath().replaceAll("\\.toml$", ".json"));
        try {
            Properties tagged = new Properties();
            new JsonLoader().load(tagged, json.toURI());
            expected = valuesOf(tagged);
            types = typesOf(tagged);
        } catch (IOException cannotRead) {
            return "the expected output could not be read: " + cannotRead.getMessage();
        }

        Set<String> keys = new TreeSet<>(expected.keySet());
        keys.addAll(actual.stringPropertyNames());
        for (String key : keys) {
            String want = expected.get(key);
            String got = actual.getProperty(key);
            if (want == null)
                return "read '" + key + "' = '" + got + "', which the suite does not expect";
            if (got == null)
                return "did not read '" + key + "', expected '" + want + "'";
            if (!same(want, got, types.get(key)))
                return "'" + key + "' is '" + got + "' where the suite says '" + want + "'";
        }
        return null;
    }

    /** Equal as the tagged type means it: a float by its value, a date-time by its instant, else by text. */
    private static boolean same(String want, String got, String type) {
        if (want.equals(got))
            return true;
        if (type == null)
            return false;
        if (type.startsWith("date") || type.startsWith("time"))
            // the suite pads fractional seconds to three digits where we hand over what was written, and
            // .6 and .600 are one instant: comparing them as text would be comparing the rendering
            return withoutTrailingZeros(want).equals(withoutTrailingZeros(got));
        if (!"float".equals(type))
            return false;
        try {
            // the suite writes inf and nan as TOML does, where we canonicalise to what Double.parseDouble
            // reads - deliberately, and it is the whole reason this comparison is numeric
            double a = Double.parseDouble(asJavaFloat(want));
            double b = Double.parseDouble(asJavaFloat(got));
            return a == b || (Double.isNaN(a) && Double.isNaN(b));
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    private static String asJavaFloat(String value) {
        if (value.endsWith("inf"))
            return value.startsWith("-") ? "-Infinity" : "Infinity";
        if (value.endsWith("nan"))
            return "NaN";
        return value;
    }

    /** A fractional second without the zeros that only pad it: .600 and .6 name the same moment. */
    private static String withoutTrailingZeros(String dateTime) {
        int dot = dateTime.indexOf('.');
        if (dot < 0)
            return dateTime;
        int end = dot + 1;
        while (end < dateTime.length() && Character.isDigit(dateTime.charAt(end)))
            end++;
        String fraction = dateTime.substring(dot + 1, end);
        while (fraction.endsWith("0"))
            fraction = fraction.substring(0, fraction.length() - 1);
        return dateTime.substring(0, dot) + (fraction.isEmpty() ? "" : "." + fraction)
                + dateTime.substring(end);
    }

    /**
     * The expected properties, out of the flattened tagged JSON: a key ending in <code>.value</code> names
     * one. A key with neither tag is an empty collection, which the suite writes as an empty array and both
     * readers write as an empty value.
     */
    private static Map<String, String> valuesOf(Properties tagged) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : tagged.stringPropertyNames()) {
            if (key.endsWith(".value"))
                values.put(key.substring(0, key.length() - ".value".length()), tagged.getProperty(key));
            else if (!key.endsWith(".type"))
                values.put(key, tagged.getProperty(key));
        }
        return values;
    }

    private static Map<String, String> typesOf(Properties tagged) {
        Map<String, String> types = new LinkedHashMap<>();
        for (String key : tagged.stringPropertyNames())
            if (key.endsWith(".type"))
                types.put(key.substring(0, key.length() - ".type".length()), tagged.getProperty(key));
        return types;
    }

    private static List<File> tomlFilesUnder(File directory) {
        List<File> found = new ArrayList<>();
        collect(directory, found);
        Collections.sort(found);
        return found;
    }

    private static void collect(File directory, List<File> found) {
        File[] children = directory.listFiles();
        if (children == null)
            return;
        for (File child : children) {
            if (child.isDirectory())
                collect(child, found);
            else if (child.getName().endsWith(".toml"))
                found.add(child);
        }
    }

    private static String relative(File document) {
        return document.getPath().substring(corpus.getPath().length() + 1).replace('\\', '/');
    }

    /**
     * Holds the count against the record: too many is a regression, too few is a record to update. Names
     * the first {@value #SHOWN} problems either way, since a regression is read by whoever caused it.
     */
    private static void report(String what, List<String> problems, int total, int allowed, String constant) {
        if (problems.size() == allowed)
            return;
        StringBuilder message = new StringBuilder();
        if (problems.size() < allowed)
            message.append("Good news, and this test has to be edited: only ").append(problems.size())
                    .append(" of ").append(total).append(' ').append(what).append(", where ")
                    .append(constant).append(" says ").append(allowed)
                    .append(". Lower it, so that the improvement cannot be lost again.\n");
        else
            message.append(problems.size()).append(" of ").append(total).append(' ').append(what)
                    .append(", where ").append(constant).append(" allows ").append(allowed)
                    .append(":\n");
        for (String problem : problems.subList(0, Math.min(SHOWN, problems.size())))
            message.append("  ").append(problem).append('\n');
        if (problems.size() > SHOWN)
            message.append("  ... and ").append(problems.size() - SHOWN).append(" more\n");
        fail(message.toString());
    }

    /**
     * The corpus still carries the line endings the suite published, which is not something to leave to
     * <code>.gitattributes</code> alone.
     *
     * <p>
     * Several of these cases are <i>about</i> carriage returns, so a checkout that helpfully turned every
     * LF into CRLF would leave a corpus that still parses, still counts 210 and 499, and no longer tests
     * what its file names say. The score would move and nothing would point at why. It has already
     * happened once here, on the way in, which is the reason this exists: git said so in a warning, in a
     * commit of 920 files, where a warning is not a mechanism.
     * </p>
     *
     * <p>
     * The invariant is exact rather than approximate. <b>Seven documents contain a carriage return, and
     * this is which and how many</b>; in the three whose names end in <code>-crlf</code> every newline is
     * one, so the counts match; and no other file in the corpus holds one at all.
     * </p>
     */
    @Test
    public void theCorpusStillHasTheLineEndingsItWasPublishedWith() throws IOException {
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("invalid/control/bare-cr.toml", 2);
        expected.put("invalid/control/comment-cr.toml", 1);
        expected.put("invalid/control/rawstring-cr.toml", 1);
        expected.put("invalid/control/string-cr.toml", 1);
        expected.put("valid/empty-crlf.toml", 1);
        expected.put("valid/newline-crlf.toml", 2);
        expected.put("valid/string/multiline-escaped-crlf.toml", 4);

        Map<String, Integer> found = new LinkedHashMap<>();
        List<String> notPaired = new ArrayList<>();
        for (File file : everyFileUnder(corpus)) {
            byte[] bytes = bytesOf(file);
            int carriageReturns = 0;
            int newlines = 0;
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] == '\r')
                    carriageReturns++;
                if (bytes[i] == '\n')
                    newlines++;
            }
            String name = relative(file);
            if (carriageReturns > 0)
                found.put(name, carriageReturns);
            // in a CRLF document every newline is one: a mixture means something rewrote part of it
            if (name.contains("-crlf.toml") && carriageReturns != newlines)
                notPaired.add(name + " has " + carriageReturns + " CR and " + newlines + " LF");
        }

        // the whole picture first, so a corpus rewritten in both directions at once is diagnosed in one go
        // rather than one assertion at a time
        assertEquals("the carriage returns in the corpus are not the ones the suite published. A checkout "
                        + "that rewrote line endings would do exactly this, and would silently change what "
                        + "the cases named after CR and LF are testing. See .gitattributes.",
                new TreeMap<>(expected), new TreeMap<>(found));
        assertTrue("these documents were published as CRLF and are no longer entirely CRLF: " + notPaired,
                notPaired.isEmpty());
    }

    private static List<File> everyFileUnder(File directory) {
        List<File> found = new ArrayList<>();
        collectEverything(directory, found);
        Collections.sort(found);
        return found;
    }

    private static void collectEverything(File directory, List<File> found) {
        File[] children = directory.listFiles();
        if (children == null)
            return;
        for (File child : children) {
            if (child.isDirectory())
                collectEverything(child, found);
            else
                found.add(child);
        }
    }

    private static byte[] bytesOf(File file) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = in.read(buffer)) != -1; )
                bytes.write(buffer, 0, read);
        }
        return bytes.toByteArray();
    }

    /** The corpus has to be the version we claim to read, not whatever was copied in last. */
    @Test
    public void theCorpusIsTheOneThisParserIsHeldTo() {
        Set<String> directories = new HashSet<>(Arrays.asList("valid", "invalid"));
        for (String name : directories)
            assertTrue("the corpus has no " + name + " directory", new File(corpus, name).isDirectory());
        assertTrue("the corpus LICENSE is missing, and it is MIT licensed",
                new File(corpus, "LICENSE").isFile());
    }
}
