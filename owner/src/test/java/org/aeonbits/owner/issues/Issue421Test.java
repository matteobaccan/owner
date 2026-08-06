/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Accessible;
import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * See: https://github.com/lviggiano/owner/issues/421
 * <p>
 * Overriding a method in a sub-interface to give it a different {@code @Key} redirects the property: the method
 * reads the new key, and the key named by the overridden declaration is no longer part of the configuration.
 * That follows from Java rather than from OWNER — {@link Class#getMethods()} returns the overriding declaration
 * only, so there is one method and therefore one key.
 * <p>
 * The overlay the reporter was after is expressible, and with better semantics than registering the orphaned
 * default would give: see {@link OverlayConfig}.
 */
public class Issue421Test {

    interface BaseConfig extends Config, Accessible {
        @Key("feature.default.setting")
        @DefaultValue("-1")
        long getSetting();

        @Key("feature.untouched")
        @DefaultValue("base")
        String untouched();
    }

    interface ConcreteFeatureConfig extends BaseConfig {
        @Key("feature.concrete.setting")
        @DefaultValue("42")
        @Override
        long getSetting();
    }

    @Test
    public void anOverriddenKeyIsRedirectedRatherThanAdded() {
        ConcreteFeatureConfig cfg = ConfigFactory.create(ConcreteFeatureConfig.class);

        assertEquals(42L, cfg.getSetting());
        assertEquals("42", cfg.getProperty("feature.concrete.setting"));

        // the overridden declaration no longer names a property of this configuration
        assertNull(cfg.getProperty("feature.default.setting"));

        // ...while a method that is not overridden keeps its key, at any depth
        assertEquals("base", cfg.getProperty("feature.untouched"));
    }

    /** With the same key on both sides, the overriding declaration provides the default value. */
    interface SameKeyConfig extends BaseConfig {
        @Key("feature.default.setting")
        @DefaultValue("99")
        @Override
        long getSetting();
    }

    @Test
    public void theOverridingDeclarationProvidesTheDefault() {
        assertEquals(99L, ConfigFactory.create(SameKeyConfig.class).getSetting());
    }

    /**
     * The overlay, written down: the concrete key falls back to the base key, which falls back to the literal
     * default. Note that this is a chain of three, and that the fallback works on the <em>properties</em> and
     * not only on the default values.
     */
    interface OverlayConfig extends BaseConfig {
        @Key("feature.concrete.setting")
        @DefaultValue("${feature.default.setting:-1}")
        @Override
        long getSetting();
    }

    @Test
    public void anOverlayIsExpressedWithAVariable() {
        assertEquals(-1L, ConfigFactory.create(OverlayConfig.class).getSetting());

        assertEquals(7L, ConfigFactory.create(OverlayConfig.class, new Properties() {{
            setProperty("feature.default.setting", "7");
        }}).getSetting());

        assertEquals(42L, ConfigFactory.create(OverlayConfig.class, new Properties() {{
            setProperty("feature.default.setting", "7");
            setProperty("feature.concrete.setting", "42");
        }}).getSetting());
    }

    /** Keeping the base key readable is a matter of declaring an accessor for it, rather than overriding. */
    interface BothKeysConfig extends BaseConfig {
        @Key("feature.concrete.setting")
        @DefaultValue("42")
        @Override
        long getSetting();

        @Key("feature.default.setting")
        @DefaultValue("-1")
        long getBaseSetting();
    }

    @Test
    public void bothKeysStayReadableWhenBothAreDeclared() {
        BothKeysConfig cfg = ConfigFactory.create(BothKeysConfig.class);

        assertEquals(42L, cfg.getSetting());
        assertEquals(-1L, cfg.getBaseSetting());
        assertEquals("42", cfg.getProperty("feature.concrete.setting"));
        assertEquals("-1", cfg.getProperty("feature.default.setting"));
    }
}
