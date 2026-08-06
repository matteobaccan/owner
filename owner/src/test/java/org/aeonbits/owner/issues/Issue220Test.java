/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * See: https://github.com/lviggiano/owner/issues/220
 * <p>
 * The question was whether a key can carry a parameter, and whether parameters and variables can be mixed in
 * one. The answer is yes to the first — it has worked for years, and koranke said so in the thread — and no to
 * the second. The properties file discussed there also happens to be readable as a map, which is what Luigi
 * sketched in 2019 and what Gmugra asked for in 2021.
 */
public class Issue220Test {

    /** The properties file written in the thread. */
    private static Properties values() {
        return new Properties() {{
            setProperty("foo.1", "a");
            setProperty("foo.2", "b");
            setProperty("foo.3", "c");
            setProperty("salutation.world", "hello");
            setProperty("salutation.moonman", "goodbye");
        }};
    }

    public interface ParametrizedConfig extends Config {
        @Key("foo.%s")
        String foo(int index);

        @Key("salutation.%s")
        String salutation(String who);
    }

    @Test
    public void aParameterChoosesTheKey() {
        ParametrizedConfig cfg = ConfigFactory.create(ParametrizedConfig.class, values());

        assertEquals("a", cfg.foo(1));
        assertEquals("c", cfg.foo(3));
        assertEquals("hello", cfg.salutation("world"));
        assertEquals("goodbye", cfg.salutation("moonman"));
    }

    @Test
    public void anUndefinedKeyGivesNull() {
        assertNull(ConfigFactory.create(ParametrizedConfig.class, values()).salutation("nobody"));
    }

    /** The same two groups, read whole rather than one entry at a time. */
    public interface AsMapsConfig extends Config {
        Map<Integer, String> foo();

        Map<String, String> salutation();
    }

    @Test
    public void theSameGroupsAreAlsoReadableAsMaps() {
        AsMapsConfig cfg = ConfigFactory.create(AsMapsConfig.class, values());

        assertEquals(3, cfg.foo().size());
        assertEquals("a", cfg.foo().get(1));
        assertEquals("goodbye", cfg.salutation().get("moonman"));
    }

    public interface MixedConfig extends Config {
        @Key("${prefix}.%s")
        String mixed(String who);
    }

    /**
     * The second question: a key holding both a variable and a parameter is expanded, not formatted, so the
     * <code>%s</code> is left where it is and the lookup fails. Pinned so that the documented rule has
     * something behind it.
     */
    @Test
    public void variablesAndParametersCannotBeMixedInOneKey() {
        MixedConfig cfg = ConfigFactory.create(MixedConfig.class, new Properties() {{
            setProperty("prefix", "salutation");
            setProperty("salutation.world", "hello");
            setProperty("salutation.%s", "the literal key");
        }});

        assertEquals("the literal key", cfg.mixed("world"));
    }
}
