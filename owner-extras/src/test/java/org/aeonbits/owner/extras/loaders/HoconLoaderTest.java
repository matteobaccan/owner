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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * What a HOCON document flattens to. The parsing is Typesafe Config's and is not retested here; what is
 * tested is the mapping onto the keys this library reads, and the handful of decisions that mapping had to
 * take.
 *
 * @author Matteo Baccan
 */
public class HoconLoaderTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final HoconLoader loader = new HoconLoader();

    @Test
    public void acceptsConfAndNothingElse() {
        assertTrue(loader.accept(URI.create("file:/etc/app.conf")));
        assertTrue(loader.accept(URI.create("file:/etc/APP.CONF")));
        assertFalse(loader.accept(URI.create("file:/etc/app.json")));
        assertFalse(loader.accept(URI.create("file:/etc/app.properties")));
    }

    @Test
    public void survivesASourceWithNoScheme() {
        assertFalse(loader.accept(URI.create("")));
        assertFalse(loader.accept(URI.create("myconfig.properties")));
    }

    @Test
    public void theDefaultSpecIsTheNameWithConfOnIt() {
        assertEquals("classpath:MyConfig.conf", loader.defaultSpecFor("classpath:MyConfig"));
    }

    @Test
    public void anObjectBecomesTheDottedKeys() throws Exception {
        Properties read = read("server { host = localhost\n port = 8080 }");
        assertEquals("localhost", read.get("server.host"));
        assertEquals("8080", read.get("server.port"));
    }

    @Test
    public void aDottedPathIsTheSameThing() throws Exception {
        assertEquals("localhost", read("server.host = localhost").get("server.host"));
    }

    @Test
    public void aListOfValuesIsIndexed() throws Exception {
        Properties read = read("ports = [80, 443]");
        assertEquals("80", read.get("ports[0]"));
        assertEquals("443", read.get("ports[1]"));
    }

    @Test
    public void aListOfObjectsIsIndexedAndNested() throws Exception {
        Properties read = read("servers = [ { host = alpha }, { host = beta } ]");
        assertEquals("alpha", read.get("servers[0].host"));
        assertEquals("beta", read.get("servers[1].host"));
    }

    @Test
    public void anEmptyListIsAnEmptyValue() throws Exception {
        // as for JSON: the library reads an empty value as an empty collection, and it overrides a default
        assertEquals("", read("servers = []").get("servers"));
    }

    @Test
    public void aNullWritesNoKeyAtAll() throws Exception {
        Properties read = read("proxy = null\nhost = there");
        assertFalse(read.containsKey("proxy"));
        assertEquals("there", read.get("host"));
    }

    @Test
    public void objectsWithTheSameKeyMerge() throws Exception {
        // the behaviour that makes HOCON HOCON, and the reason this adapts the reference implementation
        // rather than approximating it: JSON refuses a repeated key, HOCON merges
        Properties read = read("a { b = 1 }\na { c = 2 }");
        assertEquals("1", read.get("a.b"));
        assertEquals("2", read.get("a.c"));
    }

    @Test
    public void aSubstitutionIsResolvedWithinTheDocument() throws Exception {
        Properties read = read("base = /srv\npath = ${base}/app");
        assertEquals("/srv/app", read.get("path"));
    }

    @Test
    public void aSelfReferentialSubstitutionResolves() throws Exception {
        assertEquals("/bin:/usr/bin", read("path = /bin\npath = ${path}\":/usr/bin\"").get("path"));
    }

    @Test
    public void anOptionalSubstitutionThatResolvesToNothingLeavesItsKeyOut() throws Exception {
        Properties read = read("host = ${?NO_SUCH_VARIABLE_ANYWHERE}\nkept = yes");
        assertFalse(read.containsKey("host"));
        assertEquals("yes", read.get("kept"));
    }

    @Test
    public void aSubstitutionFallsBackOnASystemProperty() throws Exception {
        String key = "owner.hocon.test.property";
        System.setProperty(key, "from the system");
        com.typesafe.config.ConfigFactory.invalidateCaches();
        try {
            assertEquals("from the system", read("value = ${" + key + "}").get("value"));
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    public void whatWasUsedToResolveDoesNotBecomeAProperty() throws Exception {
        // resolveWith looks a substitution up elsewhere without taking any content from it: the whole
        // environment and every system property would otherwise land in the configuration
        String key = "owner.hocon.test.property";
        System.setProperty(key, "from the system");
        com.typesafe.config.ConfigFactory.invalidateCaches();
        try {
            Properties read = read("value = ${" + key + "}");
            assertEquals(1, read.size());
            assertNull(read.get(key));
            assertNull(read.get("java.version"));
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    public void aRequiredSubstitutionThatResolvesToNothingIsRefused() throws Exception {
        try {
            read("host = ${no.such.path.anywhere}");
            fail("an unresolved substitution was expected to be refused");
        } catch (UnsupportedOperationException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("could not be read as HOCON"));
        }
    }

    @Test
    public void aBrokenDocumentIsRefusedUncheckedRatherThanSwallowed() throws Exception {
        // an IOException here would be caught by LoadType.FIRST and MERGE and the configuration would come
        // back full of defaults with nothing said
        try {
            read("this { is = not ]] hocon");
            fail("a malformed document was expected to be refused");
        } catch (UnsupportedOperationException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("could not be read as HOCON"));
        }
    }

    @Test
    public void aNumberComesBackAsTheReferenceImplementationUnderstoodIt() throws Exception {
        // stated in the javadoc because it cannot be seen: Typesafe Config parses eagerly into typed values
        // and does not keep the text, where our own parsers hand the characters over untouched. Our JSON
        // reader answers 1e3 here; what a number is normalised *to* is the reference implementation's
        // business and this only pins that it is normalised at all
        assertEquals("1000", read("size = 1e3").get("size"));
        assertEquals("1.5", read("ratio = 1.50").get("ratio"));
        assertEquals("10s", read("timeout = 10s").get("timeout"));
        assertEquals("512K", read("buffer = 512K").get("buffer"));
    }

    @Test
    public void commentsAndQuotedKeysAreTheParsersBusiness() throws Exception {
        Properties read = read("# a comment\n\"quoted.key\" = value // another comment");
        assertEquals("value", read.get("quoted.key"));
    }

    private int documents;

    private Properties read(String document) throws IOException {
        // a name of its own each time: several of the tests read more than one document
        File file = folder.newFile("test" + documents++ + ".conf");
        try (Writer out = new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            out.write(document);
        }
        Properties result = new Properties();
        loader.load(result, file.toURI());
        return result;
    }
}
