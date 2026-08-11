/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.loaders;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * What the loaders in the core do with the options on a source, now that they all read them the same way.
 *
 * @author Matteo Baccan
 */
public class LoaderOptionsTest {

    private static final String ENCODING = "UTF-8";

    // ---------------------------------------------------------------- a query no longer hides a source

    /**
     * An XML served over HTTP as <code>config.xml?v=2</code> used to fail {@code accept}, because that test
     * ran on {@code URL.getFile()} which includes the query. It then fell through to PropertiesLoader, which
     * accepts everything it can resolve, and was read as a properties file: no error, no properties.
     */
    @Test
    public void testAnXmlWithAQueryIsStillRecognisedAsXml() throws URISyntaxException {
        assertTrue(new XMLLoader().accept(new URI("http://host/config.xml?v=2")));
        assertTrue(new XMLLoader().accept(new URI("http://host/config.xml?v=2#a=1")));
        assertFalse(new XMLLoader().accept(new URI("http://host/config.properties?v=2")));
    }

    @Test
    public void testAnEnvWithAQueryIsStillRecognisedAsEnv() throws URISyntaxException {
        assertTrue(new DotEnvLoader().accept(new URI("http://host/app.env?token=abc")));
        assertTrue(new DotEnvLoader().accept(new URI("http://host/app.env?token=abc#dialect=dotenv")));
    }

    // ---------------------------------------------------------------- a loader with no options says so

    @Test
    public void testThePropertiesLoaderRefusesAnOption() throws IOException, URISyntaxException {
        File file = write("app", ".properties", "host=localhost");
        try {
            new PropertiesLoader().load(new Properties(), new URI(file.toURI() + "#dialect=dotenv"));
            fail("the properties loader takes no options and should refuse one");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("this source takes none"));
        }
    }

    @Test
    public void testTheXmlLoaderRefusesAnOption() throws IOException, URISyntaxException {
        File file = write("app", ".xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\n"
                + "<properties><entry key=\"host\">localhost</entry></properties>");
        try {
            new XMLLoader().load(new Properties(), new URI(file.toURI() + "#dialect=dotenv"));
            fail("the XML loader does not take a dialect and should refuse one");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("dialect"));
            assertTrue(e.getMessage(), e.getMessage().contains("validate"));
        }
    }

    @Test
    public void testAPropertiesSourceWithoutOptionsIsUnaffected() throws IOException, URISyntaxException {
        File file = write("app", ".properties", "host=localhost");
        Properties result = new Properties();
        new PropertiesLoader().load(result, file.toURI());
        assertEquals("localhost", result.getProperty("host"));
    }

    // ---------------------------------------------------------------- the case the fragment exists for

    /**
     * A resource inside a jar resolves to an opaque URI, where {@link URI#getQuery()} is null and options in
     * a query would have been unreadable. The fragment survives, and {@code openStream()} ignores it - which
     * is the whole reason the options live there. Pinned end to end rather than reasoned about.
     */
    @Test
    public void testOptionsAreReadOnAResourceInsideAJar() throws IOException, URISyntaxException {
        // Files.createTempFile rather than File.createTempFile: it creates the file with owner-only
        // permissions, which is the same reason #325 changed the ones in the library itself
        File jar = Files.createTempFile("owner-test", ".jar").toFile();
        jar.deleteOnExit();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("conf/app.env"));
            zip.write("NAME=\"Matteo\"\n".getBytes(ENCODING));
            zip.closeEntry();
        }

        URI inTheJar = new URI("jar:" + jar.toURI() + "!/conf/app.env");
        assertTrue(inTheJar.isOpaque());

        Properties asIs = new Properties();
        new DotEnvLoader().load(asIs, inTheJar);
        assertEquals("the docker dialect keeps the quotes", "\"Matteo\"", asIs.getProperty("NAME"));

        Properties withOptions = new Properties();
        new DotEnvLoader().load(withOptions, new URI(inTheJar + "#dialect=dotenv"));
        assertEquals("the fragment was read on an opaque URI", "Matteo", withOptions.getProperty("NAME"));
    }

    private static File write(String prefix, String suffix, String content) throws IOException {
        File file = Files.createTempFile(prefix, suffix).toFile();
        file.deleteOnExit();
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(ENCODING));
        }
        return file;
    }
}
