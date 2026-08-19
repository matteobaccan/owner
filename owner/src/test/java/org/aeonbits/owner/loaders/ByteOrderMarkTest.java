/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.loaders;

import org.aeonbits.owner.Accessible;
import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A file that begins with a byte order mark, which on Windows is most of them.
 * <p>
 * Notepad, PowerShell's <code>Out-File</code> and Visual Studio all put a UTF-8 BOM in front of a file
 * they save, and it is invisible in every editor that wrote it.
 * {@link java.util.Properties#load(java.io.Reader)} has no idea what it is: it reads it as the first
 * character of the first key, so that property silently takes its default and <b>only the first one</b> —
 * which is what makes it hard to see, the rest of the file being fine.
 * </p>
 * <p>
 * Every other format this library reads already handled it: INI strips it by hand, the two tree formats
 * and TOML strip it while decoding, and XML is the SAX parser's problem and it deals with it. Properties
 * was the one that did not, and it is the format most people use.
 * </p>
 *
 * @author Matteo Baccan
 */
public class ByteOrderMarkTest {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final File DIR = new File("target/byteordermark");

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

    private static void write(String name, String content, boolean bom) throws IOException {
        try (OutputStream out = Files.newOutputStream(new File(DIR, name).toPath())) {
            if (bom)
                out.write(BOM);
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Sources("file:target/byteordermark/app.properties")
    public interface App extends Config, Accessible {
        @Config.DefaultValue("the default")
        String first();

        @Config.DefaultValue("the default")
        String second();
    }

    /**
     * The first property of the file, which is the one the mark lands on. Before this was fixed it
     * answered with its default, and <code>propertyNames()</code> held a key nobody could see the
     * difference in.
     */
    @Test
    public void theFirstPropertyOfAMarkedFileIsStillRead() throws IOException {
        write("app.properties", "first = one\nsecond = two\n", true);

        App cfg = ConfigFactory.newInstance().create(App.class);
        assertEquals("one", cfg.first());
        assertEquals("two", cfg.second());
        assertEquals(2, cfg.propertyNames().size());
        for (String name : cfg.propertyNames())
            assertFalse("no key may begin with the mark: " + name, name.startsWith("﻿"));
    }

    /** And a file with no mark reads exactly as it did, which is the half a fix like this can break. */
    @Test
    public void aFileWithoutOneIsUnaffected() throws IOException {
        write("app.properties", "first = one\nsecond = two\n", false);

        App cfg = ConfigFactory.newInstance().create(App.class);
        assertEquals("one", cfg.first());
        assertEquals("two", cfg.second());
    }

    /** An empty file has no first character to look at, and must not be an exception. */
    @Test
    public void anEmptyFileIsStillEmpty() throws IOException {
        write("app.properties", "", false);

        App cfg = ConfigFactory.newInstance().create(App.class);
        assertEquals("the default", cfg.first());
    }

    /** A file that is nothing but the mark, which is what an editor leaves when you empty one. */
    @Test
    public void aFileThatIsOnlyTheMarkIsEmptyToo() throws IOException {
        write("app.properties", "", true);

        App cfg = ConfigFactory.newInstance().create(App.class);
        assertEquals("the default", cfg.first());
        assertEquals("the default", cfg.second());
        // the defaults are merged into the properties, so the names are never empty - what has to be
        // empty is what the file contributed, and a mark on its own contributes no key
        for (String name : cfg.propertyNames())
            assertTrue(name, "first".equals(name) || "second".equals(name));
    }

    @Sources("file:target/byteordermark/child.properties")
    public interface Including extends Config, Accessible {
        @Config.DefaultValue("nothing")
        String fromParent();
    }

    /**
     * The compound that made this worth fixing rather than documenting. The directive belongs at the top
     * of the file by convention — it is the first thing a reader should see — which is exactly where the
     * mark lands, so a marked file included <b>nothing at all</b>, silently.
     */
    @Test
    public void aMarkedFileCanStillNameTheFilesItBuildsOn() throws IOException {
        write("parent.properties", "fromParent = from the parent\n", false);
        write("child.properties", "owner.include = file:target/byteordermark/parent.properties\n", true);

        assertEquals("from the parent", ConfigFactory.newInstance().create(Including.class).fromParent());
    }
}
