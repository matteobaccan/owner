/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Reading a properties file, changing a value and writing it back — the whole round trip against a real
 * file, which is what <a href="https://github.com/matteobaccan/owner/issues/190">#190</a> asked for.
 * <p>
 * A configuration is written through {@link Mutable} and read back out through
 * {@link Accessible#store(OutputStream, String)}. The interface is methods rather than fields, which is
 * what made the question reasonable: the setter is not on the interface, it is
 * {@link Mutable#setProperty}, keyed by name.
 * </p>
 * <p>
 * <b>And the half that has to be said out loud:</b> <code>store()</code> writes the properties out, it
 * does not edit the file. Comments, blank lines and the original order do not survive, and a timestamp
 * line is added — because underneath is
 * {@link java.util.Properties#store(OutputStream, String)}, which serialises a map. Preserving the file
 * is <a href="https://github.com/matteobaccan/owner/issues/16">#16</a> and is a different feature. Both
 * halves are asserted here so that neither can change without somebody deciding to.
 * </p>
 *
 * @author Matteo Baccan
 */
public class WritingTheFileBackTest {

    /** Where the file under test lives, put into a factory property so the spec can expand it. */
    private static final String LOCATION = "test.config.file";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File file;

    @Config.Sources("file:${test.config.file}")
    public interface AppConfig extends Mutable, Accessible {
        String host();

        int port();
    }

    @Before
    public void writeAFileByHand() throws IOException {
        file = folder.newFile("app.properties");
        Files.write(file.toPath(), ("# the database we talk to\n"
                + "host = localhost\n"
                + "\n"
                + "# in milliseconds\n"
                + "port = 8080\n").getBytes(StandardCharsets.ISO_8859_1));
        ConfigFactory.setProperty(LOCATION, file.getAbsolutePath().replace('\\', '/'));
    }

    @After
    public void forgetTheLocation() {
        ConfigFactory.clearProperty(LOCATION);
    }

    private String contents() throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
    }

    /** The answer to the question: change a value through the interface, write the file, read it again. */
    @Test
    public void aValueChangedThroughTheInterfaceReachesTheFile() throws IOException {
        AppConfig config = ConfigFactory.create(AppConfig.class);
        assertEquals(8080, config.port());

        config.setProperty("port", "9090");
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            config.store(out, "written back by the application");
        }

        assertTrue(contents(), contents().contains("port=9090"));
        assertEquals("and a configuration created afterwards reads it", 9090,
                ConfigFactory.create(AppConfig.class).port());
    }

    /** What was not changed is written back as it was, so the round trip is not lossy about values. */
    @Test
    public void everyOtherValueSurvivesTheRoundTrip() throws IOException {
        AppConfig config = ConfigFactory.create(AppConfig.class);
        config.setProperty("port", "9090");
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            config.store(out, null);
        }

        assertEquals("localhost", ConfigFactory.create(AppConfig.class).host());
    }

    /**
     * The limitation, asserted rather than left to be discovered: this rewrites the file, it does not
     * edit it. Preserving comments and layout is #16.
     */
    @Test
    public void theCommentsAndTheLayoutDoNotSurvive() throws IOException {
        AppConfig config = ConfigFactory.create(AppConfig.class);
        config.setProperty("port", "9090");
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            config.store(out, null);
        }

        String written = contents();
        assertFalse("the comment above host is gone", written.contains("the database we talk to"));
        assertFalse("and so is the one above port", written.contains("in milliseconds"));
        assertTrue("the values are all there", written.contains("host=localhost"));
        assertTrue(written.contains("port=9090"));
    }

    /**
     * A property removed through the interface is absent from the file, which is the other half of
     * editing and the reason {@link Mutable#removeProperty} exists rather than setting an empty value.
     */
    @Test
    public void aRemovedPropertyIsNotWrittenAtAll() throws IOException {
        AppConfig config = ConfigFactory.create(AppConfig.class);
        config.removeProperty("port");
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            config.store(out, null);
        }

        assertFalse(contents(), contents().contains("port"));
        assertTrue(contents(), contents().contains("host=localhost"));
    }
}
