/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.formats;

import org.aeonbits.owner.Accessible;
import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.DeclaredOnly;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Factory;
import org.aeonbits.owner.Traceable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * See: https://github.com/matteobaccan/owner/issues/165 — the directive in the formats that arrived with
 * 2.0.0.
 * <p>
 * <b>Nothing here is arranged for.</b> A loader hands back a flat {@link java.util.Properties}, a nested
 * document having been flattened with dots and brackets on the way, and the directive is read out of that
 * map like any other key. So a JSON document may name a YAML one, a TOML one may name a properties file,
 * and the chain crosses formats without anything knowing it did. It is worth measuring precisely because
 * nothing was written to make it work: what is being checked is that the flattening leaves the key where
 * the directive is looked for.
 * </p>
 * <p>
 * The rule these formats need and a properties file gets for free is <b>the directive is recognised at the
 * root of the document and nowhere else</b>: written one level down it flattens to
 * <code>section.owner.include</code> and is somebody's property. In a tree-shaped format that is not a
 * corner case — it is where a person would put it by accident.
 * </p>
 *
 * @author Matteo Baccan
 */
public class Issue165FormatsTest {

    private static final File DIR = new File("target/issue165formats");

    private Factory factory;

    @Before
    public void before() throws IOException {
        Files.createDirectories(DIR.toPath());
        factory = ConfigFactory.newInstance();
    }

    @After
    public void after() {
        File[] found = DIR.listFiles();
        if (found != null)
            for (File file : found)
                file.delete();
    }

