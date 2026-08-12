/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.formats.json;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A JSON document read the way anybody would read one: a mapping interface, no registration, no mention of
 * a loader anywhere.
 * <p>
 * It is the claim the roadmap made twice and could not keep until today. The flattening convention was
 * chosen so that a tree-shaped source would produce <code>servers[0].host</code>; nested interfaces were
 * written so that something could read a key of that shape; and this is the two of them meeting on a
 * format neither was written for.
 * </p>
 *
 * @author Matteo Baccan
 */
public class JsonEndToEndTest {

    private static final String PATH = "target/test-generated-resources/JsonEndToEnd.json";

    @BeforeClass
    public static void writeTheDocument() throws IOException {
        File file = new File(PATH);
        Files.createDirectories(Paths.get(file.getParent()));
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            out.write(("{\n"
                    + "  \"name\": \"owner\",\n"
                    + "  \"server\": { \"host\": \"localhost\", \"port\": 9090 },\n"
                    + "  \"servers\": [\n"
                    + "    { \"host\": \"alpha\", \"port\": 1 },\n"
                    + "    { \"host\": \"beta\" }\n"
                    + "  ],\n"
                    + "  \"named\": { \"one\": { \"host\": \"first\" }, \"two\": { \"host\": \"second\" } },\n"
                    + "  \"hosts\": [\"a\", \"b\"],\n"
                    + "  \"proxy\": null,\n"
                    + "  \"proxySection\": null,\n"
                    + "  \"empty\": []\n"
                    + "}\n").getBytes(UTF_8));
        }
    }

    public interface ServerConfig extends Config {
        String host();

        @DefaultValue("8080")
        int port();
    }

    @Sources("file:" + PATH)
    public interface AppConfig extends Config {
        String name();

        ServerConfig server();

        List<ServerConfig> servers();

        Map<String, ServerConfig> named();

        List<String> hosts();

        String proxy();

        Optional<ServerConfig> proxySection();

        List<String> empty();
    }

    private static AppConfig config() {
        return ConfigFactory.create(AppConfig.class);
    }

    /** Nothing registers the loader: the artifact is on the class path, so the format is on. */
    @Test
    public void aJsonSourceIsReadWithNothingRegistered() {
        assertEquals("owner", config().name());
    }

    @Test
    public void anObjectIsASection() {
        assertEquals("localhost", config().server().host());
        assertEquals(9090, config().server().port());
    }

    /** The shape most JSON documents have, and the one nothing could read until today. */
    @Test
    public void anArrayOfObjectsIsAListOfSections() {
        List<ServerConfig> servers = config().servers();

        assertEquals(2, servers.size());
        assertEquals("alpha", servers.get(0).host());
        assertEquals(1, servers.get(0).port());
        assertEquals("beta", servers.get(1).host());
        assertEquals("the default of the interface fills what the document left out", 8080,
                servers.get(1).port());
    }

    @Test
    public void anObjectOfObjectsIsAMapOfSections() {
        Map<String, ServerConfig> named = config().named();

        assertEquals(2, named.size());
        assertEquals("first", named.get("one").host());
        assertEquals("second", named.get("two").host());
    }

    @Test
    public void anArrayOfValuesIsAList() {
        assertEquals(java.util.Arrays.asList("a", "b"), config().hosts());
    }

    @Test
    public void aNullIsNotThereAndAnEmptyArrayIsAnEmptyList() {
        assertNull("a null writes no key at all", config().proxy());
        assertTrue("while an empty array writes an empty value, which is an empty collection",
                config().empty().isEmpty());
    }

    /**
     * The consequence of that, met head on rather than discovered later. The document says
     * <code>"proxySection": null</code> — nothing is written — and the section is <b>present</b> all the
     * same, because <code>ServerConfig</code> defaults its port and a default is a property like any other.
     * An Optional section and a default inside it say the opposite of each other, and the default wins.
     */
    @Test
    public void anOptionalSectionIsStillPresentIfItsInterfaceHasADefault() {
        assertTrue(config().proxySection().isPresent());
        assertNull("nothing was written, so nothing without a default is there",
                config().proxySection().get().host());
        assertEquals(8080, config().proxySection().get().port());
    }
}
