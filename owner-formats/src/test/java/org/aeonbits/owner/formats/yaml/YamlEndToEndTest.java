/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.formats.yaml;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A YAML file of the shape people actually write — a Compose file, a Kubernetes manifest, an application
 * configuration — read through a mapping interface with nothing registered.
 *
 * @author Matteo Baccan
 */
public class YamlEndToEndTest {

    private static final String PATH = "target/test-generated-resources/YamlEndToEnd.yaml";

    @BeforeClass
    public static void writeTheDocument() throws IOException {
        File file = new File(PATH);
        Files.createDirectories(Paths.get(file.getParent()));
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            out.write(("# the application\n"
                    + "name: owner\n"
                    + "server:\n"
                    + "  host: localhost\n"
                    + "  port: 9090\n"
                    + "servers:\n"
                    + "  - host: alpha\n"
                    + "    port: 1\n"
                    + "  - host: beta\n"
                    + "named:\n"
                    + "  one:\n"
                    + "    host: first\n"
                    + "  two:\n"
                    + "    host: second\n"
                    + "ports: [80, 443]\n"
                    + "proxy:\n"
                    + "banner: |\n"
                    + "  welcome\n"
                    + "  to owner\n").getBytes(UTF_8));
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

        List<Integer> ports();

        String proxy();

        String banner();
    }

    private static AppConfig config() {
        return ConfigFactory.create(AppConfig.class);
    }

    /** Nothing registers the loader: the artifact is on the class path, so the format is on. */
    @Test
    public void aYamlSourceIsReadWithNothingRegistered() {
        assertEquals("owner", config().name());
    }

    @Test
    public void aBlockIsASection() {
        assertEquals("localhost", config().server().host());
        assertEquals(9090, config().server().port());
    }

    @Test
    public void aSequenceOfMappingsIsAListOfSections() {
        List<ServerConfig> servers = config().servers();

        assertEquals(2, servers.size());
        assertEquals("alpha", servers.get(0).host());
        assertEquals(1, servers.get(0).port());
        assertEquals("beta", servers.get(1).host());
        assertEquals("the default of the interface fills what the document left out", 8080,
                servers.get(1).port());
    }

    @Test
    public void aBlockOfBlocksIsAMapOfSections() {
        Map<String, ServerConfig> named = config().named();

        assertEquals(2, named.size());
        assertEquals("first", named.get("one").host());
        assertEquals("second", named.get("two").host());
    }

    @Test
    public void aFlowSequenceIsAListAndItsElementsAreConverted() {
        assertEquals(Arrays.asList(80, 443), config().ports());
    }

    @Test
    public void aNameWithNothingAfterItIsNotThere() {
        assertNull(config().proxy());
    }

    @Test
    public void aBlockScalarArrivesWithItsLineBreaks() {
        assertEquals("welcome\nto owner\n", config().banner());
    }

    /** Both names the format goes by reach the same loader. */
    @Test
    public void theOtherExtensionWorksToo() throws IOException {
        File file = new File("target/test-generated-resources/YamlEndToEnd.yml");
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            out.write("name: owner\n".getBytes(UTF_8));
        }
        assertTrue(ConfigFactory.create(ShortSuffixConfig.class).name().equals("owner"));
    }

    @Sources("file:target/test-generated-resources/YamlEndToEnd.yml")
    public interface ShortSuffixConfig extends Config {
        String name();
    }
}
