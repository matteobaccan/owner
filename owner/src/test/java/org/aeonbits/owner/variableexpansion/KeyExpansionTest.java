/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.variableexpansion;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.DisableFeature;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import static org.aeonbits.owner.Config.DisableableFeature.VARIABLE_EXPANSION;
import static org.aeonbits.owner.util.Collections.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Luigi R. Viggiano
 */
public class KeyExpansionTest {


    @Sources("classpath:org/aeonbits/owner/variableexpansion/KeyExpansionExample.xml")
    public interface MyConfig extends Config {
        @Key("servers.${env}.name")
        String name();

        @Key("servers.${env}.hostname")
        String hostname();

        @Key("servers.${env}.port")
        Integer port();

        @Key("servers.${env}.user")
        String user();

        @DisableFeature(VARIABLE_EXPANSION)
        @Key("servers.${env}.password")
        String password();
    }

    @Test
    public void testKeyExpansion() {
        MyConfig cfg = ConfigFactory.create(MyConfig.class, map("env", "dev"));

        assertEquals("DEV", cfg.name());
        assertEquals("devhost", cfg.hostname());
        assertEquals(new Integer(6000), cfg.port());
        assertEquals("myuser1", cfg.user());
        assertNull(cfg.password()); // expansion is disabled on method level
    }

    @DisableFeature(VARIABLE_EXPANSION)
    @Sources("classpath:org/aeonbits/owner/variableexpansion/KeyExpansionExample.xml")
    public interface MyConfigWithExpansionDisabled extends Config {
        @Key("servers.${env}.name")
        String name();

        @Key("servers.${env}.hostname")
        String hostname();

        @Key("servers.${env}.port")
        Integer port();

        @Key("servers.${env}.user")
        String user();

        @Key("servers.${env}.password")
        String password();
    }

    @Test
    public void testKeyExpansionDisabled() {
        MyConfigWithExpansionDisabled cfg =
                ConfigFactory.create(MyConfigWithExpansionDisabled.class, map("env", "dev"));

        assertNull(cfg.name());
        assertNull(cfg.hostname());
        assertNull(cfg.port());
        assertNull(cfg.user());
        assertNull(cfg.password());
    }

    @Sources("classpath:org/aeonbits/owner/variableexpansion/KeyExpansionExample.xml")
    public interface ExpandsFromAnotherKey extends Config {

        @DefaultValue("dev")
        String env();

        @Key("servers.${env}.name")
        String name();

        @Key("servers.${env}.hostname")
        String hostname();

        @Key("servers.${env}.port")
        Integer port();

        @Key("servers.${env}.user")
        String user();

        @DisableFeature(VARIABLE_EXPANSION)
        @Key("servers.${env}.password")
        String password();
    }

    @Test
    public void testKeyExpansionFromAnotherKey() {
        ExpandsFromAnotherKey cfg = ConfigFactory.create(ExpandsFromAnotherKey.class);

        assertEquals("DEV", cfg.name());
        assertEquals("devhost", cfg.hostname());
        assertEquals(new Integer(6000), cfg.port());
        assertEquals("myuser1", cfg.user());
        assertNull(cfg.password()); // expansion is disabled on method level
    }

    @Test
    public void testKeyExpansionFromAnotherKeyWithImportOverriding() {
        ExpandsFromAnotherKey cfg = ConfigFactory.create(ExpandsFromAnotherKey.class, map("env", "uat"));

        assertEquals("UAT", cfg.name());
        assertEquals("uathost", cfg.hostname());
        assertEquals(new Integer(60020), cfg.port());
        assertEquals("myuser2", cfg.user());
        assertNull("mypass2", cfg.password()); // expansion is disabled on method level
    }

    @Sources("classpath:org/aeonbits/owner/variableexpansion/KeyExpansionExample.xml")
    public interface UseOfDefaultValueIfNotFound extends Config {

        @DefaultValue("dev")
        String env();

        @Config.Key("servers.${env}.nonDefinedInSourceKey")
        @Config.DefaultValue("wantedValue")
        String undefinedPropInSource();

    }

    @Test
    public void testKeyExpansionAndDefaultValue() {
        UseOfDefaultValueIfNotFound cfg = ConfigFactory.create(UseOfDefaultValueIfNotFound.class);

        assertEquals("dev", cfg.env());
        assertEquals("wantedValue", cfg.undefinedPropInSource());
    }
}
