/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Reloadable;
import org.aeonbits.owner.util.TimeProviderForTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * See: https://github.com/matteobaccan/owner/issues/165 — the half of it that could not be built on the
 * shape this library had.
 * <p>
 * The list of sources used to be computed once in the constructor and handed to <code>HotReloadLogic</code>,
 * which set up what it watches and never looked again. <b>With includes the list changes at every load</b>:
 * a file can name one more file or stop naming one, so the watched set has to be worked out again after
 * each load, and the file this configuration never heard of at construction has to become the reason it
 * reloads.
 * </p>
 * <p>
 * These are in a class of their own because they own the clock: {@link TimeProviderForTest} is global, and
 * a test that moves time cannot share a class with tests that do not.
 * </p>
 *
 * @author Matteo Baccan
 */
public class Issue165HotReloadTest {

    private static final File DIR = new File("target/issue165hotreload");

    private TimeProviderForTest time;

    @Before
    public void before() {
        DIR.mkdirs();
        time = new TimeProviderForTest();
        time.setup();
    }

    @After
    public void after() {
        time.tearDown();
        File[] found = DIR.listFiles();
        if (found != null)
            for (File file : found)
                file.delete();
    }

    private static File write(String name, String... lines) throws IOException {
        File file = new File(DIR, name);
        try (PrintWriter out = new PrintWriter(file, Charset.defaultCharset().name())) {
            for (String line : lines)
                out.println(line);
        }
        return file;
    }

    /** Ages a file and moves the test's clock to match it, the way the other hot reload tests do. */
    private void age(File file) {
        assertTrue(file.setLastModified(file.lastModified() - 15000));
        time.setTime(file.lastModified());
    }

    @Sources("file:target/issue165hotreload/child.properties")
    @HotReload(5)
    interface Watching extends Config, Reloadable {
        @DefaultValue("nothing yet")
        String fromParent();

        @DefaultValue("nothing yet")
        String fromTheOtherParent();
    }

    /**
     * <b>Touching the included file reloads the configuration.</b>
     * <p>
     * Nothing in {@link Sources} names it: it is watched because the file that was named said so, which is
     * the whole of what #165 asked for carried through to the feature next door. Before this it would have
     * been the commonest way to be wrong about a configuration — the values come from a file, and changing
     * that file does nothing.
     * </p>
     */
    @Test
    public void touchingTheIncludedFileReloadsTheConfiguration() throws IOException {
        File parent = write("parent.properties", "fromParent = the first value");
        File child = write("child.properties",
                "owner.include = file:target/issue165hotreload/parent.properties");
        age(parent);
        age(child);

        Watching cfg = ConfigFactory.create(Watching.class);
        assertEquals("the first value", cfg.fromParent());

        write("parent.properties", "fromParent = the second value");

        time.elapse(4, SECONDS);
        assertEquals("the first value", cfg.fromParent());

        time.elapse(1, SECONDS);
        assertEquals("the second value", cfg.fromParent());
    }

    /**
     * A file that <b>starts</b> naming an include: the new file is watched from then on, though it was not
     * a source of this configuration when the object was created.
     * <p>
     * This is the case the old shape could not have: the watched set is rebuilt out of what the last load
     * turned out to read, so a source that only exists because a file mentioned it two reloads ago is
     * watched exactly as a declared one is.
     * </p>
     */
    @Test
    public void aFileThatStartsNamingAnIncludeGetsItWatched() throws IOException {
        File other = write("otherParent.properties", "fromTheOtherParent = the first value");
        File child = write("child.properties", "# naming nothing yet");
        age(other);
        age(child);

        Watching cfg = ConfigFactory.create(Watching.class);
        assertEquals("nothing yet", cfg.fromTheOtherParent());

        // the declared file starts naming one, which is what makes the reload happen
        write("child.properties",
                "owner.include = file:target/issue165hotreload/otherParent.properties");
        time.elapse(5, SECONDS);
        assertEquals("the first value", cfg.fromTheOtherParent());

        // and from now on the file it named is watched in its own right: nothing else changes
        write("otherParent.properties", "fromTheOtherParent = the second value");
        time.elapse(5, SECONDS);
        assertEquals("the second value", cfg.fromTheOtherParent());
    }

    /**
     * A file that <b>stops</b> naming an include: the values go, and touching the file it no longer names
     * is no longer a reason to reload.
     * <p>
     * The watched set shrinks as well as grows, which is the part that would be easy to leave out — a list
     * that only ever gains entries keeps a configuration reloading for a file nothing reads. It is counted
     * rather than read off the values, because a configuration that reloads for no reason answers exactly
     * the same as one that does not reload at all: the only difference is that it happened.
     * </p>
     */
    @Test
    public void aFileThatStopsNamingAnIncludeStopsWatchingIt() throws IOException {
        File parent = write("parent.properties", "fromParent = the first value");
        File child = write("child.properties",
                "owner.include = file:target/issue165hotreload/parent.properties");
        age(parent);
        age(child);

        Watching cfg = ConfigFactory.create(Watching.class);
        assertEquals("the first value", cfg.fromParent());

        write("child.properties", "# it builds on nothing now");
        time.elapse(5, SECONDS);
        assertEquals("nothing yet", cfg.fromParent());

        AtomicInteger reloads = new AtomicInteger();
        cfg.addReloadListener(event -> reloads.incrementAndGet());

        // the file it no longer names moves, and this configuration has no reason to care
        write("parent.properties", "fromParent = the second value");
        time.elapse(5, SECONDS);
        assertEquals("nothing yet", cfg.fromParent());
        assertEquals(0, reloads.get());

        // while the file it does name is still watched, so the mechanism is alive and it is the list that
        // shrank rather than the checking that stopped
        write("child.properties", "fromParent = written here now");
        time.elapse(5, SECONDS);
        assertEquals("written here now", cfg.fromParent());
        assertEquals(1, reloads.get());
    }
}
