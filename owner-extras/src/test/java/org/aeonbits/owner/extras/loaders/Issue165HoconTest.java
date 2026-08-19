/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.loaders;

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
import java.nio.file.Files;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * See: https://github.com/matteobaccan/owner/issues/165 — the directive in a HOCON file, which is the one
 * format this library reads with somebody else's parser.
 * <p>
 * <b>HOCON has an <code>include</code> of its own</b>, and it is the one to use in a <code>.conf</code>
 * file: Typesafe Config resolves it while parsing, long before anything here sees the document. Ours works
 * too, and the documentation says both — a claim worth a test rather than an assumption, since it holds for
 * a reason that is not ours to keep: the loader hands back a flat map and the directive is read out of it.
 * </p>
 *
 * @author Matteo Baccan
 */
public class Issue165HoconTest {

    private static final File DIR = new File("target/issue165hocon");

    @Before
    public void before() throws IOException {
        Files.createDirectories(DIR.toPath());
    }

    @After
    public void after() {
        File[] found = DIR.listFiles();
        if (found != null)
            for (File file : found)
                file.delete();
    }

    private static void write(String name, String content) throws IOException {
        try (OutputStream out = Files.newOutputStream(new File(DIR, name).toPath())) {
            out.write(content.getBytes(UTF_8));
        }
    }

    @Sources("file:target/issue165hocon/quoted.conf")
    interface Quoted extends Config, Accessible {
        String fromParent();

        String own();
    }

    @Sources("file:target/issue165hocon/dotted.conf")
    interface Dotted extends Config, Accessible {
        String fromParent();
    }

    /** The key quoted, which is the form that says what it means in every format this library reads. */
    @Test
    public void theDirectiveWorksInAHoconFile() throws IOException {
        write("parent.properties", "fromParent = from the parent\n");
        write("quoted.conf", "\"owner.include\" = \"file:target/issue165hocon/parent.properties\"\n"
                + "own = from the hocon\n");

        Quoted cfg = ConfigFactory.newInstance().create(Quoted.class);
        assertEquals("from the hocon", cfg.own());
        assertEquals("from the parent", cfg.fromParent());
        assertFalse(cfg.propertyNames().contains("owner.include"));
    }

    /**
     * And unquoted, which HOCON reads as <code>include</code> inside an object called <code>owner</code> —
     * a different document that flattens to the same key, so the directive is found either way.
     * <p>
     * Worth pinning rather than relying on: it is true because of how the flattening works, and somebody
     * writing a <code>.conf</code> file by hand will write it this way.
     * </p>
     */
    @Test
    public void theDirectiveWorksUnquotedToo() throws IOException {
        write("parent.properties", "fromParent = from the parent\n");
        write("dotted.conf", "owner.include = \"file:target/issue165hocon/parent.properties\"\n");

        Dotted cfg = ConfigFactory.newInstance().create(Dotted.class);
        assertEquals("from the parent", cfg.fromParent());
        assertFalse(cfg.propertyNames().contains("owner.include"));
    }
}
