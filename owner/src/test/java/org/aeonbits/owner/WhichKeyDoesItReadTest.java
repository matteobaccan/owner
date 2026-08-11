/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.DisableFeature;
import org.aeonbits.owner.Config.Key;
import org.aeonbits.owner.Config.Prefix;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.aeonbits.owner.Config.DisableableFeature.PREFIX;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The configuration saying which key each of its methods reads.
 * <p>
 * A wrong prefix is the most disorienting failure there is and the least visible: every property vanishes at
 * once, nothing errors, and the file is full of values that look right. The errors this library raises
 * already name the whole key, so what these two lines cover is the case where nothing goes wrong and
 * nothing is found.
 * </p>
 *
 * @author Matteo Baccan
 */
public class WhichKeyDoesItReadTest {

    private final List<LogRecord> records = new ArrayList<>();
    private final Logger logger = Logger.getLogger("org.aeonbits.owner");
    private Handler collector;
    private Level before;

    @Before
    public void listen() {
        before = logger.getLevel();
        logger.setLevel(Level.FINE);
        collector = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };
        collector.setLevel(Level.FINE);
        logger.addHandler(collector);
    }

    @After
    public void stopListening() {
        logger.removeHandler(collector);
        logger.setLevel(before);
    }

    private String said(Level level) {
        StringBuilder text = new StringBuilder();
        for (LogRecord record : records)
            if (record.getLevel().equals(level))
                text.append(record.getMessage()).append('\n');
        return text.toString();
    }

    @Prefix("server.")
    public interface ServerConfig extends Config {
        String host();

        @Key("max.threads")
        int maxThreads();

        @DisableFeature(PREFIX)
        String version();

        @Key("pool.%s")
        int pool(String name);

        @Key("servers.${env}.url")
        String url();

        SectionConfig section();
    }

    public interface SectionConfig extends Config {
        String name();
    }

    @Test
    public void everyMethodSaysWhichKeyItReads() {
        ConfigFactory.create(ServerConfig.class);
        String fine = said(Level.FINE);

        assertTrue(fine, fine.contains("ServerConfig.host() reads 'server.host'"));
        assertTrue(fine, fine.contains("ServerConfig.maxThreads() reads 'server.max.threads'"));
    }

    /** The three that would look like mistakes if the line did not say what they are. */
    @Test
    public void aKeyThatIsNotYetWhatItWillBeSaysSo() {
        ConfigFactory.create(ServerConfig.class);
        String fine = said(Level.FINE);

        assertTrue(fine, fine.contains("version() reads 'version', with no prefix at all"));
        assertTrue(fine, fine.contains("pool() reads 'server.pool.%s', its arguments formatted in"));
        assertTrue(fine, fine.contains("url() reads 'server.servers.${env}.url', before the variables"));
    }

    /** A section resolves to a path rather than to a property, and its own methods are walked too. */
    @Test
    public void aSectionSaysWhatItIsAndItsMethodsAreWalked() {
        ConfigFactory.create(ServerConfig.class);
        String fine = said(Level.FINE);

        assertTrue(fine, fine.contains("section() is the section under 'server.section.'"));
        assertTrue(fine, fine.contains("SectionConfig.name() reads 'server.section.name'"));
    }

    public interface PlainConfig extends Config, Accessible, Mutable, Reloadable, Traceable {
        String host();
    }

    @Test
    public void theMethodsOfTheLibraryAreNotKeys() {
        ConfigFactory.create(PlainConfig.class);
        String fine = said(Level.FINE);

        assertTrue(fine, fine.contains("PlainConfig.host() reads 'host'"));
        assertFalse("getProperty is answered by the library, not by a key: " + fine,
                fine.contains("getProperty()"));
        assertFalse(fine, fine.contains("reload()"));
        assertFalse(fine, fine.contains("originOf()"));
    }

    // -------------------------------------------------------------------------------------------------
    // the prefix nobody can read in the source
    // -------------------------------------------------------------------------------------------------

    @Test
    public void aPrefixConfiguredOnTheFactoryIsSaidOutLoud() {
        Factory factory = ConfigFactory.newInstance();
        factory.setProperty(KeyPrefix.LITERAL, "myapp.");

        factory.create(PlainConfig.class, new Properties());

        String config = said(Level.CONFIG);
        assertTrue(config, config.contains("every key is prefixed with 'myapp.', from owner.key.prefix"));
    }

    @Test
    public void theOneDerivedFromThePackageSaysThatItIsDerived() {
        Factory factory = ConfigFactory.newInstance();
        factory.setProperty(KeyPrefix.FROM_PACKAGE, "true");

        factory.create(PlainConfig.class, new Properties());

        String config = said(Level.CONFIG);
        assertTrue(config, config.contains("the package of the interface declaring each method"));
    }

    @Test
    public void aFactoryWithNoPrefixHasNothingToSay() {
        ConfigFactory.create(PlainConfig.class);

        assertFalse(said(Level.CONFIG), said(Level.CONFIG).contains("every key is prefixed"));
    }
}
