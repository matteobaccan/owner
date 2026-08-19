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
import java.nio.file.Files;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The number grammar and the whitespace of {@link JsonParser}, which {@link JsonLoaderTest} takes as read.
 * <p>
 * A JSON number is a small grammar with several places to be wrong in, and each of them is a branch that
 * decides between a value and a refusal. The same goes for what separates two tokens: four characters are
 * whitespace and everything else is the next thing.
 * </p>
 *
 * @author Matteo Baccan
 */
public class JsonParserEdgesTest {

    private static Properties read(String json) throws IOException {
        File file = Files.createTempFile("owner-json-edges", ".json").toFile();
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

    // ---------------------------------------------------------------- the number grammar

    /** An exponent may carry either sign, or neither, and may run to more than one digit. */
    @Test
    public void anExponentMayCarryEitherSignAndMoreThanOneDigit() throws IOException {
        Properties p = read("{\"a\": 1e3, \"b\": 1e+3, \"c\": 1e-3, \"d\": 1.5E+10, \"e\": 2e105}");
        assertEquals("1e3", p.getProperty("a"));
        assertEquals("1e+3", p.getProperty("b"));
        assertEquals("1e-3", p.getProperty("c"));
        assertEquals("1.5E+10", p.getProperty("d"));
        assertEquals("2e105", p.getProperty("e"));
    }

    /** A negative number, and a zero, which is the one integer that may not be followed by a digit. */
    @Test
    public void aNegativeNumberAndAZeroAreRead() throws IOException {
        Properties p = read("{\"a\": -17, \"b\": 0, \"c\": -0.5, \"d\": 0.25}");
        assertEquals("-17", p.getProperty("a"));
        assertEquals("0", p.getProperty("b"));
        assertEquals("-0.5", p.getProperty("c"));
        assertEquals("0.25", p.getProperty("d"));
    }

    /**
     * A minus sign with nothing usable behind it. Having written the sign the author meant a number, so
     * the message says that rather than the general one below — the same check, told apart by whether a
     * sign was read.
     */
    @Test
    public void aMinusSignWithNoDigitsAfterItIsRefused() {
        String said = refused("{\"a\": -}");
        assertNotNull(said);
        assertTrue(said, said.contains("needs a digit after its sign"));
    }

    /**
     * A character that begins nothing at all. It arrives here because a number is the last thing tried,
     * so this check is where every unrecognised value ends up — which is why the sentence is about JSON
     * and not about numbers.
     * <p>
     * Its other half, <i>"a value was expected and the document ends"</i>, cannot be reached: a document
     * that stops where a value was due is caught earlier, by the check that says so in fewer words, and
     * an unsigned number begins with a digit and therefore has one. Left in place — it is the honest
     * thing for that {@code if} to say — but nothing can make it fire.
     * </p>
     */
    @Test
    public void aCharacterThatBeginsNoValueIsRefused() {
        String said = refused("{\"a\": @}");
        assertNotNull(said);
        assertTrue(said, said.contains("begins no value that JSON has"));
    }

    /** And a document that stops where a value was due, which is the check that gets there first. */
    @Test
    public void aDocumentThatStopsWhereAValueWasDueIsRefused() {
        String said = refused("{\"a\": ");
        assertNotNull(said);
        assertTrue(said, said.contains("a value was expected"));
    }

    // ---------------------------------------------------------------- what separates two tokens

    /**
     * All four whitespace characters JSON has, used as the only separator in a document — including the
     * carriage return, which is what a file written on Windows is full of.
     */
    @Test
    public void allFourWhitespaceCharactersSeparateTokens() throws IOException {
        Properties p = read("{\r\n\t \"server\"\r\n\t : \r\n\t{\r\n\t\"host\"\t:\r\n\"localhost\"\r\n}\r\n}");
        assertEquals("localhost", p.getProperty("server.host"));
    }

    /**
     * A separator that is not there. The message carries the line and the column, and the column is
     * counted by walking the text, so a document with more than one line has to be measured rather than
     * assumed.
     */
    @Test
    public void aMissingSeparatorIsRefusedWithItsPlace() {
        String said = refused("{\n  \"a\": 1\n  \"b\": 2\n}");
        assertNotNull(said);
        assertTrue(said, said.contains("Line 3"));
    }
}
