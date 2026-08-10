/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.Config.Key;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.LoadType;
import org.aeonbits.owner.Config.Prefix;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.loaders.IniDialect;
import org.aeonbits.owner.loaders.IniLoader;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.aeonbits.owner.TestConstants.RESOURCES_DIR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * An INI file read the way somebody reads one: through {@code @Sources} and a configuration interface,
 * rather than by calling the loader.
 * <p>
 * {@code IniLoaderTest} covers the parsing rule by rule. What this covers is the chain — the options on the
 * source, the loader, the indexed keys a repeated key turns into, the type conversion — which no test of any
 * one part can show, and which is where the two files that motivated INI actually live.
 * </p>
 *
 * @author Matteo Baccan
 */
public class IniConfigTest {

    private static final String DIR = RESOURCES_DIR + "/ini";
    private static final String APP = DIR + "/app.ini";
    private static final String GITCONFIG = DIR + "/gitconfig.ini";
    private static final String CREDENTIALS = DIR + "/credentials.ini";

    @Before
    public void before() throws IOException {
        new File(DIR).mkdirs();
        write(APP,
                "# the application itself",
                "name = invoicing",
                "",
                "[server]",
                "host = example.org",
                "port = 8080",
                "debug = true",
                "timeout = 30 s",
                "",
                "[servers]",
                "host = alpha",
                "host = beta",
                "host = gamma",
                "",
                "[credentials]",
                "user = app",
                "password = s3cr3t");
        // the shape of a real ~/.gitconfig, subsections and all
        write(GITCONFIG,
                "[user]",
                "\tname = Matteo Baccan",
                "\temail = matteo.baccan@gmail.com",
                "[remote \"origin\"]",
                "\turl = git@github.com:matteobaccan/owner.git",
                "[core]",
                "\tbare");
        // the shape of ~/.aws/credentials
        write(CREDENTIALS,
                "[default]",
                "aws_access_key_id = AKIAIOSFODNN7EXAMPLE",
                "[dev]",
                "aws_access_key_id = AKIAI44QH8DHBEXAMPLE");
        write(DIR + "/fallback.properties",
                "server.host=overridden", "extra=from the properties file");
    }

    // ---------------------------------------------------------------- the ordinary reading

    @Sources("file:" + APP)
    interface AppConfig extends Config {
        String name();

        @Key("server.host")
        String host();

        @Key("server.port")
        int port();

        @Key("server.debug")
        boolean debug();

        @Key("server.timeout")
        Duration timeout();

        @DefaultValue("not in the file")
        String missing();
    }

    @Test
    public void aSectionIsJustAPrefixOnTheKeys() {
        AppConfig cfg = ConfigFactory.create(AppConfig.class);

        assertEquals("invoicing", cfg.name());
        assertEquals("example.org", cfg.host());
        assertEquals(8080, cfg.port());
        assertTrue(cfg.debug());
        assertEquals(Duration.ofSeconds(30), cfg.timeout());
        assertEquals("not in the file", cfg.missing());
    }

    /** {@code @Prefix} names the section, which is the shape somebody writes once they have several. */
    @Prefix("server.")
    @Sources("file:" + APP)
    interface ServerConfig extends Config {
        String host();

        int port();
    }

    @Test
    public void aPrefixNamesTheSection() {
        ServerConfig cfg = ConfigFactory.create(ServerConfig.class);

        assertEquals("example.org", cfg.host());
        assertEquals(8080, cfg.port());
    }

    // ---------------------------------------------------------------- a repeated key reaches a List

    @Sources("file:" + APP)
    interface RepeatedConfig extends Config {
        @Key("servers.host")
        List<String> hosts();

        @Key("servers.host")
        String[] hostsAsArray();
    }

    /**
     * The whole chain in one assertion: the loader turns three repeated keys into three indexed ones, and
     * the collection reader puts them back together. Neither half can show this on its own.
     */
    @Test
    public void aRepeatedKeyArrivesAsAList() {
        RepeatedConfig cfg = ConfigFactory.create(RepeatedConfig.class);

        assertEquals(Arrays.asList("alpha", "beta", "gamma"), cfg.hosts());
        assertEquals(3, cfg.hostsAsArray().length);
        assertEquals("gamma", cfg.hostsAsArray()[2]);
    }

    // ---------------------------------------------------------------- a section as a group

    @Sources("file:" + APP)
    interface GroupConfig extends Config {
        Map<String, String> credentials();
    }

