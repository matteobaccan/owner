/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.loaders.DiscoverableLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Which class loader does the looking, shown rather than described.
 * <p>
 * The same service file, the same class, the same JVM: found or not found according only to which class
 * loader is the thread's context one when the factory is built. The class itself always comes from the test
 * class path - the jar holds nothing but the declaration - which is the part that is easy to get backwards:
 * discovery is about who <b>searches</b>, not about who <b>loads</b>.
 * </p>
 *
 * @author Matteo Baccan
 */
public class LoaderDiscoveryClassLoaderTest {

    private static final String SERVICE_FILE = "META-INF/services/org.aeonbits.owner.loaders.Loader";
    private static final URI A_SOURCE_ONLY_THAT_LOADER_WANTS = uri("file:/app/config.discoverable");

    private ClassLoader original;
    private File declaration;

    @Before
    public void before() throws IOException {
        original = Thread.currentThread().getContextClassLoader();
        declaration = jarDeclaring(DiscoverableLoader.class.getName());
    }

    @After
    public void after() {
        Thread.currentThread().setContextClassLoader(original);
    }

    @Test
    public void aLoaderIsFoundWhenTheContextClassLoaderCanSeeItsDeclaration() throws IOException {
        Thread.currentThread().setContextClassLoader(classLoaderSeeing(declaration));

        assertTrue("the declaration was in the context class loader and the loader was not found",
                new LoadersManager().findLoader(A_SOURCE_ONLY_THAT_LOADER_WANTS) instanceof DiscoverableLoader);
    }

    /**
     * The same jar exists and the same class is on the class path; only the thread is looking elsewhere. In
     * an application server this is the shape of "my format works locally and not once deployed".
     */
    @Test
    public void theSameLoaderIsNotFoundWhenTheContextClassLoaderCannotSeeIt() {
        Thread.currentThread().setContextClassLoader(original);

        assertFalse("nothing declares the loader here, so it must not be discovered",
                new LoadersManager().findLoader(A_SOURCE_ONLY_THAT_LOADER_WANTS) instanceof DiscoverableLoader);
    }

    /**
     * A context class loader that finds nothing is not the same as no context class loader at all: with none
     * the search falls back on the one that loaded OWNER, and the point of the test is that it happens
     * without an accident.
     */
    @Test
    public void noContextClassLoaderFallsBackInsteadOfFailing() {
        Thread.currentThread().setContextClassLoader(null);

        assertFalse(new LoadersManager().findLoader(A_SOURCE_ONLY_THAT_LOADER_WANTS) instanceof DiscoverableLoader);
    }

    /**
     * A loader that is not discovered leaves no other trace, so the line that says what was discovered has
     * to be there even when the answer is nothing - that is the case somebody turns it on to look into.
     */
    @Test
    public void whatWasDiscoveredIsReportedAtConfigLevel() throws IOException {
        Thread.currentThread().setContextClassLoader(classLoaderSeeing(declaration));
        assertTrue(recordWhileBuildingAManager().contains(DiscoverableLoader.class.getName()));

        Thread.currentThread().setContextClassLoader(original);
        assertTrue(recordWhileBuildingAManager().contains("none"));
    }

    /** Collects what the manager says about discovery while one is being built. */
    private static String recordWhileBuildingAManager() {
        Logger logger = Logger.getLogger(LoadersManager.class.getName());
        final StringBuilder said = new StringBuilder();
        Handler collector = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == Level.CONFIG)
                    said.append(record.getMessage());
            }

            @Override
            public void flush() {
                // nothing is buffered
            }

            @Override
            public void close() {
                // nothing to release
            }
        };
        Level before = logger.getLevel();
        logger.setLevel(Level.CONFIG);
        logger.addHandler(collector);
        try {
            new LoadersManager();
            return said.toString();
        } finally {
            logger.removeHandler(collector);
            logger.setLevel(before);
        }
    }

    /**
     * A jar holding the declaration and nothing else. The class named in it is resolved through the parent,
     * which is the ordinary shape: an artifact declares what it offers, the classes come from wherever the
     * class path put them.
     */
    private static File jarDeclaring(String loaderClassName) throws IOException {
        File jar = File.createTempFile("owner-discovery", ".jar");
        jar.deleteOnExit();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry(SERVICE_FILE));
            zip.write((loaderClassName + "\n").getBytes("UTF-8"));
            zip.closeEntry();
        }
        return jar;
    }

    private ClassLoader classLoaderSeeing(File jar) throws IOException {
        return new URLClassLoader(new URL[]{jar.toURI().toURL()}, original);
    }

    private static URI uri(String spec) {
        try {
            return new URI(spec);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
