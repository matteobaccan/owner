/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.LoadType;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.loaders.DotEnvLoader;
import org.aeonbits.owner.loaders.EnvDialect;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.aeonbits.owner.TestConstants.RESOURCES_DIR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A <code>.env</code> file read the way a user reads one: through {@code @Sources} and a configuration
 * interface, rather than by calling the loader directly.
 *
 * @author Matteo Baccan
 */
public class DotEnvConfigTest {

    private static final String DIR = RESOURCES_DIR + "/dotenv";
    private static final String PLAIN = DIR + "/plain.env";
    private static final String QUOTED = DIR + "/quoted.env";

    @Sources("file:" + PLAIN)
    interface ServerConfig extends Config {
        String host();

        int port();

        @DefaultValue("false")
        boolean debug();

        Duration timeout();

        List<String> tags();

        @DefaultValue("not in the file")
        String missing();
    }

    @Sources("file:" + QUOTED)
    interface QuotedConfig extends Config {
        String name();
    }

    @Sources("file:" + QUOTED + "?dialect=dotenv")
    interface QuotedDotenvConfig extends Config {
        String name();
    }

    @Sources("file:" + QUOTED + "?quotes=strip")
    interface QuotedOneRuleConfig extends Config {
        String name();
    }

    /** A <code>.env</code> beside a properties file: both are read, and the first named source wins. */
    @LoadPolicy(LoadType.MERGE)
    @Sources({"file:" + PLAIN, "file:" + DIR + "/fallback.properties"})
    interface MergedConfig extends Config {
        String host();

        String extra();
    }

    @Before
    public void before() throws IOException {
        // reading a quoted value under the default dialect is meant to be warned about; it is the subject of
        // DotEnvLoaderTest, and here it would only be noise on the build output
        Logger.getLogger(DotEnvLoader.class.getName()).setLevel(Level.OFF);
        new File(DIR).mkdirs();
        write(PLAIN,
                "# the server this instance talks to",
                "host=example.org",
                "port=8080",
                "debug=true",
                "timeout=30 s",
                "tags=alpha,beta");
        write(QUOTED, "name=\"Matteo\"");
        write(DIR + "/fallback.properties", "host=overridden", "extra=from the properties file");
    }

    @After
    public void after() {
        Logger.getLogger(DotEnvLoader.class.getName()).setLevel(null);
        new File(PLAIN).delete();
        new File(QUOTED).delete();
        new File(DIR + "/fallback.properties").delete();
    }

    private static void write(String path, String... lines) throws IOException {
        Writer writer = new OutputStreamWriter(Files.newOutputStream(new File(path).toPath()), "UTF-8");
        try {
            for (String line : lines) {
                writer.write(line);
                writer.write("\n");
            }
        } finally {
            writer.close();
        }
    }

    @Test
    public void testTheDotEnvLoaderIsTheOneChosenForTheSource() {
        LoadersManagerForTest loaders = new LoadersManagerForTest();
        assertTrue(loaders.findLoader(URI.create("file:" + PLAIN)) instanceof DotEnvLoader);
    }

    /** The properties loader accepts every URL, so the order the two are registered in is what decides. */
    @Test
    public void testThePropertiesLoaderStillAnswersForItsOwnFiles() {
        LoadersManagerForTest loaders = new LoadersManagerForTest();
        assertTrue(loaders.findLoader(URI.create("file:" + DIR + "/fallback.properties"))
                .getClass().getName().endsWith("PropertiesLoader"));
    }

    @Test
    public void testValuesAreReadAndConverted() {
        ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
        assertEquals("example.org", cfg.host());
        assertEquals(8080, cfg.port());
        assertTrue(cfg.debug());
        assertEquals(Duration.ofSeconds(30), cfg.timeout());
        assertEquals(2, cfg.tags().size());
        assertEquals("alpha", cfg.tags().get(0));
    }

    @Test
    public void testADefaultValueStillAppliesToWhatTheFileDoesNotSay() {
        assertEquals("not in the file", ConfigFactory.create(ServerConfig.class).missing());
    }

    @Test
    public void testTheDefaultDialectKeepsTheQuotes() {
        assertEquals("\"Matteo\"", ConfigFactory.create(QuotedConfig.class).name());
    }

    @Test
    public void testTheSourceCanAskForAnotherDialect() {
        assertEquals("Matteo", ConfigFactory.create(QuotedDotenvConfig.class).name());
    }

    @Test
    public void testTheSourceCanAdjustASingleRule() {
        assertEquals("Matteo", ConfigFactory.create(QuotedOneRuleConfig.class).name());
    }

    @Test
    public void testAFactoryCanBeGivenADifferentDefaultDialect() {
        Factory factory = ConfigFactory.newInstance();
        factory.registerLoader(new DotEnvLoader(EnvDialect.DOTENV));
        assertEquals("Matteo", factory.create(QuotedConfig.class).name());
    }

    /** Registering one on a factory must leave every other factory as it was. */
    @Test
    public void testTheDefaultDialectOfOtherFactoriesIsUntouched() {
        Factory factory = ConfigFactory.newInstance();
        factory.registerLoader(new DotEnvLoader(EnvDialect.DOTENV));
        factory.create(QuotedConfig.class);
        assertEquals("\"Matteo\"", ConfigFactory.create(QuotedConfig.class).name());
    }

    @Test
    public void testADotEnvMergesWithTheOtherFormats() {
        MergedConfig cfg = ConfigFactory.create(MergedConfig.class);
        assertEquals("example.org", cfg.host());
        assertEquals("from the properties file", cfg.extra());
    }

    /** No {@code @Sources} means the usual lookups, and the new loader must not have added one of its own. */
    @Test
    public void testAnInterfaceWithNoSourcesIsUnaffected() {
        assertNull(ConfigFactory.create(NoSources.class).anything());
    }

    interface NoSources extends Config {
        String anything();
    }
}
