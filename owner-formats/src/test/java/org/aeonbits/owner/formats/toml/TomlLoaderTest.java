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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * What a TOML document flattens to, and what it refuses.
 *
 * <p>
 * The conformance suite in {@link TomlConformanceTest} is the broad check; these are the decisions of ours
 * that the suite cannot see, because they are about the keys we emit rather than about TOML.
 * </p>
 *
 * @author Matteo Baccan
 */
public class TomlLoaderTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final TomlLoader loader = new TomlLoader();
    private int documents;

    @Test
    public void acceptsTomlAndNothingElse() {
        assertTrue(loader.accept(URI.create("file:/etc/app.toml")));
        assertTrue(loader.accept(URI.create("file:/etc/APP.TOML")));
        assertFalse(loader.accept(URI.create("file:/etc/app.json")));
        assertFalse(loader.accept(URI.create("")));
    }

    @Test
    public void theDefaultSpecIsTheNameWithTomlOnIt() {
        assertEquals("classpath:MyConfig.toml", loader.defaultSpecFor("classpath:MyConfig"));
    }

    // ------------------------------------------------------------ the shape

    @Test
    public void aTableIsAPrefix() throws Exception {
        Properties read = read("[server]\nhost = \"localhost\"\nport = 8080");
        assertEquals("localhost", read.get("server.host"));
        assertEquals("8080", read.get("server.port"));
    }

    @Test
    public void aDottedKeyIsTheFlatteningItself() throws Exception {
        assertEquals("localhost", read("server.host = \"localhost\"").get("server.host"));
    }

    @Test
    public void anArrayOfTablesIsIndexed() throws Exception {
        Properties read = read("[[servers]]\nhost = \"alpha\"\n\n[[servers]]\nhost = \"beta\"");
        assertEquals("alpha", read.get("servers[0].host"));
        assertEquals("beta", read.get("servers[1].host"));
    }

    @Test
    public void anArrayOfValuesIsIndexed() throws Exception {
        Properties read = read("ports = [80, 443]");
        assertEquals("80", read.get("ports[0]"));
        assertEquals("443", read.get("ports[1]"));
    }

    @Test
    public void anEmptyArrayIsAnEmptyValue() throws Exception {
        assertEquals("", read("servers = []").get("servers"));
    }

    @Test
    public void anInlineTableNests() throws Exception {
        Properties read = read("server = { host = \"localhost\", port = 8080 }");
        assertEquals("localhost", read.get("server.host"));
        assertEquals("8080", read.get("server.port"));
    }

    @Test
    public void anArrayOfInlineTablesIsBoth() throws Exception {
        Properties read = read("servers = [ { host = \"alpha\" }, { host = \"beta\" } ]");
        assertEquals("alpha", read.get("servers[0].host"));
        assertEquals("beta", read.get("servers[1].host"));
    }

    @Test
    public void aQuotedKeyKeepsItsDots() throws Exception {
        assertEquals("value", read("\"quoted.key\" = \"value\"").get("quoted.key"));
    }

    @Test
    public void anArraySpansLinesAndTakesATrailingComma() throws Exception {
        Properties read = read("ports = [\n  80,   # http\n  443,  # https\n]");
        assertEquals("80", read.get("ports[0]"));
        assertEquals("443", read.get("ports[1]"));
    }

    // ---------------------------------------------------------- the values

    @Test
    public void theFourSpellingsOfAnIntegerBecomeOne() throws Exception {
        Properties read = read("a = 1_000\nb = 0xDEADBEEF\nc = 0o755\nd = 0b1101");
        assertEquals("1000", read.get("a"));
        assertEquals("3735928559", read.get("b"));
        assertEquals("493", read.get("c"));
        assertEquals("13", read.get("d"));
    }

    @Test
    public void infinityAndNotANumberAreWrittenTheWayJavaReadsThem() throws Exception {
        Properties read = read("a = inf\nb = -inf\nc = +inf\nd = nan");
        assertEquals("Infinity", read.get("a"));
        assertEquals("-Infinity", read.get("b"));
        assertEquals("Infinity", read.get("c"));
        assertEquals("NaN", read.get("d"));
    }

    @Test
    public void anOrdinaryNumberIsKeptAsWritten() throws Exception {
        Properties read = read("a = 3.1415\nb = 5e+22\nc = -0.01\nd = 42");
        assertEquals("3.1415", read.get("a"));
        assertEquals("5e+22", read.get("b"));
        assertEquals("-0.01", read.get("c"));
        assertEquals("42", read.get("d"));
    }

    @Test
    public void aDateTimeWrittenWithASpaceGetsItsT() throws Exception {
        // TOML allows the space; java.time.LocalDateTime.parse does not
        assertEquals("1979-05-27T07:32:00", read("d = 1979-05-27 07:32:00").get("d"));
        assertEquals("1979-05-27T07:32:00Z", read("d = 1979-05-27T07:32:00Z").get("d"));
        assertEquals("1979-05-27", read("d = 1979-05-27").get("d"));
        assertEquals("07:32:00", read("d = 07:32:00").get("d"));
    }

    @Test
    public void theStringFormsAllRead() throws Exception {
        assertEquals("a\tb", read("s = \"a\\tb\"").get("s"));
        assertEquals("a\\tb", read("s = 'a\\tb'").get("s"));
        assertEquals("one\ntwo", read("s = \"\"\"\none\ntwo\"\"\"").get("s"));
        assertEquals("one\ntwo", read("s = '''\none\ntwo'''").get("s"));
    }

    @Test
    public void aLineEndingBackslashJoinsTheLines() throws Exception {
        assertEquals("The quick brown fox.",
                read("s = \"\"\"\nThe quick \\\n     brown \\\n     fox.\"\"\"").get("s"));
    }

    @Test
    public void anEscapeIsResolved() throws Exception {
        assertEquals("\u00e9 \u20ac", read("s = \"\\u00E9 \\u20AC\"").get("s"));
    }

    @Test
    public void aBooleanIsTheWordItself() throws Exception {
        Properties read = read("a = true\nb = false");
        assertEquals("true", read.get("a"));
        assertEquals("false", read.get("b"));
    }

    @Test
    public void aCommentIsNotAValue() throws Exception {
        Properties read = read("# a whole line\nkey = \"value\" # and a trailing one");
        assertEquals("value", read.get("key"));
        assertEquals(1, read.size());
    }

    // --------------------------------------------------------- the refusals

    @Test
    public void aKeyWrittenTwiceIsRefused() throws Exception {
        refuses("a = 1\na = 2", "defined twice");
    }

    @Test
    public void aTableWrittenTwiceIsRefused() throws Exception {
        refuses("[a]\nx = 1\n\n[a]\ny = 2", "defined twice");
    }

    @Test
    public void aTableOverAValueIsRefused() throws Exception {
        refuses("a = 1\n\n[a]\nx = 1", "cannot be a table");
    }

    @Test
    public void extendingAnInlineTableIsRefused() throws Exception {
        refuses("a = { x = 1 }\n\n[a.b]\ny = 2", "inline table");
    }

    @Test
    public void anUnderscoreThatIsNotBetweenDigitsIsRefused() throws Exception {
        refuses("a = 1__000", "underscore");
        refuses("a = _1000", "underscore");
        refuses("a = 1000_", "underscore");
    }

    @Test
    public void aLeadingZeroIsRefused() throws Exception {
        refuses("a = 01", "cannot begin with a zero");
    }

    @Test
    public void twoExpressionsOnOneLineAreRefused() throws Exception {
        refuses("a = 1 b = 2", "more on this line");
    }

    @Test
    public void anUnknownEscapeIsRefused() throws Exception {
        refuses("s = \"\\q\"", "is not an escape");
    }

    @Test
    public void anUnclosedStringIsRefused() throws Exception {
        refuses("s = \"open", "not closed");
    }

    @Test
    public void everyComplaintCarriesTheLineAndTheColumn() throws Exception {
        refuses("a = 1\nb = 2\na = 3", "Line 3");
    }

    // ------------------------------------------------- dates that do not exist

    @Test
    public void aDayThatIsNotInTheMonthIsRefused() throws Exception {
        // java.time is asked rather than the month lengths written out again, so a leap year is right
        refuses("d = 2026-02-29", "is not a date that exists");
        refuses("d = 1988-02-30", "is not a date that exists");
        refuses("d = 2026-04-31", "is not a date that exists");
        assertEquals("1988-02-29", read("d = 1988-02-29").get("d"));
    }

    @Test
    public void aMonthOrADayOutsideItsRangeIsRefused() throws Exception {
        refuses("d = 2026-13-01", "is not a date that exists");
        refuses("d = 2026-00-01", "is not a date that exists");
        refuses("d = 2026-01-00", "is not a date that exists");
    }

    @Test
    public void anHourMinuteOrSecondOutsideItsRangeIsRefused() throws Exception {
        refuses("d = 2026-01-01T24:00:00Z", "hour 24");
        refuses("d = 2026-01-01T00:60:00Z", "minute 60");
        refuses("d = 2026-01-01T00:00:61Z", "second 61");
    }

    @Test
    public void aLeapSecondIsAllowed() throws Exception {
        // 60 and not 59: TOML says a document may write one, and this is the one range java.time refuses
        assertEquals("1987-07-05T17:45:60Z", read("d = 1987-07-05T17:45:60Z").get("d"));
    }

    @Test
    public void aYearOutsideFourDigitsIsRefused() throws Exception {
        // five digits is not a year, so it is not a date at all and never reaches the date rules
        refuses("d = 10000-01-01", "is not a number TOML can read");
    }

    @Test
    public void aDateWithoutItsTimeSeparatorIsRefused() throws Exception {
        // with a digit where the T belongs there is no date to complain about, only a value that is not
        // anything TOML has: what matters is that neither is read
        refuses("d = 1987-07-0517:45:00Z", "is not a number TOML can read");
        refuses("d = 2020-01-01x", "no T between its date and its time");
    }

    @Test
    public void anOffsetOutsideItsRangeOrWronglyWrittenIsRefused() throws Exception {
        refuses("d = 2026-01-01T00:00:00+24:00", "offset hour 24");
        refuses("d = 2026-01-01T00:00:00+00:60", "offset minute 60");
        refuses("d = 2026-01-01T00:00:00+07", "offset that is not Z or +hh:mm");
    }

    @Test
    public void aTimeWithoutSecondsOrWithATrailingDotIsRefused() throws Exception {
        refuses("d = 2026-01-01T17:45Z", "not written as hh:mm:ss");
        refuses("d = 2026-01-01T17:45:00.Z", "not a fraction");
    }

    @Test
    public void aLowerCaseTOrZIsTheSameInstant() throws Exception {
        assertEquals("1987-07-05T17:45:00Z", read("d = 1987-07-05t17:45:00z").get("d"));
    }

    // ------------------------------------------ numbers that are not numbers

    @Test
    public void aFloatWithoutDigitsOnBothSidesOfItsDotIsRefused() throws Exception {
        // Double.parseDouble reads all four of these; TOML reads none of them
        refuses("a = .5", "digits on both sides");
        refuses("a = 5.", "digits on both sides");
        refuses("a = 1.e2", "digits on both sides");
        refuses("a = 1e2.3", "exponent that is not a whole number");
    }

    @Test
    public void anUnderscoreInAnExponentOrBesideTheEIsRefused() throws Exception {
        refuses("a = 1e_2", "underscore");
        refuses("a = 1e2_", "underscore");
    }

    @Test
    public void aSignedOrDoubleSignedRadixIntegerIsRefused() throws Exception {
        refuses("a = +0xFF", "cannot be signed");
        refuses("a = 0x-1", "sign after its prefix");
    }

    @Test
    public void anEscapeBeyondTheLastCharacterIsRefusedAsSuch() throws Exception {
        // eight hexadecimal digits overflow an int, and the overflow used to walk past the range check
        refuses("s = \"\\UFFFFFFFF\"", "does not name a character");
        refuses("s = \"\\U0011FFFF\"", "does not name a character");
    }

    @Test
    public void aBareCarriageReturnIsRefused() throws Exception {
        refuses("a = 1\r", "carriage return has to be followed by a newline");
    }

    @Test
    public void aDottedKeyCannotAddToATableAlreadyWrittenOut() throws Exception {
        refuses("[a.b.c]\nz = 9\n\n[a]\nb.c.t = \"no\"", "cannot add to it");
    }

    @Test
    public void anArrayOfTablesCannotBeReopenedAsATable() throws Exception {
        refuses("[[tbl]]\n[tbl]", "array of tables");
    }

    @Test
    public void anArrayNestedInAnArrayOfTablesCountsWithinItsElement() throws Exception {
        Properties read = read("[[a]]\n[[a.b]]\nn = 1\n\n[[a]]\n[[a.b]]\nn = 2");
        assertEquals("1", read.get("a[0].b[0].n"));
        assertEquals("2", read.get("a[1].b[0].n"));
    }

    // ------------------------------------------- the ends nothing reached yet

    /**
     * Written against the coverage report rather than from imagination. Each of these is the only way into
     * a line the conformance suite never touches, because the suite's documents all end tidily — a file
     * that stops in the middle of a key or an escape is a truncated download or a half-written editor
     * buffer, which is the moment a parser most needs to say something rather than fall over.
     */

    @Test
    public void aDocumentThatEndsWhereAKeyShouldBeIsRefused() throws Exception {
        refuses("[", "a key was expected");
        refuses("a = 1\n[", "a key was expected");
    }

    @Test
    public void aDocumentThatEndsInsideAnEscapeIsRefused() throws Exception {
        refuses("s = \"\"\"a\\", "unfinished escape");
    }

    @Test
    public void aDocumentThatEndsInsideAUnicodeEscapeIsRefused() throws Exception {
        refuses("s = \"\"\"a\\u00", "hexadecimal digits");
    }

    @Test
    public void aMultiLineStringMayOpenOnACarriageReturnAndNewline() throws Exception {
        // the newline straight after the delimiter is not part of the string, and on a file written under
        // Windows that newline is two characters
        assertEquals("one", read("s = \"\"\"\r\none\"\"\"").get("s"));
    }

    @Test
    public void aRadixPrefixWithNothingAfterItIsRefused() throws Exception {
        // 0x is two characters, so it never reaches the radix branch and is judged as a decimal
        refuses("a = 0x", "cannot begin with a zero");
        // "has no digits after its prefix" is left without a test: radixInteger is only called for a
        // body longer than the prefix, and withoutUnderscores either refuses or answers with at least one
        // digit, so the empty case it guards against cannot arise. It is dead and this says so.
    }

    @Test
    public void anIntegerTooLargeForItsTypeIsRefused() throws Exception {
        // every digit is a digit and the shape is right, so only the parse can say no
        refuses("a = 99999999999999999999", "is not a value TOML can read");
    }

    @Test
    public void aFloatThatOverflowsIsRefused() throws Exception {
        // TOML has inf, and a number that merely rounds to it is a different thing: the document says a
        // finite value and no finite value is what it says
        refuses("a = 1e400", "is not a number TOML can read");
    }

    @Test
    public void aSignWithNoNumberAfterItIsRefused() throws Exception {
        refuses("a = -", "not a number TOML can read");
    }

    @Test
    public void somethingShorterThanADateIsNotOne() throws Exception {
        // digitsAt has to answer for a string too short to hold what it is being asked about
        refuses("a = 2026-08", "is not a number TOML can read");
        refuses("a = 12:34", "is not a number TOML can read");
    }

    // ------------------------------------------------------------- plumbing

    private void refuses(String document, String expected) throws IOException {
        try {
            Properties read = read(document);
            fail("expected a refusal mentioning '" + expected + "', but it read " + read);
        } catch (IOException refused) {
            assertTrue("the message does not mention '" + expected + "': " + refused.getMessage(),
                    refused.getMessage().contains(expected));
        }
    }

    private Properties read(String document) throws IOException {
        File file = folder.newFile("test" + documents++ + ".toml");
        try (Writer out = new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            out.write(document);
        }
        Properties result = new Properties();
        loader.load(result, file.toURI());
        return result;
    }
}
