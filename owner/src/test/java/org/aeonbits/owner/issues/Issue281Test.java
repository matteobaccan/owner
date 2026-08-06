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

import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * See: https://github.com/lviggiano/owner/issues/281
 * <p>
 * The same configuration interface has to run in two environments, one of which prefixes every variable with
 * something like <code>FOO_</code>. There are two ways to say it, and they answer two different needs: one
 * prefix switched for the whole interface, or one method reading two keys in a defined order.
 */
public class Issue281Test {

    // -- one prefix, switched for the whole interface -------------------------------------------------

    @Prefix("${env.prefix:}")
    interface SwitchablePrefixConfig extends Config {
        String host();

        int port();
    }

    @Test
    public void withoutTheVariableTheKeysAreBare() {
        SwitchablePrefixConfig cfg = ConfigFactory.create(SwitchablePrefixConfig.class, new Properties() {{
            setProperty("host", "plainhost");
            setProperty("port", "80");
        }});

        assertEquals("plainhost", cfg.host());
        assertEquals(80, cfg.port());
    }

    @Test
    public void withTheVariableEveryKeyMoves() {
        SwitchablePrefixConfig cfg = ConfigFactory.create(SwitchablePrefixConfig.class, new Properties() {{
            setProperty("env.prefix", "FOO_");
            setProperty("FOO_host", "prefixedhost");
            setProperty("FOO_port", "8080");
            setProperty("host", "must not be read");
        }});

        assertEquals("prefixedhost", cfg.host());
        assertEquals(8080, cfg.port());
    }

    // -- one method, two keys -------------------------------------------------------------------------

    interface TwoKeysConfig extends Config {
        @Key("host")
        @DefaultValue("${FOO_host}")
        String host();
    }

    @Test
    public void thePrimaryKeyWins() {
        assertEquals("plainhost", ConfigFactory.create(TwoKeysConfig.class, new Properties() {{
            setProperty("host", "plainhost");
            setProperty("FOO_host", "prefixedhost");
        }}).host());
    }

    @Test
    public void theSecondKeyIsReadWhenTheFirstIsMissing() {
        assertEquals("prefixedhost", ConfigFactory.create(TwoKeysConfig.class, new Properties() {{
            setProperty("FOO_host", "prefixedhost");
        }}).host());
    }

    /**
     * With neither key defined the unresolved variable yields the empty string rather than <code>null</code>,
     * because a {@code @DefaultValue} is present. A last resort is worth adding for that reason.
     */
    @Test
    public void neitherKeyGivesTheEmptyString() {
        assertEquals("", ConfigFactory.create(TwoKeysConfig.class).host());
    }

    interface TwoKeysWithLastResortConfig extends Config {
        @Key("host")
        @DefaultValue("${FOO_host:localhost}")
        String host();
    }

    @Test
    public void aLastResortCanBeGivenToTheSecondKey() {
        assertEquals("localhost", ConfigFactory.create(TwoKeysWithLastResortConfig.class).host());

        assertEquals("prefixedhost", ConfigFactory.create(TwoKeysWithLastResortConfig.class, new Properties() {{
            setProperty("FOO_host", "prefixedhost");
        }}).host());
    }
}
