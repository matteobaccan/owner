/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.formats.json;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * RFC 8259, and the three decisions it leaves to whoever reads it: what a <code>null</code> becomes, what
 * an empty array becomes, and what happens to a name given twice. Every one of them is a choice rather
 * than a fact, and <code>FORMATS.md</code> records why each went the way it did.
 *
 * @author Matteo Baccan
 */
public class JsonLoaderTest {

    private static Properties read(String json) throws IOException {
        File file = Files.createTempFile("owner-json", ".json").toFile();
        file.deleteOnExit();
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            out.write(json.getBytes(UTF_8));
        }
        Properties result = new Properties();
        new JsonLoader().load(result, file.toURI());
        return result;
    }

    private static String refused(String json) {
        try {
            read(json);
            return null;
        } catch (IOException expected) {
            return expected.getMessage();
        }
    }

    // -------------------------------------------------------------------------------------------------
    // what it answers for
    // -------------------------------------------------------------------------------------------------

    @Test
    public void itAnswersForItsOwnExtension() throws URISyntaxException {
        assertTrue(new JsonLoader().accept(new URI("file:/app/config.json")));
        assertTrue(new JsonLoader().accept(new URI("file:/app/CONFIG.JSON")));
        assertTrue(new JsonLoader().accept(new URI("http://host/app.json?v=2")));
        assertFalse(new JsonLoader().accept(new URI("file:/app/config.properties")));
        assertFalse(new JsonLoader().accept(new URI("file:/app/config.xml")));
    }

    @Test
    public void itIsLookedForWhenNoSourcesAreDeclared() {
        assertEquals("classpath:MyConfig.json", new JsonLoader().defaultSpecFor("classpath:MyConfig"));
    }

    // -------------------------------------------------------------------------------------------------
    // the shape of a document becomes the shape of the keys
    // -------------------------------------------------------------------------------------------------

    @Test
    public void anObjectBecomesAPrefix() throws IOException {
        Properties props = read("{\"server\": {\"host\": \"localhost\", \"port\": 8080}}");

        assertEquals("localhost", props.getProperty("server.host"));
        assertEquals("8080", props.getProperty("server.port"));
    }

    @Test
    public void anArrayBecomesIndexedKeys() throws IOException {
        Properties props = read("{\"hosts\": [\"alpha\", \"beta\"]}");

        assertEquals("alpha", props.getProperty("hosts[0]"));
        assertEquals("beta", props.getProperty("hosts[1]"));
    }

    /** The shape this whole feature was waiting for: a list of objects, which is most JSON documents. */
    @Test
    public void anArrayOfObjectsBecomesIndexedSections() throws IOException {
        Properties props = read("{\"servers\": [{\"host\": \"alpha\", \"port\": 1}, {\"host\": \"beta\"}]}");

        assertEquals("alpha", props.getProperty("servers[0].host"));
        assertEquals("1", props.getProperty("servers[0].port"));
        assertEquals("beta", props.getProperty("servers[1].host"));
    }

    @Test
    public void nestingGoesAsDeepAsTheDocument() throws IOException {
        Properties props = read("{\"a\": {\"b\": {\"c\": [[1, 2]]}}}");

        assertEquals("1", props.getProperty("a.b.c[0][0]"));
        assertEquals("2", props.getProperty("a.b.c[0][1]"));
    }

    // -------------------------------------------------------------------------------------------------
    // values are kept as they were written
    // -------------------------------------------------------------------------------------------------

    @Test
    public void aNumberIsKeptExactlyAsWritten() throws IOException {
        Properties props = read("{\"a\": 1e3, \"b\": -0.500, \"c\": 9007199254740993, \"d\": 1E+2}");

        assertEquals("a number read into a double and printed back would say 1000.0", "1e3",
                props.getProperty("a"));
        assertEquals("-0.500", props.getProperty("b"));
        assertEquals("past 2^53, and every digit still there", "9007199254740993", props.getProperty("c"));
        assertEquals("1E+2", props.getProperty("d"));
    }

    @Test
    public void trueAndFalseAreTheirOwnWords() throws IOException {
        Properties props = read("{\"a\": true, \"b\": false}");

        assertEquals("true", props.getProperty("a"));
        assertEquals("false", props.getProperty("b"));
    }

    @Test
    public void theSixEscapesAndTheUnicodeOneAreExpanded() throws IOException {
        Properties props = read("{\"a\": \"one\\ntwo\", \"b\": \"a\\\"b\", \"c\": \"a\\\\b\","
                + " \"d\": \"a\\/b\", \"e\": \"\\u00e8\", \"f\": \"\\b\\f\\r\\t\"}");

        assertEquals("one\ntwo", props.getProperty("a"));
        assertEquals("a\"b", props.getProperty("b"));
        assertEquals("a\\b", props.getProperty("c"));
        assertEquals("a/b", props.getProperty("d"));
        assertEquals("è", props.getProperty("e"));
        assertEquals("\b\f\r\t", props.getProperty("f"));
    }

    /** Two escapes, two chars, one character: a Java string is UTF-16 and needs nothing done to it. */
    @Test
    public void aSurrogatePairSurvivesAsOneCharacter() throws IOException {
        Properties props = read("{\"a\": \"\\uD83D\\uDE00\"}");

        assertEquals("\uD83D\uDE00", props.getProperty("a"));
        assertEquals(1, props.getProperty("a").codePointCount(0, props.getProperty("a").length()));
    }

    @Test
    public void theDocumentIsReadAsUtf8WhateverThePlatformUses() throws IOException {
        assertEquals("città", read("{\"a\": \"città\"}").getProperty("a"));
    }

    // -------------------------------------------------------------------------------------------------
    // the three that RFC 8259 leaves to us
    // -------------------------------------------------------------------------------------------------

    @Test
    public void aNullWritesNothingAtAll() throws IOException {
        Properties props = read("{\"proxy\": null, \"host\": \"localhost\"}");

        assertNull("Properties cannot hold a null, so the key is simply not there",
                props.getProperty("proxy"));
        assertFalse(props.containsKey("proxy"));
        assertEquals("and it stops nothing else being read", "localhost", props.getProperty("host"));
    }

    @Test
    public void anEmptyArrayIsAnEmptyValue() throws IOException {
        Properties props = read("{\"servers\": [], \"other\": {}}");

        assertEquals("an empty value is already read as an empty collection", "",
                props.getProperty("servers"));
        assertNull("a section with nothing in it has nothing to say", props.getProperty("other"));
    }

    @Test
    public void aNameGivenTwiceIsRefused() {
        String message = refused("{\"a\": 1, \"b\": 2, \"a\": 3}");

        assertTrue(String.valueOf(message), message.contains("'a' is given twice"));
        assertTrue("and it says where: " + message, message.contains("column"));
    }

    @Test
    public void theSameNameUnderTwoObjectsIsNotARepeat() throws IOException {
        Properties props = read("{\"a\": {\"host\": 1}, \"b\": {\"host\": 2}}");

        assertEquals("1", props.getProperty("a.host"));
        assertEquals("2", props.getProperty("b.host"));
    }

    // -------------------------------------------------------------------------------------------------
    // what is refused, and where
    // -------------------------------------------------------------------------------------------------

    @Test
    public void aDocumentThatIsNotAnObjectIsRefused() {
        assertTrue(String.valueOf(refused("[1, 2]")), refused("[1, 2]").contains("JSON object"));
        assertTrue(String.valueOf(refused("\"just a string\"")),
                refused("\"just a string\"").contains("JSON object"));
        assertTrue(String.valueOf(refused("")), refused("").contains("JSON object"));
    }

    /** JSON5 and JavaScript, not JSON: a file this accepted would be one other tools refuse. */
    @Test
    public void whatIsJavaScriptAndNotJsonIsRefused() {
        assertTrue(String.valueOf(refused("{a: 1}")), refused("{a: 1}").contains("'\"' was expected"));
        assertTrue(String.valueOf(refused("{'a': 1}")), refused("{'a': 1}").contains("'\"' was expected"));
        assertTrue(String.valueOf(refused("{\"a\": 1,}")), refused("{\"a\": 1,}").contains("'\"' was expected"));
        assertTrue(String.valueOf(refused("{\"a\": 1} // comment")),
                refused("{\"a\": 1} // comment").contains("more text after the end"));
        assertTrue(String.valueOf(refused("{\"a\": 01}")),
                refused("{\"a\": 01}").contains("cannot begin with a zero"));
    }

    @Test
    public void aBrokenStringSaysWhatIsWrongWithIt() {
        assertTrue(String.valueOf(refused("{\"a\": \"unclosed}")),
                refused("{\"a\": \"unclosed}").contains("never closed"));
        assertTrue(String.valueOf(refused("{\"a\": \"\\q\"}")),
                refused("{\"a\": \"\\q\"}").contains("is not an escape"));
        assertTrue(String.valueOf(refused("{\"a\": \"\\u12\"}")),
                refused("{\"a\": \"\\u12\"}").contains("hexadecimal"));
        assertTrue(String.valueOf(refused("{\"a\": \"\u0001\"}")),
                refused("{\"a\": \"\u0001\"}").contains("control character"));
    }

    @Test
    public void aBrokenNumberSaysWhatIsWrongWithIt() {
        assertTrue(String.valueOf(refused("{\"a\": -}")), refused("{\"a\": -}").contains("after its sign"));
        assertTrue(String.valueOf(refused("{\"a\": 1.}")),
                refused("{\"a\": 1.}").contains("after its decimal point"));
        assertTrue(String.valueOf(refused("{\"a\": 1e}")),
                refused("{\"a\": 1e}").contains("after its exponent"));
    }

    /** Line and column, because a message that sends the reader searching by hand is half a message. */
    @Test
    public void everyComplaintSaysWhereItIs() {
        String message = refused("{\n  \"a\": 1,\n  \"b\": nope\n}");

        assertTrue(String.valueOf(message), message.contains("Line 3"));
        assertTrue(String.valueOf(message), message.contains("column"));
    }

    @Test
    public void aByteOrderMarkIsNotPartOfTheDocument() throws IOException {
        assertEquals("1", read("\uFEFF{\"a\": 1}").getProperty("a"));
    }

    @Test
    public void itTakesNoOptions() throws Exception {
        File file = Files.createTempFile("owner-json", ".json").toFile();
        file.deleteOnExit();
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            out.write("{\"a\": 1}".getBytes(UTF_8));
        }
        try {
            new JsonLoader().load(new Properties(), new URI(file.toURI() + "#dialect=whatever"));
            fail("JSON has no dialects: it is the one format that is actually specified");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("takes none"));
        }
    }
}
