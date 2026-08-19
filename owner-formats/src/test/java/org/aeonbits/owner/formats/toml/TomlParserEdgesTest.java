/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.formats.toml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * The corners of {@link TomlParser} that neither {@link TomlLoaderTest} nor
 * {@link TomlConformanceTest} reaches.
 * <p>
 * The conformance suite is the broad check and it is thorough about what TOML <i>is</i>. What it does not
 * exercise is the arithmetic of reading it: how many quotes end a multi-line string, whether a carriage
 * return arrived with a line feed behind it, whether the four characters after a <code>T</code> are a
 * time. Those are the branches that decide whether a malformed document is <b>declined or misread</b>,
 * and a suite of valid documents never visits them.
 * </p>
 *
 * @author Matteo Baccan
 */
public class TomlParserEdgesTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final TomlLoader loader = new TomlLoader();
    private int documents;

    private Properties read(String document) throws IOException {
        File file = folder.newFile("edges" + documents++ + ".toml");
        try (Writer out = new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            out.write(document);
        }
        Properties result = new Properties();
        loader.load(result, file.toURI());
        return result;
    }

    private String refused(String document) {
        try {
            read(document);
            return null;
        } catch (IOException expected) {
            return expected.getMessage();
        }
    }

    // ---------------------------------------------------------------- numbers written with a prefix

    /**
     * A radix prefix with nothing usable after it, in all three radixes and both spellings of nothing.
     * <p>
     * The assertion is that each is <b>refused</b> and not which sentence says so, because two earlier
     * rules get there first and they are the right ones: a bare <code>0x</code> is a zero followed by
     * something, so the leading-zero rule names it, and <code>0x_</code> is an underscore that is not
     * between two digits. The parser's own "no digits after its prefix" appears to be unreachable behind
     * those two — left in place, since a check that cannot fire costs nothing and a radix added later
     * would need it.
     * </p>
     */
    @Test
    public void aRadixPrefixWithNothingUsableAfterItIsRefused() {
        for (String prefix : new String[] {"0x", "0o", "0b"}) {
            assertNotNull(prefix, refused("value = " + prefix + "\n"));
            assertNotNull(prefix + "_", refused("value = " + prefix + "_\n"));
        }
    }

    /** A sign belongs to a decimal number and not to a prefixed one, where it is not part of the grammar. */
    @Test
    public void aSignOnAPrefixedNumberIsRefused() {
        assertNotNull(refused("value = 0x+1f\n"));
        assertNotNull(refused("value = 0b-101\n"));
    }

    /** The three radixes, read. The underscore is a separator in all of them. */
    @Test
    public void theThreeRadixesAreRead() throws IOException {
        Properties p = read("hex = 0xdead_beef\noct = 0o755\nbin = 0b1010_1010\n");
        assertEquals(String.valueOf(0xdeadbeefL), p.getProperty("hex"));
        assertEquals("493", p.getProperty("oct"));
        assertEquals("170", p.getProperty("bin"));
    }

    // ---------------------------------------------------------------- what ends a line

    /** A document written on Windows reads the same as one written anywhere else. */
    @Test
    public void aDocumentWithWindowsLineEndingsReadsTheSame() throws IOException {
        Properties p = read("name = \"owner\"\r\n[server]\r\nhost = \"localhost\"\r\n");
        assertEquals("owner", p.getProperty("name"));
        assertEquals("localhost", p.getProperty("server.host"));
    }

    /**
     * A carriage return with no line feed behind it is not a line ending, and TOML says so: it is refused
     * rather than treated as one. A file half-converted between platforms is the way this arrives.
     */
    @Test
    public void aLoneCarriageReturnIsRefused() {
        assertNotNull(refused("name = \"owner\"\rother = \"value\"\n"));
    }

    // ---------------------------------------------------------------- strings, and how they end

    /**
     * A multi-line basic string ends at three quotes, and up to two more belong to the value — which is
     * how a string ending in a quotation mark is written at all.
     */
    @Test
    public void aMultiLineStringMayEndWithQuotesOfItsOwn() throws IOException {
        Properties p = read("a = \"\"\"he said \"\"\"\"\n");
        assertEquals("he said \"", p.getProperty("a"));

        Properties two = read("b = \"\"\"and then \"\"\"\"\"\n");
        assertEquals("and then \"\"", two.getProperty("b"));
    }

    /** The same for a literal string, where nothing inside it is an escape. */
    @Test
    public void aMultiLineLiteralStringMayEndWithQuotesOfItsOwn() throws IOException {
        Properties p = read("a = '''it's here''''\n");
        assertEquals("it's here'", p.getProperty("a"));
    }

    /**
     * A literal control character inside a basic string is refused. It is not an escaping question — the
     * character cannot be there at all, and a file carrying one was written by something that did not
     * know that.
     */
    @Test
    public void aControlCharacterInsideAStringIsRefused() {
        assertNotNull(refused("a = \"before" + (char) 1 + "after\"\n"));
    }

    // ---------------------------------------------------------------- dates and times

    /** The four date-time shapes TOML has, each read as the text it was written with. */
    @Test
    public void theFourDateTimeShapesAreRead() throws IOException {
        Properties p = read("odt = 1979-05-27T07:32:00Z\n"
                + "ldt = 1979-05-27T07:32:00\n"
                + "ld  = 1979-05-27\n"
                + "lt  = 07:32:00\n");
        assertEquals("1979-05-27T07:32:00Z", p.getProperty("odt"));
        assertEquals("1979-05-27T07:32:00", p.getProperty("ldt"));
        assertEquals("1979-05-27", p.getProperty("ld"));
        assertEquals("07:32:00", p.getProperty("lt"));
    }

    /** Fractional seconds, which are optional and of any length. */
    @Test
    public void fractionalSecondsAreRead() throws IOException {
        Properties p = read("a = 07:32:00.5\nb = 1979-05-27T07:32:00.999999Z\n");
        assertEquals("07:32:00.5", p.getProperty("a"));
        assertEquals("1979-05-27T07:32:00.999999Z", p.getProperty("b"));
    }

    /** A dot with no digits after it is not a fraction. */
    @Test
    public void aFractionWithNoDigitsIsRefused() {
        assertNotNull(refused("a = 07:32:00.\n"));
    }

    /** An offset is a sign, two digits, a colon and two digits, and anything else is not one. */
    @Test
    public void aMalformedOffsetIsRefused() {
        assertNotNull(refused("a = 1979-05-27T07:32:00+0100\n"));
        assertNotNull(refused("a = 1979-05-27T07:32:00+1:00\n"));
        assertNotNull(refused("a = 1979-05-27T07:32:00 07:00\n"));
    }

    /** A well formed one, both signs, so that the refusals above are not refusing everything. */
    @Test
    public void aWellFormedOffsetIsRead() throws IOException {
        Properties p = read("a = 1979-05-27T07:32:00+01:00\nb = 1979-05-27T07:32:00-08:00\n");
        assertEquals("1979-05-27T07:32:00+01:00", p.getProperty("a"));
        assertEquals("1979-05-27T07:32:00-08:00", p.getProperty("b"));
    }

    /**
     * A space may stand in for the <code>T</code> — TOML allows it — and the parser therefore has to tell
     * a date followed by a time from a date followed by a comment or by the next key.
     */
    @Test
    public void aSpaceMayStandInForTheT() throws IOException {
        Properties p = read("a = 1979-05-27 07:32:00Z\nb = 1979-05-27 # just the date\n");
        // and it comes back with the T, not with the space: the value is normalised to one spelling, so
        // two files written differently hand a configuration the same string
        assertEquals("1979-05-27T07:32:00Z", p.getProperty("a"));
        assertEquals("1979-05-27", p.getProperty("b"));
    }

    // ---------------------------------------------------------------- floats that are not numbers

    /**
     * Infinity and not-a-number are TOML floats, and they arrive as <b>Java</b> spells them — which is
     * what a <code>double</code> method parses back, and what the TOML spelling would not be.
     */
    @Test
    public void infinityAndNotANumberAreRead() throws IOException {
        Properties p = read("a = inf\nb = -inf\nc = nan\n");
        assertEquals("Infinity", p.getProperty("a"));
        assertEquals("-Infinity", p.getProperty("b"));
        assertEquals("NaN", p.getProperty("c"));
    }

    /** A decimal float so large it becomes infinite is refused rather than quietly becoming one. */
    @Test
    public void aFloatThatOverflowsIsRefused() {
        assertNotNull(refused("a = 1e400\n"));
    }

    // ---------------------------------------------------------------- keys already spoken for

    /**
     * The three ways a name can already be taken — a value, a table, and a table written out in full —
     * are one check, and each of them has to reach it.
     */
    @Test
    public void aNameAlreadyTakenIsRefused() {
        assertNotNull(refused("a = 1\na = 2\n"));
        assertNotNull(refused("[a]\nb = 1\n[a]\nc = 2\n"));
        assertNotNull(refused("a = 1\n[a]\nb = 2\n"));
    }

    /** A dotted key runs as far as the bare names go, and stops at the first thing that is not one. */
    @Test
    public void aDottedKeyRunsAsFarAsTheBareNamesGo() throws IOException {
        Properties p = read("a.b.c = 1\na.b.\"d e\" = 2\n");
        assertEquals("1", p.getProperty("a.b.c"));
        assertEquals("2", p.getProperty("a.b.d e"));
    }
}
