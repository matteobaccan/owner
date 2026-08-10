/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.loaders.DotEnvLoader;
import org.aeonbits.owner.loaders.Loader;
import org.aeonbits.owner.loaders.PropertiesLoader;
import org.aeonbits.owner.loaders.SystemLoader;
import org.aeonbits.owner.loaders.XMLLoader;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * What a source is, end to end: which loader answers for it, what survives variable expansion, and what the
 * fragment does once it has been through the whole of {@code @Sources}.
 * <p>
 * The pieces are tested one at a time elsewhere - {@code SourceOptionsTest} for the parsing,
 * {@code LoaderOptionsTest} for the loaders, {@code ConfigURIFactoryTest} for the resolution. This is the
 * table nobody could see from any single one of them.
 * </p>
 *
 * @author Matteo Baccan
 */
public class SourceUriTest {

    private final LoadersManager loaders = new LoadersManager(Collections.<Loader>emptyList());

    // ---------------------------------------------------------------- who answers for what

    @Test
    public void theExtensionDecides() {
        assertLoader(PropertiesLoader.class, "file:/app/config.properties");
        assertLoader(XMLLoader.class, "file:/app/config.xml");
        assertLoader(DotEnvLoader.class, "file:/app/config.env");
        assertLoader(SystemLoader.class, "system:properties");
        assertLoader(SystemLoader.class, "system:env");
    }

    /** A file that is all extension, and the relative form that makes the URI opaque. */
    @Test
    public void aDotEnvIsRecognisedWhateverItIsCalled() {
        assertLoader(DotEnvLoader.class, "file:/app/.env");
        assertLoader(DotEnvLoader.class, "file:.env");
        assertLoader(DotEnvLoader.class, "file:/app/staging.env");
    }

    @Test
    public void caseDoesNotDecideAnything() {
        assertLoader(XMLLoader.class, "file:/app/CONFIG.XML");
        assertLoader(DotEnvLoader.class, "file:/app/Config.Env");
    }

    /**
     * The query is the server's business and must not change which loader answers - this is the shape of the
     * bug where an XML served with a version parameter was read as a properties file.
     */
    @Test
    public void aQueryDoesNotChangeTheFormat() {
        assertLoader(XMLLoader.class, "http://host/config.xml?v=2");
        assertLoader(DotEnvLoader.class, "http://host/app.env?token=abc");
        assertLoader(PropertiesLoader.class, "http://host/app.properties?v=2");
    }

    @Test
    public void neitherDoTheOptions() {
        assertLoader(XMLLoader.class, "file:/app/config.xml#a=1");
        assertLoader(DotEnvLoader.class, "file:.env#dialect=dotenv&quotes=strip");
        assertLoader(DotEnvLoader.class, "http://host/app.env?token=abc#dialect=dotenv");
    }

    /** Inside a jar the URI is opaque, and half the reason the options are in the fragment. */
    @Test
    public void aResourceInsideAJarIsRecognisedToo() {
        assertLoader(DotEnvLoader.class, "jar:file:/a.jar!/conf/app.env");
        assertLoader(XMLLoader.class, "jar:file:/a.jar!/conf/config.xml#a=1");
    }

    /**
     * Only the end of the path counts. A directory called <code>.env</code> is not a <code>.env</code> file,
     * and a backup of one is not one either.
     */
    @Test
    public void theExtensionIsTheEndOfThePathAndNotAPieceOfIt() {
        assertLoader(PropertiesLoader.class, "file:/app/.env/config.properties");
        assertLoader(PropertiesLoader.class, "file:/app/config.env.bak");
        assertLoader(PropertiesLoader.class, "file:/app/xml/config.txt");
    }

    /**
     * PropertiesLoader accepts anything it can resolve, which is what makes an unknown extension work and
     * what makes an undiscovered loader silent. Both halves of that are worth pinning.
     */
    @Test
    public void anUnknownExtensionFallsBackToProperties() {
        assertLoader(PropertiesLoader.class, "file:/app/settings.conf");
        assertLoader(PropertiesLoader.class, "file:/app/config.yaml");
    }

    @Test
    public void aSchemeNobodyKnowsIsRefusedRatherThanGuessed() {
        try {
            loaders.findLoader(uri("db://config/server"));
            fail("no loader can resolve that scheme, and saying so is the point");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Can't resolve a Loader"));
        }
    }

    // ---------------------------------------------------------------- the fragment, end to end

