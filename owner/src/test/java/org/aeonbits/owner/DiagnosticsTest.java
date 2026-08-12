/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.util.LogCapture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.logging.Level;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.aeonbits.owner.util.Util.hideCredentials;
import static org.aeonbits.owner.util.Util.unsupported;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What the library says about itself when asked, and what it must never say.
 * <p>
 * One switch — <code>org.aeonbits.owner.level = CONFIG</code> — and it reports what it looked for, what of
 * it was there, and which loader answered. That is most of "why is my property missing", which is otherwise
 * a guess: everything succeeded, only not on the file somebody had in mind.
 * </p>
 *
 * @author Matteo Baccan
 */
public class DiagnosticsTest {

    private LogCapture capture;

    @Before
    public void before() {
        capture = LogCapture.ofLibrary(Level.CONFIG);
    }

    @After
    public void after() {
        capture.close();
    }

    // ---------------------------------------------------------------- a source may carry credentials

    @Test
    public void credentialsInASourceAreNotPartOfWhatItIsCalled() throws URISyntaxException {
        assertEquals("https://***@config.example.org/app.properties",
                hideCredentials(new URI("https://user:secret@config.example.org/app.properties")));
        assertEquals("https://***@config.example.org/app.properties",
                hideCredentials(new URI("https://user@config.example.org/app.properties")));
    }

    @Test
    public void aSourceWithNoCredentialsIsLeftAsItIs() throws URISyntaxException {
        assertEquals("https://config.example.org/app.properties",
                hideCredentials(new URI("https://config.example.org/app.properties")));
        assertEquals("file:/etc/app.ini", hideCredentials(new URI("file:/etc/app.ini")));
        assertEquals("null", hideCredentials(null));
    }

    /**
     * Read from the text and not from getUserInfo(), which is null on an opaque URI - and an opaque URI is
     * what <code>file:.env</code> and a resource inside a jar both are, so the common cases would have been
     * the ones it got wrong.
     */
    @Test
    public void anOpaqueSourceIsHandledToo() throws URISyntaxException {
        assertEquals("file:.env", hideCredentials(new URI("file:.env")));
        assertEquals("jar:file:/a.jar!/conf/app.ini", hideCredentials(new URI("jar:file:/a.jar!/conf/app.ini")));
    }

    /**
     * Done once where the message is built, rather than at each of the twenty-odd places that name a source
     * in one. A message written tomorrow is covered without anybody remembering to.
     */
    @Test
    public void anySourceInAMessageIsCoveredWithoutTheCallerDoingAnything() throws URISyntaxException {
        String message = unsupported("cannot read %s", new URI("ftp://joe:hunter2@host/app.ini")).getMessage();

        assertTrue(message, message.contains("ftp://***@host/app.ini"));
        assertFalse(message, message.contains("hunter2"));
    }

    // ---------------------------------------------------------------- what it says when asked

    @Test
    public void itNamesTheLoaderThatAnswered() throws IOException {
        readTheIni();

        assertTrue(said(), said().contains("IniLoader"));
        assertTrue(said(), said().contains("Reading"));
    }

    @Test
    public void itSaysWhatItLookedForWhenNothingDeclaresTheSources() {
        ConfigFactory.create(WithoutSources.class);

        assertTrue(said(), said().contains("no @Sources, looking for:"));
        assertTrue(said(), said().contains(".properties"));
        assertTrue(said(), said().contains(".ini"));
    }

    /**
     * A spec that resolves to nothing is dropped, which is how the fallback is meant to work and is also why
     * a configuration full of defaults can have no explanation at all. Now it has one.
     */
    @Test
    public void itSaysWhichSourceWasNotThere() {
        ConfigFactory.create(FromAMissingSource.class);

        assertTrue(said(), said().contains("was not found, skipping it"));
        assertTrue(said(), said().contains("no/such/file.properties"));
    }

    @Test
    public void itSaysNothingAtAllWhenNobodyAsked() throws IOException {
        // the capture this class installs listens from CONFIG, which is the switch being turned on. This
        // test is about it being off, so it listens from INFO instead, for the length of one load
        capture.close();
        try (LogCapture nobodyAsked = LogCapture.ofLibrary(Level.INFO)) {
            readTheIni();

            assertTrue(nobodyAsked.messagesWithTheirLevel(), nobodyAsked.lines().isEmpty());
        }
    }

    @Sources("${source}")
    public interface FromASource extends Config {
        @Config.Key("server.host")
        String host();
    }

    @Sources("classpath:no/such/file.properties")
    public interface FromAMissingSource extends Config {
        String anything();
    }

    public interface WithoutSources extends Config {
        String anything();
    }

    /**
     * The source is named through a variable so that it can be a temporary file, and that variable has to be
     * a <b>factory</b> property: {@code @Sources} is expanded from those, not from the imports handed to
     * {@code create}, which are properties of the configuration rather than of the factory.
     */
    private void readTheIni() throws IOException {
        File ini = write(".ini", "[server]\nhost = localhost\n");
        ConfigFactory.setProperty("source", ini.toURI().toString());
        try {
            assertEquals("localhost", ConfigFactory.create(FromASource.class).host());
        } finally {
            ConfigFactory.clearProperty("source");
        }
    }

    private String said() {
        return capture.messagesWithTheirLevel();
    }

    private static File write(String suffix, String content) throws IOException {
        File file = Files.createTempFile("owner-diagnostics", suffix).toFile();
        file.deleteOnExit();
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(UTF_8));
        }
        return file;
    }
}
