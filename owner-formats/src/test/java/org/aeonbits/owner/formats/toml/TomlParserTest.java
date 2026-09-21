/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.formats.toml;

import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Direct unit test for {@link TomlParser}.
 *
 * @author Matteo Baccan
 */
public class TomlParserTest {

    private Properties parse(String toml) throws IOException {
        Properties props = new Properties();
        TomlParser parser = new TomlParser(toml, props);
        parser.parse();
        return props;
    }

    private void assertRefused(String toml, String expectedSubstring) {
        try {
            parse(toml);
            fail("Expected IOException containing '" + expectedSubstring + "' but parse succeeded");
        } catch (IOException e) {
            assertTrue("Expected exception message containing '" + expectedSubstring
                            + "', but got: " + e.getMessage(),
                    e.getMessage().contains(expectedSubstring));
        }
    }

    @Test
    public void testBasicKeyValue() throws IOException {
        Properties p = parse("title = \"TOML Example\"\nkey = 'value'\n");
        assertEquals("TOML Example", p.getProperty("title"));
        assertEquals("value", p.getProperty("key"));
    }

    @Test
    public void testBooleans() throws IOException {
        Properties p = parse("bool1 = true\nbool2 = false\n");
        assertEquals("true", p.getProperty("bool1"));
        assertEquals("false", p.getProperty("bool2"));
    }

    @Test
    public void testNumbersAndRadix() throws IOException {
        Properties p = parse("int1 = 42\n"
                + "int2 = +99\n"
                + "int3 = -17\n"
                + "hex = 0xDEADBEEF\n"
                + "oct = 0o755\n"
                + "bin = 0b1101\n"
                + "with_underscore = 1_000_000\n");
        assertEquals("42", p.getProperty("int1"));
        assertEquals("99", p.getProperty("int2"));
        assertEquals("-17", p.getProperty("int3"));
        assertEquals("3735928559", p.getProperty("hex"));
        assertEquals("493", p.getProperty("oct"));
        assertEquals("13", p.getProperty("bin"));
        assertEquals("1000000", p.getProperty("with_underscore"));
    }

    @Test
    public void testFloatsAndSpecialValues() throws IOException {
        Properties p = parse("flt1 = +1.0\n"
                + "flt2 = 3.1415\n"
                + "flt3 = -0.01\n"
                + "flt4 = 5e+22\n"
                + "inf1 = inf\n"
                + "inf2 = +inf\n"
                + "inf3 = -inf\n"
                + "nan1 = nan\n");
        assertEquals("1.0", p.getProperty("flt1"));
        assertEquals("3.1415", p.getProperty("flt2"));
        assertEquals("-0.01", p.getProperty("flt3"));
        assertEquals("5e+22", p.getProperty("flt4"));
        assertEquals("Infinity", p.getProperty("inf1"));
        assertEquals("Infinity", p.getProperty("inf2"));
        assertEquals("-Infinity", p.getProperty("inf3"));
        assertEquals("NaN", p.getProperty("nan1"));
    }

    @Test
    public void testDateTimes() throws IOException {
        Properties p = parse("odt = 1979-05-27T07:32:00Z\n"
                + "ldt1 = 1979-05-27T07:32:00\n"
                + "ldt2 = 1979-05-27 07:32:00\n"
                + "ld = 1979-05-27\n"
                + "lt = 07:32:00\n"
                + "frac = 07:32:00.999\n");
        assertEquals("1979-05-27T07:32:00Z", p.getProperty("odt"));
        assertEquals("1979-05-27T07:32:00", p.getProperty("ldt1"));
        assertEquals("1979-05-27T07:32:00", p.getProperty("ldt2"));
        assertEquals("1979-05-27", p.getProperty("ld"));
        assertEquals("07:32:00", p.getProperty("lt"));
        assertEquals("07:32:00.999", p.getProperty("frac"));
    }

    @Test
    public void testStringsAndEscapes() throws IOException {
        Properties p = parse("basic = \"Hello\\nWorld\\t!\"\n"
                + "literal = 'C:\\\\Users\\\\path'\n"
                + "unicode = \"\\u00E9 \\U0001F600\"\n"
                + "multiline = \"\"\"\nLine 1\nLine 2\"\"\"\n");
        assertEquals("Hello\nWorld\t!", p.getProperty("basic"));
        assertEquals("C:\\Users\\path", p.getProperty("literal"));
        assertEquals("\u00E9 \uD83D\uDE00", p.getProperty("unicode"));
        assertEquals("Line 1\nLine 2", p.getProperty("multiline"));
    }

    @Test
    public void testArraysAndInlineTables() throws IOException {
        Properties p = parse("arr1 = [1, 2, 3]\n"
                + "arr2 = []\n"
                + "inline = { x = 1, y = 2 }\n");
        assertEquals("1", p.getProperty("arr1[0]"));
        assertEquals("2", p.getProperty("arr1[1]"));
        assertEquals("3", p.getProperty("arr1[2]"));
        assertEquals("", p.getProperty("arr2"));
        assertEquals("1", p.getProperty("inline.x"));
        assertEquals("2", p.getProperty("inline.y"));
    }

    @Test
    public void testTablesAndArrayOfTables() throws IOException {
        Properties p = parse("[a.b]\n"
                + "c = 1\n"
                + "\n"
                + "[[products]]\n"
                + "name = \"Hammer\"\n"
                + "\n"
                + "[[products]]\n"
                + "name = \"Nail\"\n");
        assertEquals("1", p.getProperty("a.b.c"));
        assertEquals("Hammer", p.getProperty("products[0].name"));
        assertEquals("Nail", p.getProperty("products[1].name"));
    }

    @Test
    public void testErrorHandlingAndLocation() {
        assertRefused("a = 1\na = 2", "Line 2, column 1: 'a' is defined twice");
        assertRefused("a = 01", "cannot begin with a zero");
        assertRefused("a = 1.e2", "digits on both sides");
        assertRefused("a = 1979-02-29", "is not a date that exists");
        assertRefused("a = \"\\q\"", "is not an escape TOML knows");
        assertRefused("a = \"unclosed", "the string was not closed before the end of the line");
    }
}
