/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.formats;

import org.aeonbits.owner.formats.json.JsonLoader;
import org.aeonbits.owner.formats.toml.TomlLoader;
import org.aeonbits.owner.formats.yaml.YamlLoader;
import org.aeonbits.owner.loaders.DotEnvLoader;
import org.aeonbits.owner.loaders.IniLoader;
import org.aeonbits.owner.loaders.Loader;
import org.aeonbits.owner.loaders.PropertiesLoader;
import org.aeonbits.owner.loaders.XMLLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The same characters through every format there is, and what each of them does with a file that is not
 * valid UTF-8.
 * <p>
 * A configuration is written by people, and people write in their own language. The five below are the
 * classes that break a reader in different ways: a letter outside ASCII, an ideograph, a character that
 * needs <b>two</b> Java chars, a mark that combines with the letter before it, and a script that runs the
 * other way. If a format mangles any of them it does so silently — the value looks nearly right — which is
 * why they are asserted together and against every reader at once.
 * </p>
 *
 * @author Matteo Baccan
 */
public class EveryFormatReadsUtf8Test {

    /** A letter with an accent, one Java char. */
    private static final String ACCENT = "caffè";
    /** Two ideographs, one Java char each. */
    private static final String CJK = "设置";
    /** A rocket, outside the basic plane: one code point, and two Java chars. */
    private static final String EMOJI = "a🚀b";
    /** An e and a combining acute, which is two code points that print as one letter. */
    private static final String COMBINING = "é";
    /** Hebrew, which runs right to left. */
    private static final String RTL = "שלום";

    private static final String ALL = ACCENT + "|" + CJK + "|" + EMOJI + "|" + COMBINING + "|" + RTL;

    private static final File DIR = new File("target/utf8");

    @Before
    public void before() {
        DIR.mkdirs();
    }

    @After
    public void after() {
        File[] found = DIR.listFiles();
        if (found != null)
            for (File file : found)
                file.delete();
    }

    private static File write(String name, String content) throws IOException {
        File file = new File(DIR, name);
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    private static String read(Loader loader, File file, String key) throws IOException {
        Properties result = new Properties();
        loader.load(result, file.toURI());
        return result.getProperty(key);
    }

    // ---------------------------------------------------------------- the values

    /**
     * Every format reads the same five things the same way. Asserted character by character rather than
     * by looking at the file, because a reader that mangles one of them produces a value that still looks
     * like a word.
     */
    @Test
    public void everyFormatReadsTheSameCharacters() throws IOException {
        assertEquals(ALL, read(new PropertiesLoader(), write("a.properties", "value = " + ALL), "value"));
        assertEquals(ALL, read(new DotEnvLoader(), write("a.env", "value=" + ALL), "value"));
        assertEquals(ALL, read(new IniLoader(), write("a.ini", "value = " + ALL), "value"));
        assertEquals(ALL, read(new XMLLoader(),
                write("a.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><c><value>" + ALL + "</value></c>"),
                "c.value"));
        assertEquals(ALL, read(new JsonLoader(), write("a.json", "{\"value\": \"" + ALL + "\"}"), "value"));
        assertEquals(ALL, read(new YamlLoader(), write("a.yaml", "value: \"" + ALL + "\""), "value"));
        assertEquals(ALL, read(new TomlLoader(), write("a.toml", "value = \"" + ALL + "\""), "value"));
    }

    /** And a key may be written in them too, which is what a configuration in one language looks like. */
    @Test
    public void aKeyMayBeWrittenInAnyOfThemToo() throws IOException {
        assertEquals("ok", read(new PropertiesLoader(), write("k.properties", CJK + " = ok"), CJK));
        assertEquals("ok", read(new JsonLoader(), write("k.json", "{\"" + CJK + "\": \"ok\"}"), CJK));
        assertEquals("ok", read(new YamlLoader(), write("k.yaml", CJK + ": ok"), CJK));
        assertEquals("ok", read(new TomlLoader(), write("k.toml", "\"" + CJK + "\" = \"ok\""), CJK));
    }

    /**
     * The escape each format spells a code point with, including one outside the basic plane — which JSON
     * and YAML write as a surrogate pair and TOML as a single <code>\U</code> of eight digits.
     */
    @Test
    public void anEscapedCodePointOutsideTheBasicPlaneIsReadWhole() throws IOException {
        assertEquals(EMOJI, read(new JsonLoader(),
                write("e.json", "{\"value\": \"a\\ud83d\\ude80b\"}"), "value"));
        assertEquals(EMOJI, read(new YamlLoader(),
                write("e.yaml", "value: \"a\\ud83d\\ude80b\""), "value"));
        assertEquals(EMOJI, read(new TomlLoader(),
                write("e.toml", "value = \"a\\U0001F680b\""), "value"));
        assertEquals(EMOJI, read(new PropertiesLoader(),
                write("e.properties", "value = a\\ud83d\\ude80b"), "value"));
    }

    // ---------------------------------------------------------------- a file that is not UTF-8

    /** A malformed byte, in the middle of an otherwise good document. */
    private static File writeWithABadByte(String name, String before, String after) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(before.getBytes(StandardCharsets.UTF_8));
        bytes.write(0xC3);   // the first byte of a two-byte sequence…
        bytes.write(0x28);   // …followed by something that cannot continue it
        bytes.write(after.getBytes(StandardCharsets.UTF_8));
        File file = new File(DIR, name);
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            out.write(bytes.toByteArray());
        }
        return file;
    }

    /**
     * <b>Refused, not repaired.</b> All three of these formats say their document is UTF-8, so one that is
     * not is declined — where {@code new String(bytes, UTF_8)} would put a U+FFFD inside the value and read
     * on, and the configuration would come up with a password that authenticates nowhere and nothing
     * anywhere saying why.
     */
    @Test
    public void aDocumentThatIsNotValidUtf8IsRefused() throws IOException {
        refuses(new JsonLoader(), writeWithABadByte("bad.json", "{\"value\": \"a", "b\"}"));
        refuses(new YamlLoader(), writeWithABadByte("bad.yaml", "value: \"a", "b\""));
        refuses(new TomlLoader(), writeWithABadByte("bad.toml", "value = \"a", "b\""));
    }

    private static void refuses(Loader loader, File file) {
        try {
            loader.load(new Properties(), file.toURI());
            fail(loader.getClass().getSimpleName() + " read a document that is not UTF-8");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("UTF-8"));
        }
    }
}
