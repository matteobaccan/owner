/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The escaping {@link PropertiesFileWriter} does, and the reading it does back.
 * <p>
 * {@link SavingTheFileTest} covers what the writer is <i>for</i> — keeping the file it writes into. This
 * covers the characters, which is a different kind of question: a properties file has an escaping table
 * and either both directions implement the same one or a round trip loses something. The two directions
 * are here together for that reason.
 * </p>
 * <p>
 * <b>Every case is a character that changes meaning.</b> A tab inside a key is the separator; a newline
 * inside a value ends the line; anything outside printable ASCII is not what
 * {@link java.util.Properties#store} writes. The table is small and each entry is one line of code, which
 * is exactly the shape of thing that gets written once and never exercised.
 * </p>
 *
 * @author Matteo Baccan
 */
public class PropertiesFileWriterEscapingTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Config.Sources("file:${test.escaping.file}")
    public interface AppConfig extends Mutable, Accessible {
        @Config.DefaultValue("plain")
        String message();
    }

    // ------------------------------------------------------------------------------------------------
    // writing: what a value has to be written as
    // ------------------------------------------------------------------------------------------------

    /**
     * The whitespace a value cannot hold literally. A newline would end the line, a carriage return would
     * end it on one platform and not on another, a form feed is invisible — all three are written as the
     * two characters {@link java.util.Properties#store} writes.
     */
    @Test
    public void theWhitespaceAValueCannotHoldIsWrittenAsAnEscape() {
        assertEquals("before\\nafter", PropertiesFileWriter.escapeValue("before\nafter"));
        assertEquals("before\\rafter", PropertiesFileWriter.escapeValue("before\rafter"));
        assertEquals("before\\fafter", PropertiesFileWriter.escapeValue("before\fafter"));
        assertEquals("before\\tafter", PropertiesFileWriter.escapeValue("before\tafter"));
    }

    /**
     * Anything outside printable ASCII is written as <code>\\uXXXX</code>, which is what
     * {@link java.util.Properties#store} does and the reason a properties file is readable by a tool that
     * assumes ISO-8859-1.
     */
    @Test
    public void anythingOutsidePrintableAsciiIsWrittenAsAUnicodeEscape() {
        assertEquals("caff\\u00e8", PropertiesFileWriter.escapeValue("caffè"));
        // and below the printable range too, not only above it. Written as a construction rather
        // than as a literal: a control character in a source file is invisible to whoever reads it
        assertEquals("bell\\u0007", PropertiesFileWriter.escapeValue("bell" + (char) 7));
    }

    /**
     * A space is the separator in a key and ordinary in a value — <b>except the first</b>, which would be
     * eaten as padding. That is the one asymmetry in the table and it is the JDK's, not ours.
     */
    @Test
    public void aSpaceIsEscapedEverywhereInAKeyAndOnlyFirstInAValue() {
        assertEquals("two\\ words", PropertiesFileWriter.escapeKey("two words"));
        assertEquals("\\ leading", PropertiesFileWriter.escapeValue(" leading"));
        assertEquals("two words", PropertiesFileWriter.escapeValue("two words"));
    }

    // ------------------------------------------------------------------------------------------------
    // reading: the key a line declares
    // ------------------------------------------------------------------------------------------------

    /**
     * The same table, read back: a key written with an escape is the character it stands for, so the
     * writer recognises its own line when it saves the file a second time.
     */
    @Test
    public void aKeyWrittenWithAnEscapeIsReadAsTheCharacterItStandsFor() {
        assertEquals("with\ttab", PropertiesFileWriter.keyOf("with\\ttab = value"));
        assertEquals("with\nnewline", PropertiesFileWriter.keyOf("with\\nnewline = value"));
        assertEquals("with\rreturn", PropertiesFileWriter.keyOf("with\\rreturn = value"));
        assertEquals("with\ffeed", PropertiesFileWriter.keyOf("with\\ffeed = value"));
        // an escape with no meaning of its own is the character itself, which is how a space or an
        // equals sign gets into a key
        assertEquals("two words", PropertiesFileWriter.keyOf("two\\ words = value"));
    }

    /**
     * A line that declares no key at all. It is not a failure — the writer hands such a line straight
     * back out, which is how it keeps a file it only partly owns.
     */
    @Test
    public void aLineThatDeclaresNoKeyIsRecognisedAsSuch() {
        assertNull(PropertiesFileWriter.keyOf("   "));
        assertNull(PropertiesFileWriter.keyOf("# a comment"));
        assertNull(PropertiesFileWriter.keyOf("! also a comment"));
        // a separator with nothing before it: java.util.Properties reads it as the empty key, and an
        // empty key is not one we could ever be asked to write
        assertNull(PropertiesFileWriter.keyOf("= a value with no key"));
        assertNull(PropertiesFileWriter.keyOf(": nor this one"));
    }

    // ------------------------------------------------------------------------------------------------
    // and the two directions meeting
    // ------------------------------------------------------------------------------------------------

    /**
     * The round trip, which is the only assertion that covers both directions at once: a value holding
     * every character in the table survives being written and read back, and
     * {@link java.util.Properties} agrees with what we wrote.
     */
    @Test
    public void aValueHoldingEveryEscapedCharacterSurvivesTheRoundTrip() throws IOException {
        File file = folder.newFile("escaping.properties");
        write(file, "message = plain\n");
        System.setProperty("test.escaping.file", file.getAbsolutePath());
        try {
            AppConfig cfg = ConfigFactory.create(AppConfig.class);
            String awkward = "line\nbreak\ttab\rreturn\ffeed caffè ";
            cfg.setProperty("message", awkward);
            cfg.save(file);

            // what the JDK reads back out of it, which is the contract this writer is held to
            Properties reloaded = new Properties();
            try (java.io.InputStream in = Files.newInputStream(file.toPath())) {
                reloaded.load(in);
            }
            assertEquals(awkward, reloaded.getProperty("message"));

            // and the file itself holds no raw newline inside the value: one property, one line
            String written = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
            assertTrue(written, written.contains("\\n"));
            assertTrue(written, written.contains("\\u00e8"));
        } finally {
            System.clearProperty("test.escaping.file");
        }
    }

    private static void write(File file, String content) throws IOException {
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            out.write(content.getBytes(StandardCharsets.ISO_8859_1));
        }
    }
}
