/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.Prefix;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Writing a configuration that has <b>sections</b>, which is the half of
 * {@link Accessible#save(File)} that nothing was asserting.
 * <p>
 * It shipped dropping them: the keys a configuration owns were read off its own methods, so a section
 * accessor contributed the bare path - <code>db</code>, which is no property - and every value underneath
 * was left to whoever else reads the file, which when the file is being generated means lost. Every test
 * here is a shape that walk has to survive, and each of them fails on the version that shipped.
 * </p>
 *
 * @author Matteo Baccan
 */
public class WritingNestedSectionsTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    // ------------------------------------------------------------------ the shapes

    public interface Db extends Config {

        @Description("A host name or an address.")
        @DefaultValue("localhost")
        String host();

        @DefaultValue("5432")
        int port();
    }

    public interface App extends Config, Accessible {

        @DefaultValue("acme")
        String name();

        @Description("The database we talk to.")
        Db db();
    }

    @Test
    public void aSectionContributesItsKeysAndItsDescriptions() throws IOException {
        String written = saved(App.class);

        assertTrue(written, written.contains("name = acme"));
        assertTrue(written, written.contains("db.host = localhost"));
        assertTrue(written, written.contains("db.port = 5432"));
        assertTrue("a description written inside the section reaches the key it describes",
                written.contains("# A host name or an address."));
        assertTrue("and the one on the accessor is a heading over the block, which is what WRITING.md "
                        + "promised and nothing had ever written",
                written.contains("# The database we talk to.\n# A host name or an address.\ndb.host"));
    }

    /**
     * The heading is written by the code and therefore rewritten, which only works if the second save
     * finds it and replaces it rather than adding another above it. It touches the key for exactly that
     * reason: the convention says a comment block touching a key is ours, and one above a blank line is
     * the file's — so a blank line under the heading would turn it into a note of the file's own, kept
     * forever and joined by a fresh copy on every save.
     */
    @Test
    public void savingTwiceDoesNotStackTheHeadings() throws IOException {
        File file = new File(folder.getRoot(), "twice.properties");
        App config = ConfigFactory.create(App.class);
        config.save(file);
        String once = new String(Files.readAllBytes(file.toPath()), ISO_8859_1);

        config.save(file);
        String twice = new String(Files.readAllBytes(file.toPath()), ISO_8859_1);

        assertEquals(once, twice);
        assertEquals("one heading, not two", 1,
                twice.split("# The database we talk to\\.", -1).length - 1);
    }

    public interface Two extends Config, Accessible {

        Db primary();

        Db replica();
    }

    /** The same interface under two accessors is two sections, and neither may swallow the other. */
    @Test
    public void oneInterfaceUsedTwiceIsWrittenUnderBothPaths() throws IOException {
        String written = saved(Two.class);

        assertTrue(written, written.contains("primary.host = localhost"));
        assertTrue(written, written.contains("replica.host = localhost"));
        assertTrue(written, written.contains("primary.port = 5432"));
        assertTrue(written, written.contains("replica.port = 5432"));
    }

    public interface Middle extends Config {

        Db db();

        @DefaultValue("2")
        int retries();
    }

    public interface Deep extends Config, Accessible {

        Middle middle();
    }

    /** Sections nest to any depth when read, so they do when written. */
    @Test
    public void aSectionInsideASectionIsWrittenAtItsFullPath() throws IOException {
        String written = saved(Deep.class);

        assertTrue(written, written.contains("middle.retries = 2"));
        assertTrue(written, written.contains("middle.db.host = localhost"));
    }

    @Prefix("http.")
    public interface Prefixed extends Config {

        @DefaultValue("8080")
        int port();
    }

    public interface Holding extends Config, Accessible {

        Prefixed server();
    }

    /** The prefix of a nested interface composes with the path, and the file has to show the composition. */
    @Test
    public void aPrefixInsideASectionComposesInTheFileToo() throws IOException {
        assertTrue(saved(Holding.class).contains("server.http.port = 8080"));
    }

    /**
     * A section that holds one of its own kind never reaches the writer at all: such a configuration is
     * refused when it is created - <i>"the nested configuration interfaces form a cycle... there is no
     * configuration to build"</i> - which is asserted in {@link NestedPropertiesTest}. Written down here
     * because it is the question anybody reading a recursive walk asks next.
     */

    // ------------------------------------------------------------------ the shapes that must be left alone

    public interface Cluster extends Config, Accessible, Mutable {

        List<Db> servers();

        Map<String, Db> byName();

        @DefaultValue("acme")
        String name();
    }

    /**
     * A group - a list of sections, a map of them - has no key to be named by until something is written
     * under it, so its keys are not part of what this configuration owns. That has a consequence worth
     * pinning: lines like <code>servers[0].host</code> already in the file are <b>somebody else's</b> as
     * far as the writer is concerned, which is the safe way round - they are kept exactly as they are
     * rather than rewritten under a key nobody can predict, or dropped.
     */
    @Test
    public void theKeysOfAGroupAreKeptAsTheyAreRatherThanRewritten() throws IOException {
        File file = folder.newFile("cluster.properties");
        Files.write(file.toPath(), ("name = acme\n"
                + "servers[0].host = alpha\n"
                + "byName.first.host = beta\n").getBytes(ISO_8859_1));

        Cluster config = ConfigFactory.create(Cluster.class, given("test.nested.file", file.toString()));
        config.save(file);

        String written = new String(Files.readAllBytes(file.toPath()), ISO_8859_1);
        assertTrue(written, written.contains("servers[0].host = alpha"));
        assertTrue(written, written.contains("byName.first.host = beta"));
    }

    // ------------------------------------------------------------------ and back again

    /**
     * The round trip, which is the only assertion that covers the whole of it: what was written is read
     * back through the accessors that produced it, section by section.
     */
    @Test
    public void whatIsWrittenIsReadBackThroughTheSections() throws IOException {
        File file = folder.newFile("round-trip.properties");
        ConfigFactory.create(App.class).save(file);

        Properties read = new Properties();
        try (java.io.InputStream in = Files.newInputStream(file.toPath())) {
            read.load(in);
        }
        App back = ConfigFactory.create(App.class, read);

        assertEquals("acme", back.name());
        assertEquals("localhost", back.db().host());
        assertEquals(5432, back.db().port());
    }

    /** The tool writes the same file, sections included: it is the same writer and must stay so. */
    @Test
    public void theTemplateToolWritesTheSectionsAsWell() {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int code = TemplateTool.run(new String[] {App.class.getName()},
                new java.io.PrintStream(out, true), new java.io.PrintStream(new java.io.ByteArrayOutputStream()));

        String written = new String(out.toByteArray(), ISO_8859_1);
        assertEquals(written, 0, code);
        assertTrue(written, written.contains("db.host = localhost"));
        assertTrue(written, written.contains("# A host name or an address."));
    }

    private String saved(Class<? extends Accessible> configClass) throws IOException {
        File file = new File(folder.getRoot(), configClass.getSimpleName() + ".properties");
        ConfigFactory.create(configClass).save(file);
        return new String(Files.readAllBytes(file.toPath()), ISO_8859_1);
    }

    private static Properties given(String key, String value) {
        Properties properties = new Properties();
        properties.setProperty(key, value);
        return properties;
    }
}
