/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Prefix;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * See: https://github.com/lviggiano/owner/issues/191
 * <p>
 * A value that cannot be converted throws, and the {@link Config.DefaultValue} is deliberately not used in
 * its place: falling back on a failed conversion would turn a typo like <code>port=8O80</code>, written with
 * the letter O, into a silent default. What the message was missing is <em>which</em> property is at fault,
 * which is what this test pins down: with fifty properties in a file, "Cannot convert '' to int" alone leaves
 * the search to be done by hand.
 * <p>
 * The key named is the one the property is read with, so it accounts for {@link Config.Key} and
 * {@link Prefix} rather than being the name of the method.
 *
 * @author Matteo Baccan
 */
public class Issue191Test {

    public interface ServerConfig extends Config {
        @Key("server.port")
        @DefaultValue("8080")
        int port();

        @Key("server.ports")
        @DefaultValue("8080")
        int[] ports();

        @Key("server.name")
        @DefaultValue("localhost")
        String name();

        @Key("server.protocol")
        Protocol protocol();
    }

    /** No converter can build one of these, so the conversion fails on the target type rather than on the value. */
    public interface Protocol {
    }

    @Prefix("server.")
    public interface PrefixedConfig extends Config {
        @DefaultValue("8080")
        int port();
    }

    private static Map<String, String> given(String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    private static String messageOf(Runnable read) {
        try {
            read.run();
            fail("an UnsupportedOperationException was expected");
            return null;
        } catch (UnsupportedOperationException e) {
            return e.getMessage();
        }
    }

    @Test
    public void theFailingPropertyIsNamedInTheMessage() {
        ServerConfig cfg = ConfigFactory.create(ServerConfig.class, given("server.port", "abc"));

        assertEquals("Cannot convert 'abc' to int for property 'server.port'", messageOf(cfg::port));
    }

    /**
     * The case reported in the issue: the property is there but the value is empty. It still throws, and the
     * message now says which line to go and fill in.
     */
    @Test
    public void anEmptyValueNamesThePropertyToo() {
        ServerConfig cfg = ConfigFactory.create(ServerConfig.class, given("server.port", ""));

        assertEquals("Cannot convert '' to int for property 'server.port'", messageOf(cfg::port));
    }

    /** An element of a list fails on the element, but the property to go and fix is still the whole one. */
    @Test
    public void anElementOfAnArrayNamesTheWholeProperty() {
        ServerConfig cfg = ConfigFactory.create(ServerConfig.class, given("server.ports", "80, 8O80, 8081"));

        assertEquals("Cannot convert '8O80' to int for property 'server.ports'", messageOf(cfg::ports));
    }

    /** A type nothing can convert to reports the same way, from the last converter of the chain. */
    @Test
    public void anUnsupportedTargetTypeNamesThePropertyAsWell() {
        ServerConfig cfg = ConfigFactory.create(ServerConfig.class, given("server.protocol", "http"));

        assertEquals(
                "Cannot convert 'http' to org.aeonbits.owner.issues.Issue191Test.Protocol"
                        + " for property 'server.protocol'",
                messageOf(cfg::protocol));
    }

    /** The name is the key the property is read with, prefix included, not the name of the method. */
    @Test
    public void theKeyNamedIsThePrefixedOne() {
        PrefixedConfig cfg = ConfigFactory.create(PrefixedConfig.class, given("server.port", "abc"));

        assertEquals("Cannot convert 'abc' to int for property 'server.port'", messageOf(cfg::port));
    }

    /**
     * What the reporter asked for and does not happen: the {@link Config.DefaultValue} is not used when the
     * conversion of the configured value fails. Pinned down here so that changing it stays a deliberate act.
     */
    @Test
    public void theDefaultValueIsNotUsedWhenTheConversionFails() {
        ServerConfig cfg = ConfigFactory.create(ServerConfig.class, given("server.port", ""));

        try {
            cfg.port();
            fail("the default value was used in place of the empty one");
        } catch (UnsupportedOperationException expected) {
            // the empty value is not silently replaced by 8080
        }
    }

    /**
     * The types where the empty value is already meaningful are unaffected: a String stays empty and a
     * collection stays empty, which is the same distinction MicroProfile Config draws between a value that
     * is empty and one that cannot be converted.
     */
    @Test
    public void theTypesThatAcceptAnEmptyValueKeepAccepting() {
        Map<String, String> values = given("server.name", "");
        values.put("server.ports", "");

        ServerConfig cfg = ConfigFactory.create(ServerConfig.class, values);

        assertEquals("", cfg.name());
        assertEquals(0, cfg.ports().length);
    }
}