    @Test
    public void aSectionCanBeReadAsAWholeGroup() {
        Map<String, String> group = ConfigFactory.create(GroupConfig.class).credentials();

        assertEquals(2, group.size());
        assertEquals("app", group.get("user"));
        assertEquals("s3cr3t", group.get("password"));
    }

    // ---------------------------------------------------------------- the two files that motivated INI

    @Sources("file:" + GITCONFIG + "#dialect=git")
    interface GitConfig extends Config {
        @Key("user.email")
        String email();

        @Key("remote.origin.url")
        String originUrl();

        @Key("core.bare")
        boolean bare();
    }

    /**
     * The subsection is the point of that dialect: <code>remote.origin.url</code> is the key
     * <code>git config</code> itself prints, so the interface names what the tool names.
     */
    @Test
    public void aRealGitconfigReadsUnderTheGitDialect() {
        GitConfig cfg = ConfigFactory.create(GitConfig.class);

        assertEquals("matteo.baccan@gmail.com", cfg.email());
        assertEquals("git@github.com:matteobaccan/owner.git", cfg.originUrl());
        assertTrue("a bare key means true to git", cfg.bare());
    }

    @Sources("file:" + CREDENTIALS)
    interface AwsConfig extends Config {
        @Key("default.aws_access_key_id")
        String defaultKey();

        @Key("dev.aws_access_key_id")
        String devKey();
    }

    @Test
    public void anAwsCredentialsFileNeedsNoDialectAtAll() {
        AwsConfig cfg = ConfigFactory.create(AwsConfig.class);

        assertEquals("AKIAIOSFODNN7EXAMPLE", cfg.defaultKey());
        assertEquals("AKIAI44QH8DHBEXAMPLE", cfg.devKey());
    }

    // ---------------------------------------------------------------- among the other sources

    @LoadPolicy(LoadType.MERGE)
    @Sources({"file:" + APP, "file:" + DIR + "/fallback.properties"})
    interface MergedConfig extends Config {
        @Key("server.host")
        String host();

        String extra();
    }

    @Test
    public void anIniMergesWithTheOtherFormats() {
        MergedConfig cfg = ConfigFactory.create(MergedConfig.class);

        assertEquals("the first source wins, as it does for any other pair", "example.org", cfg.host());
        assertEquals("from the properties file", cfg.extra());
    }

    @Sources("file:" + DIR + "/there-is-no-such-file.ini")
    interface AbsentConfig extends Config {
        String anything();

        @DefaultValue("still here")
        String fallback();
    }

    @Test
    public void anAbsentIniIsNotAnError() {
        AbsentConfig cfg = ConfigFactory.create(AbsentConfig.class);

        assertNull(cfg.anything());
        assertEquals("still here", cfg.fallback());
    }

    // ---------------------------------------------------------------- the dialect chosen on the factory

    @Sources("file:" + GITCONFIG)
    interface RegisteredDialectConfig extends Config {
        @Key("remote.origin.url")
        String originUrl();
    }

    @Test
    public void aDialectCanBeChosenForAWholeFactoryInstead() {
        Factory factory = ConfigFactory.newInstance();
        factory.registerLoader(new IniLoader(IniDialect.GIT));

        assertEquals("git@github.com:matteobaccan/owner.git",
                factory.create(RegisteredDialectConfig.class).originUrl());
    }

    /**
     * The same file read without the dialect it was written for does not quietly give the wrong answer: it
     * stops at the first line plain INI cannot make sense of, which is git's bare <code>[core] bare</code>.
     * That is the whole argument for refusing rather than guessing — the failure names the line, where a
     * <code>null</code> from {@code originUrl()} would have sent somebody looking at the interface.
     */
    @Test
    public void theSameFileReadWithoutItsDialectSaysSoRatherThanGuessing() {
        try {
            ConfigFactory.create(RegisteredDialectConfig.class).originUrl();
            fail("a bare key is not an assignment under the plain INI dialect");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("neither a comment nor an assignment"));
            assertTrue(e.getMessage(), e.getMessage().contains("bare"));
        }
    }

    /** And with the bare key alone forgiven, the quoted name is simply part of the section's own name. */
    @Test
    public void withoutTheGitDialectASubsectionIsNotOne() {
        assertNull(ConfigFactory.create(TolerantConfig.class).originUrl());
    }

    @Sources("file:" + GITCONFIG + "#bare=ignore")
    interface TolerantConfig extends Config {
        @Key("remote.origin.url")
        String originUrl();
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
}
