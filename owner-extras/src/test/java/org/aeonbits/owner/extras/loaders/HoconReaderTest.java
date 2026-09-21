/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.loaders;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Direct unit tests for {@link HoconReader}.
 *
 * @author Matteo Baccan
 */
public class HoconReaderTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private int documents;

    @Test
    public void privateConstructorCanBeInstantiatedForCoverage() throws Exception {
        Constructor<HoconReader> constructor = HoconReader.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        HoconReader instance = constructor.newInstance();
        assertNotNull(instance);
    }

    @Test
    public void readsObjectAndFlattensKeys() throws Exception {
        Properties result = read("server { host = localhost\n port = 8080 }");
        assertEquals("localhost", result.getProperty("server.host"));
        assertEquals("8080", result.getProperty("server.port"));
    }

    @Test
    public void readsIndexedListsAndNestedObjectLists() throws Exception {
        Properties result = read("ports = [80, 443]\nservers = [ { host = alpha }, { host = beta } ]");
        assertEquals("80", result.getProperty("ports[0]"));
        assertEquals("443", result.getProperty("ports[1]"));
        assertEquals("alpha", result.getProperty("servers[0].host"));
        assertEquals("beta", result.getProperty("servers[1].host"));
    }

    @Test
    public void handlesEmptyListAndNullValue() throws Exception {
        Properties result = read("emptyList = []\nnullValue = null\nvalidKey = value");
        assertEquals("", result.getProperty("emptyList"));
        assertFalse(result.containsKey("nullValue"));
        assertEquals("value", result.getProperty("validKey"));
    }

    @Test
    public void resolvesSubstitutionsAndSelfReferences() throws Exception {
        Properties result = read("base = /srv\npath = ${base}/app\nenvPath = /bin\nenvPath = ${envPath}\":/usr/bin\"");
        assertEquals("/srv/app", result.getProperty("path"));
        assertEquals("/bin:/usr/bin", result.getProperty("envPath"));
    }

    @Test
    public void handlesOptionalSubstitutionAndSystemProperties() throws Exception {
        String key = "owner.hocon.reader.test.prop";
        System.setProperty(key, "sysVal");
        com.typesafe.config.ConfigFactory.invalidateCaches();
        try {
            Properties result = read("missingOpt = ${?NON_EXISTENT_VAR}\nsysProp = ${" + key + "}");
            assertFalse(result.containsKey("missingOpt"));
            assertEquals("sysVal", result.getProperty("sysProp"));
            assertNull(result.getProperty(key));
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    public void throwsIOExceptionForMissingSourceFile() {
        File missingFile = new File(folder.getRoot(), "does-not-exist.conf");
        Properties result = new Properties();
        try {
            HoconReader.read(result, missingFile.toURI());
            fail("Expected IOException for non-existent file");
        } catch (IOException expected) {
            // expected
        }
    }

    @Test
    public void throwsUnsupportedOperationExceptionOnParseErrorAndHidesCredentials() throws Exception {
        File file = writeDocument("invalid { hocon = [ ]");
        URI uriWithCreds = URI.create("file://user:secretpassword@localhost" + file.getAbsolutePath());

        try {
            Properties result = new Properties();
            HoconReader.read(result, uriWithCreds);
            fail("Expected UnsupportedOperationException for malformed document");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage().contains("could not be read as HOCON"));
            assertTrue(expected.getMessage().contains("***@localhost"));
            assertFalse(expected.getMessage().contains("secretpassword"));
        }
    }

    @Test
    public void throwsUnsupportedOperationExceptionForMissingRequiredSubstitution() throws Exception {
        try {
            read("host = ${no.such.required.property.anywhere}");
            fail("Expected UnsupportedOperationException for unresolved required substitution");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage().contains("could not be read as HOCON"));
        }
    }

    private Properties read(String content) throws IOException {
        File file = writeDocument(content);
        Properties result = new Properties();
        HoconReader.read(result, file.toURI());
        return result;
    }

    private File writeDocument(String content) throws IOException {
        File file = folder.newFile("test" + documents++ + ".conf");
        try (Writer out = new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            out.write(content);
        }
        return file;
    }
}