    private static void write(String name, String content) throws IOException {
        try (OutputStream out = Files.newOutputStream(new File(DIR, name).toPath())) {
            out.write(content.getBytes(UTF_8));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // one test per format: the directive is read, and it is not a property afterwards
    // ------------------------------------------------------------------------------------------------

    @Sources("file:target/issue165formats/from.json")
    interface FromJson extends Config, Accessible {
        String fromParent();

        String own();
    }

    /** JSON: a string at the root of the object. */
    @Test
    public void theDirectiveWorksInAJsonDocument() throws IOException {
        write("parent.properties", "fromParent = from the parent\n");
        write("from.json", "{\n"
                + "  \"owner.include\": \"file:target/issue165formats/parent.properties\",\n"
                + "  \"own\": \"from the json\"\n"
                + "}\n");

        FromJson cfg = factory.create(FromJson.class);
        assertEquals("from the json", cfg.own());
        assertEquals("from the parent", cfg.fromParent());
        assertFalse(cfg.propertyNames().contains("owner.include"));
    }

    @Sources("file:target/issue165formats/from.yaml")
    interface FromYaml extends Config, Accessible {
        String fromParent();

        String own();
    }

    /** YAML: a scalar at the root of the mapping. */
    @Test
    public void theDirectiveWorksInAYamlDocument() throws IOException {
        write("parent.properties", "fromParent = from the parent\n");
        write("from.yaml", "owner.include: file:target/issue165formats/parent.properties\n"
                + "own: from the yaml\n");

        FromYaml cfg = factory.create(FromYaml.class);
        assertEquals("from the yaml", cfg.own());
        assertEquals("from the parent", cfg.fromParent());
        assertFalse(cfg.propertyNames().contains("owner.include"));
    }

    @Sources("file:target/issue165formats/from.toml")
    interface FromToml extends Config, Accessible {
        String fromParent();

        String own();
    }

    /**
     * TOML: a bare key at the top of the document, before any table.
     * <p>
     * The quoting is the format's and not ours — <code>owner.include</code> unquoted is a dotted key, which
     * TOML reads as <code>include</code> inside a table called <code>owner</code>. It flattens back to the
     * same string either way, which is worth knowing and worth not relying on: written as
     * <code>"owner.include"</code> it is one key and means what it says.
     * </p>
     */
    @Test
    public void theDirectiveWorksInATomlDocument() throws IOException {
        write("parent.properties", "fromParent = from the parent\n");
        write("from.toml", "\"owner.include\" = \"file:target/issue165formats/parent.properties\"\n"
                + "own = \"from the toml\"\n");

        FromToml cfg = factory.create(FromToml.class);
        assertEquals("from the toml", cfg.own());
        assertEquals("from the parent", cfg.fromParent());
        assertFalse(cfg.propertyNames().contains("owner.include"));
    }

    @Sources("file:target/issue165formats/dotted.toml")
    interface FromDottedToml extends Config, Accessible {
        String fromParent();
    }

    /**
     * TOML unquoted, which is how somebody writing the file by hand will write it.
     * <p>
     * It is a different document — a dotted key is <code>include</code> inside a table called
     * <code>owner</code>, not a key with a dot in its name — and it <b>flattens to the same string</b>, so
     * the directive is found either way. Pinned rather than relied on: it holds because of how the
     * flattening works, and the two spellings meaning the same thing is the sort of coincidence that stops
     * being one the day a parser changes.
     * </p>
     */
    @Test
    public void theDirectiveWorksUnquotedInTomlToo() throws IOException {
        write("parent.properties", "fromParent = from the parent\n");
        write("dotted.toml", "owner.include = \"file:target/issue165formats/parent.properties\"\n");

        FromDottedToml cfg = factory.create(FromDottedToml.class);
        assertEquals("from the parent", cfg.fromParent());
        assertFalse(cfg.propertyNames().contains("owner.include"));
    }

    // ------------------------------------------------------------------------------------------------
    // below the root it is a property and not a directive
    // ------------------------------------------------------------------------------------------------

    @Sources("file:target/issue165formats/nested.yaml")
    interface NotADirective extends Config, Accessible {
        @DefaultValue("its default")
        String fromParent();
    }

    /**
     * The same key one level down is <b>not</b> the directive: it flattens to
     * <code>database.owner.include</code> and stays a property.
     * <p>
     * This is the test the nested formats are here for. A rule that matched the end of a key would turn any
     * <code>owner.include</code> at any depth into a directive, and in a tree-shaped document that is
     * exactly where somebody would write one meaning something else.
     * </p>
     */
    @Test
    public void theDirectiveIsNotRecognisedBelowTheRootOfADocument() throws IOException {
        write("parent.properties", "fromParent = from the parent\n");
        write("nested.yaml", "database:\n"
                + "  owner.include: file:target/issue165formats/parent.properties\n");

        NotADirective cfg = factory.create(NotADirective.class);
        assertEquals("its default", cfg.fromParent());
        assertEquals("file:target/issue165formats/parent.properties",
                cfg.getProperty("database.owner.include"));
    }

    // ------------------------------------------------------------------------------------------------
    // the directive written twice
    // ------------------------------------------------------------------------------------------------

    @Sources("file:target/issue165formats/twice.json")
    interface TwiceInJson extends Config, Accessible {
        @DefaultValue("nothing")
        String which();
    }

    @Sources("file:target/issue165formats/twice.yaml")
    interface TwiceInYaml extends Config, Accessible {
        @DefaultValue("nothing")
        String which();
    }

    @Sources("file:target/issue165formats/twice.toml")
    interface TwiceInToml extends Config, Accessible {
        @DefaultValue("nothing")
        String which();
    }

    /**
     * The directive written twice: <b>these three formats refuse the whole document</b>, naming the key and
     * the line, and the configuration is left with its defaults.
     * <p>
     * Nothing about the directive is responsible for that — a repeated name is refused by each of these
     * three parsers whatever the name is, which is their own specification talking. It is measured here
     * because the same two lines mean three different things across the formats this library reads: a
     * properties file keeps the last and loses the first in silence, an INI file turns them into a list and
     * includes nothing (reported), and these refuse the document. A person who moves a configuration from
     * one format to another needs that written down.
     * </p>
     */
    @Test
    public void aDirectiveWrittenTwiceRefusesTheWholeDocument() throws IOException {
        write("one.properties", "which = one\n");
        write("two.properties", "which = two\n");

        write("twice.json", "{\n"
                + "  \"owner.include\": \"file:target/issue165formats/one.properties\",\n"
                + "  \"owner.include\": \"file:target/issue165formats/two.properties\"\n"
                + "}\n");
        write("twice.yaml", "owner.include: file:target/issue165formats/one.properties\n"
                + "owner.include: file:target/issue165formats/two.properties\n");
        write("twice.toml", "\"owner.include\" = \"file:target/issue165formats/one.properties\"\n"
                + "\"owner.include\" = \"file:target/issue165formats/two.properties\"\n");

        assertEquals("nothing", factory.create(TwiceInJson.class).which());
        assertEquals("nothing", ConfigFactory.newInstance().create(TwiceInYaml.class).which());
        assertEquals("nothing", ConfigFactory.newInstance().create(TwiceInToml.class).which());
    }

    // ------------------------------------------------------------------------------------------------
    // inheritance across formats
    // ------------------------------------------------------------------------------------------------

    @Sources("file:target/issue165formats/chain.json")
    interface Crossing extends Config, Accessible, Traceable {
        String fromJson();

        String fromYaml();

        String fromToml();

        String fromProperties();

        String shared();

        String twoDeep();
    }

    /**
     * A chain four formats long: JSON includes YAML includes TOML includes a properties file.
     * <p>
     * <b>The inheritance goes the whole way down</b>, which is what a two-level test cannot tell apart from
     * "the root always wins": each file beats the one it includes, and where the root says nothing the
     * second beats the third. The keys nobody declared travel too, and {@code originOf} names the document
     * each value actually came from, four steps away from the one the interface declares.
     * </p>
     */
    @Test
    public void aChainThatCrossesFourFormats() throws IOException {
        write("fourth.properties", "fromProperties = from the properties\n"
                + "shared = the properties file's\n"
                + "twoDeep = the properties file's\n"
                + "undeclared = the properties file's\n");
        write("third.toml", "\"owner.include\" = \"file:target/issue165formats/fourth.properties\"\n"
                + "fromToml = \"from the toml\"\n"
                + "shared = \"the toml's\"\n"
                + "twoDeep = \"the toml's\"\n");
        write("second.yaml", "owner.include: file:target/issue165formats/third.toml\n"
                + "fromYaml: from the yaml\n"
                + "shared: the yaml's\n"
                + "twoDeep: the yaml's\n");
        write("chain.json", "{\n"
                + "  \"owner.include\": \"file:target/issue165formats/second.yaml\",\n"
                + "  \"fromJson\": \"from the json\",\n"
                + "  \"shared\": \"the json's\"\n"
                + "}\n");

        Crossing cfg = factory.create(Crossing.class);
        assertEquals("from the json", cfg.fromJson());
        assertEquals("from the yaml", cfg.fromYaml());
        assertEquals("from the toml", cfg.fromToml());
        assertEquals("from the properties", cfg.fromProperties());

        // the root wins where it speaks...
        assertEquals("the json's", cfg.shared());
        // ...and where it does not, the next one down does, all the way to the bottom
        assertEquals("the yaml's", cfg.twoDeep());

        // a key nobody declared travels the chain like any other
        assertEquals("the properties file's", cfg.getProperty("undeclared"));
        assertTrue(cfg.propertyNames().contains("undeclared"));

        // and every value is attributed to the document it came from
        assertTrue(cfg.originOf("fromToml").source().endsWith("third.toml"));
        assertTrue(cfg.originOf("fromProperties").source().endsWith("fourth.properties"));
        assertTrue(cfg.originOf("shared").source().endsWith("chain.json"));
    }

    // ------------------------------------------------------------------------------------------------
    // a nested document included by a flat one
    // ------------------------------------------------------------------------------------------------

    interface Server extends Config {
        String host();

        int port();
    }

    @Sources("file:target/issue165formats/flat.properties")
    interface WithASection extends Config, Accessible {
        Server server();

        String name();
    }

    @DeclaredOnly
    @Sources("file:target/issue165formats/flat.properties")
    interface WithASectionDeclaredOnly extends Config, Accessible {
        Server server();

        String name();
    }

    /**
     * A properties file may include a YAML document, and <b>the section that document describes is read as
     * a section</b>: the nesting survives the include, because an include is a source and a source is
     * flattened before anything else happens to it.
     * <p>
     * The values a section is built from are ordinary keys — <code>server.host</code>,
     * <code>server.port</code> — so there was never a second thing to make work here. It earns a test
     * because the two features were built a week apart and nobody would assume they meet.
     * </p>
     */
    @Test
    public void aFlatFileMayIncludeADocumentWithSectionsInIt() throws IOException {
        write("sections.yaml", "server:\n"
                + "  host: localhost\n"
                + "  port: 9090\n"
                + "unread:\n"
                + "  deep: and nobody asked for it\n");
        write("flat.properties", "owner.include = file:target/issue165formats/sections.yaml\n"
                + "name = the flat one\n"
                + "server.port = 8080\n");

        WithASection cfg = factory.create(WithASection.class);
        assertEquals("the flat one", cfg.name());
        // the section comes from the included document...
        assertEquals("localhost", cfg.server().host());
        // ...key by key, so the including file overrides one of them and not the whole section
        assertEquals(8080, cfg.server().port());
        assertEquals("and nobody asked for it", cfg.getProperty("unread.deep"));
    }

    /**
     * The same, restricted: {@link DeclaredOnly} narrows the view to the keys the interface declares —
     * <b>including the ones a section declares</b> — and the value of each still comes from the included
     * document.
     * <p>
     * The two features do not meet: {@link DeclaredOnly} restricts the views, includes decide what was
     * read. The directive is invisible under either, having never been a property at all.
     * </p>
     */
    @Test
    public void declaredOnlyOverAnIncludedDocument() throws IOException {
        write("sections.yaml", "server:\n"
                + "  host: localhost\n"
                + "  port: 9090\n"
                + "unread:\n"
                + "  deep: and nobody asked for it\n");
        write("flat.properties", "owner.include = file:target/issue165formats/sections.yaml\n"
                + "name = the flat one\n");

        WithASectionDeclaredOnly cfg = factory.create(WithASectionDeclaredOnly.class);
        assertEquals("localhost", cfg.server().host());
        assertEquals(9090, cfg.server().port());

        assertTrue(cfg.propertyNames().contains("server.host"));
        assertTrue(cfg.propertyNames().contains("name"));
        assertFalse(cfg.propertyNames().contains("unread.deep"));
        assertFalse(cfg.propertyNames().contains("owner.include"));
        assertNull(cfg.getProperty("owner.include"));
    }
}
