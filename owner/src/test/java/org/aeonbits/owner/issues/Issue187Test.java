/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * See: https://github.com/lviggiano/owner/issues/187
 * <p>
 * {@link Path} was not converted at all — not even an ordinary absolute path — while {@link File} was, and with
 * the leading <code>~</code> expanded to the user home. The two ways of naming a path now behave alike. The
 * reporter wondered whether this belonged in a separate module for Java 7; the question no longer arises, since
 * the minimum runtime is Java 8.
 */
public class Issue187Test {

    public interface WithPath extends Config {
        @DefaultValue("~")
        Path home();

        @DefaultValue("~/config")
        Path underHome();

        @DefaultValue("/etc/myapp/conf.properties")
        Path absolute();

        @DefaultValue("~")
        File homeAsFile();

        @DefaultValue("~/config")
        File underHomeAsFile();

        @DefaultValue("~/one, ~/two")
        Path[] several();

        @DefaultValue("~/one, ~/two")
        List<Path> asList();
    }

    private static final WithPath CFG = ConfigFactory.create(WithPath.class);

    private static Path userHome() {
        return Paths.get(System.getProperty("user.home"));
    }

    @Test
    public void aPathIsConverted() {
        assertEquals(Paths.get("/etc/myapp/conf.properties"), CFG.absolute());
    }

    @Test
    public void theTildeIsExpandedAsItIsForFile() {
        assertEquals(userHome(), CFG.home());
        assertEquals(userHome().resolve("config"), CFG.underHome());
    }

    /** The point of the issue: the two ways of naming a path must not disagree. */
    @Test
    public void aPathAndAFileAgree() {
        assertEquals(CFG.homeAsFile().toPath(), CFG.home());
        assertEquals(CFG.underHomeAsFile().toPath(), CFG.underHome());
    }

    @Test
    public void arraysAndCollectionsOfPathsFollow() {
        assertEquals(2, CFG.several().length);
        assertEquals(userHome().resolve("one"), CFG.several()[0]);
        assertEquals(userHome().resolve("two"), CFG.several()[1]);

        assertEquals(2, CFG.asList().size());
        assertEquals(userHome().resolve("one"), CFG.asList().get(0));
    }
}
