/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.formats.json;

import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Direct unit tests for {@link JsonParser}, covering parsing logic, data structures,
 * scalar types, escape sequences, number formats, and error conditions.
 *
 * @author Matteo Baccan
 */
public class JsonParserTest {

    private Properties parse(String json) throws IOException {
        Properties properties = new Properties();
        new JsonParser(json, properties).parse();
        return properties;
    }

    private String parseExpectingError(String json) {
        try {
            parse(json);
            fail("Expected IOException for input: " + json);
            return null;
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    @Test
    public void testEmptyObject() throws IOException {
        Properties props = parse("{}");
        assertTrue(props.isEmpty());
    }

    @Test
    public void testSimpleKeyValue() throws IOException {
        Properties props = parse("{\"key\": \"value\"}");
        assertEquals("value", props.getProperty("key"));
    }

    @Test
    public void testMultipleProperties() throws IOException {
        Properties props = parse("{\"a\": \"1\", \"b\": \"2\"}");
        assertEquals("1", props.getProperty("a"));
        assertEquals("2", props.getProperty("b"));
    }

    @Test
    public void testNestedObjects() throws IOException {
        Properties props = parse("{\"outer\": {\"inner\": \"value\"}}");
        assertEquals("value", props.getProperty("outer.inner"));
    }

    @Test
    public void testArray() throws IOException {
        Properties props = parse("{\"items\": [\"one\", \"two\", \"three\"]}");
        assertEquals("one", props.getProperty("items[0]"));
        assertEquals("two", props.getProperty("items[1]"));
        assertEquals("three", props.getProperty("items[2]"));
    }

    @Test
    public void testEmptyArray() throws IOException {
        Properties props = parse("{\"empty\": []}");
        assertEquals("", props.getProperty("empty"));
    }

    @Test
    public void testBooleansAndNull() throws IOException {
        Properties props = parse("{\"t\": true, \"f\": false, \"n\": null}");
        assertEquals("true", props.getProperty("t"));
        assertEquals("false", props.getProperty("f"));
        assertNull(props.getProperty("n"));
        assertFalse(props.containsKey("n"));
    }

    @Test
    public void testNumbers() throws IOException {
        Properties props = parse("{\"int\": 42, \"neg\": -17, \"zero\": 0, \"dec\": 3.14159, \"exp1\": 1e10, \"exp2\": 2.5E-3}");
        assertEquals("42", props.getProperty("int"));
        assertEquals("-17", props.getProperty("neg"));
        assertEquals("0", props.getProperty("zero"));
        assertEquals("3.14159", props.getProperty("dec"));
        assertEquals("1e10", props.getProperty("exp1"));
        assertEquals("2.5E-3", props.getProperty("exp2"));
    }

    @Test
    public void testStringEscapes() throws IOException {
        Properties props = parse("{\"str\": \"\\\" \\\\ \\/ \\b \\f \\n \\r \\t \\u0041\"}");
        assertEquals("\" \\ / \b \f \n \r \t A", props.getProperty("str"));
    }

    @Test
    public void testNonObjectDocumentRefused() {
        String err = parseExpectingError("[\"a\", \"b\"]");
        assertTrue(err, err.contains("a configuration has to be a JSON object"));

        err = parseExpectingError("\"string\"");
        assertTrue(err, err.contains("a configuration has to be a JSON object"));

        err = parseExpectingError("123");
        assertTrue(err, err.contains("a configuration has to be a JSON object"));

        err = parseExpectingError("");
        assertTrue(err, err.contains("a configuration has to be a JSON object"));
    }

    @Test
    public void testTextAfterDocumentRefused() {
        String err = parseExpectingError("{\"a\": 1} extra");
        assertTrue(err, err.contains("there is more text after the end of the document"));
    }

    @Test
    public void testDuplicateNameInSameObjectRefused() {
        String err = parseExpectingError("{\"a\": 1, \"a\": 2}");
        assertTrue(err, err.contains("the name 'a' is given twice in the same object"));
    }

    @Test
    public void testUnclosedStringRefused() {
        String err = parseExpectingError("{\"a\": \"unclosed}");
        assertTrue(err, err.contains("the string was never closed"));
    }

    @Test
    public void testControlCharacterInStringRefused() {
        String err = parseExpectingError("{\"a\": \"line\nbreak\"}");
        assertTrue(err, err.contains("a string cannot hold the control character"));
    }

    @Test
    public void testInvalidEscapeRefused() {
        String err = parseExpectingError("{\"a\": \"\\x\"}");
        assertTrue(err, err.contains("'\\x' is not an escape"));
    }

    @Test
    public void testIncompleteEscapeRefused() {
        String err = parseExpectingError("{\"a\": \"\\");
        assertTrue(err, err.contains("ends in the middle of an escape"));
    }

    @Test
    public void testIncompleteUnicodeEscapeRefused() {
        String err = parseExpectingError("{\"a\": \"\\u00");
        assertTrue(err, err.contains("a \\u escape needs four hexadecimal digits"));
    }

    @Test
    public void testInvalidUnicodeHexRefused() {
        String err = parseExpectingError("{\"a\": \"\\u000G\"}");
        assertTrue(err, err.contains("is not four hexadecimal digits"));
    }

    @Test
    public void testLeadingZeroNumberRefused() {
        String err = parseExpectingError("{\"a\": 012}");
        assertTrue(err, err.contains("a number cannot begin with a zero"));
    }

    @Test
    public void testMinusWithoutDigitsRefused() {
        String err = parseExpectingError("{\"a\": -}");
        assertTrue(err, err.contains("a number needs a digit after its sign"));
    }

    @Test
    public void testNumberMissingDecimalDigitsRefused() {
        String err = parseExpectingError("{\"a\": 1.}");
        assertTrue(err, err.contains("a number needs a digit after its decimal point"));
    }

    @Test
    public void testNumberMissingExponentDigitsRefused() {
        String err = parseExpectingError("{\"a\": 1e}");
        assertTrue(err, err.contains("a number needs a digit after its exponent"));

        err = parseExpectingError("{\"a\": 1e+}");
        assertTrue(err, err.contains("a number needs a digit after its exponent"));
    }

    @Test
    public void testInvalidValueLiteralRefused() {
        String err = parseExpectingError("{\"a\": tru}");
        assertTrue(err, err.contains("'true' was expected"));

        err = parseExpectingError("{\"a\": fal}");
        assertTrue(err, err.contains("'false' was expected"));

        err = parseExpectingError("{\"a\": nul}");
        assertTrue(err, err.contains("'null' was expected"));
    }

    @Test
    public void testMissingColonRefused() {
        String err = parseExpectingError("{\"a\" 1}");
        assertTrue(err, err.contains("':' was expected"));
    }

    @Test
    public void testMissingClosingObjectBraceRefused() {
        String err = parseExpectingError("{\"a\": 1");
        assertTrue(err, err.contains("'}' was expected"));
    }

    @Test
    public void testMissingClosingArrayBracketRefused() {
        String err = parseExpectingError("{\"a\": [1, 2");
        assertTrue(err, err.contains("']' was expected"));
    }

    @Test
    public void testMissingValueRefused() {
        String err = parseExpectingError("{\"a\": }");
        assertTrue(err, err.contains("begins no value that JSON has"));
    }
}