    @Test
    public void theOptionsSurviveTheWholeOfSources() throws IOException {
        File env = write(".env", "NAME=\"Matteo\"\n");

        assertEquals("the docker dialect keeps the quotes", "\"Matteo\"",
                read(env.toURI().toString()));
        assertEquals("the fragment reached the loader", "Matteo",
                read(env.toURI() + "#dialect=dotenv"));
    }

    /**
     * A path written the way Windows writes one is not a legal URI, and {@code ConfigURIFactory} is what
     * turns it into one. The options have to come through that unharmed, which is not obvious: the fixing is
     * done on the text of the whole spec, fragment included.
     */
    @Test
    public void theOptionsSurviveAPathWrittenWithBackslashes() throws IOException {
        File env = write(".env", "NAME=\"Matteo\"\n");
        ConfigURIFactory uris = new ConfigURIFactory(getClass().getClassLoader(),
                new VariablesExpander(new Properties()));

        URI resolved = newURI(uris, "file:" + env.getAbsolutePath().replace('/', '\\') + "#dialect=dotenv");
        assertEquals("dialect=dotenv", resolved.getFragment());

        Properties result = new Properties();
        try {
            new DotEnvLoader().load(result, resolved);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        assertEquals("Matteo", result.getProperty("NAME"));
    }

    /**
     * A spec goes through variable expansion before it becomes a URI, so an option written after a variable
     * has to come out the other side intact.
     */
    @Test
    public void theOptionsSurviveVariableExpansion() throws IOException {
        File env = write(".env", "NAME=\"Matteo\"\n");
        Properties factoryProperties = new Properties();
        factoryProperties.setProperty("here", env.getParent().replace('\\', '/'));

        VariablesExpander expander = new VariablesExpander(factoryProperties);
        ConfigURIFactory uris = new ConfigURIFactory(getClass().getClassLoader(), expander);

        URI resolved = newURI(uris, "file:${here}/" + env.getName() + "#dialect=dotenv");
        assertEquals("dialect=dotenv", resolved.getFragment());

        Properties result = new Properties();
        try {
            new DotEnvLoader().load(result, resolved);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        assertEquals("Matteo", result.getProperty("NAME"));
    }

    /** The options are not part of the resource name, on the classpath as anywhere else. */
    @Test
    public void aClasspathSourceKeepsItsOptionsAndIsStillFound() {
        ConfigURIFactory uris = new ConfigURIFactory(getClass().getClassLoader(),
                new VariablesExpander(new Properties()));

        URI resolved = newURI(uris, "classpath:test.properties#a=1&b=2");
        assertNotNull("the resource was not found once the fragment was appended", resolved);
        assertEquals("a=1&b=2", resolved.getFragment());
        assertTrue(resolved.toString(), resolved.toString().contains("test.properties"));
    }

    @Test
    public void aClasspathSourceThatIsNotThereIsStillNotThere() {
        ConfigURIFactory uris = new ConfigURIFactory(getClass().getClassLoader(),
                new VariablesExpander(new Properties()));

        assertNull(newURI(uris, "classpath:no/such/file.properties#a=1"));
    }

    /** An empty fragment is no options at all, not a malformed one. */
    @Test
    public void anEmptyFragmentIsNotAnError() throws IOException {
        File properties = write(".properties", "host=localhost\n");

        assertEquals("localhost", readProperty(properties.toURI() + "#", "host"));
    }

    // ---------------------------------------------------------------- helpers

    private void assertLoader(Class<? extends Loader> expected, String spec) {
        Loader answered = loaders.findLoader(uri(spec));
        assertEquals(spec, expected, answered.getClass());
    }

    private String read(String spec) {
        Properties result = new Properties();
        try {
            loaders.load(result, uri(spec));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return result.getProperty("NAME");
    }

    private String readProperty(String spec, String key) {
        Properties result = new Properties();
        try {
            loaders.load(result, uri(spec));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return result.getProperty(key);
    }

    private static File write(String suffix, String content) throws IOException {
        File file = File.createTempFile("owner-source", suffix);
        file.deleteOnExit();
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes("UTF-8"));
        }
        return file;
    }

    private static URI newURI(ConfigURIFactory uris, String spec) {
        try {
            return uris.newURI(spec);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(spec, e);
        }
    }

    private static URI uri(String spec) {
        try {
            return new URI(spec);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(spec, e);
        }
    }
}
