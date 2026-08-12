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
