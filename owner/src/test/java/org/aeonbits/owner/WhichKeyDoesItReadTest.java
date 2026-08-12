/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.Prefix;
import org.aeonbits.owner.util.LogCapture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
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

    private LogCapture capture;

    @Before
    public void listen() {
        capture = LogCapture.ofLibrary(Level.FINE);
    }

    @After
    public void stopListening() {
        capture.close();
    }

    private String said(Level level) {
        return capture.messagesAt(level);
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
    // a value used as a format that is not one
    // -------------------------------------------------------------------------------------------------

    public interface GreetingConfig extends Config {
        String greeting(String name);

        String password(String ignored);
    }

    private static GreetingConfig greetings() {
        Properties props = new Properties();
        props.setProperty("greeting", "hello %q");
        props.setProperty("password", "secret%q");
        return ConfigFactory.create(GreetingConfig.class, props);
    }

    /**
     * Returning the value as written is the documented answer and stays: a method taking arguments makes
     * its value a template, and a value has no obligation to be one. What was missing is a way to find out,
     * for the case where it <b>was</b> meant as a format and a placeholder is mistyped.
     */
    @Test
    public void aValueThatIsNotAFormatComesBackAsWrittenAndSaysSoAtFine() {
        assertEquals("hello %q", greetings().greeting("world"));

        String fine = said(Level.FINE);
        assertTrue(fine, fine.contains("greeting() takes arguments"));
        assertTrue(fine, fine.contains("'greeting'"));
        assertTrue("the kind of failure is named: " + fine,
                fine.contains("UnknownFormatConversionException"));
    }

    /**
     * The rule that outranks the diagnostic: a value never reaches a log. The message of a formatting
     * failure quotes the piece of the format it choked on, which is a piece of the value, so neither it nor
     * the value itself is in the line.
     */
    @Test
    public void neitherTheValueNorTheReasonQuotingItIsLogged() {
        greetings().password("x");

        String fine = said(Level.FINE);
        assertTrue(fine, fine.contains("password() takes arguments"));
        assertFalse("the value was logged: " + fine, fine.contains("secret"));
        assertFalse("the message, which quotes the piece it choked on, was logged: " + fine,
                fine.contains("= 'q'"));
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
