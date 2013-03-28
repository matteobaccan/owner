/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.DisableFeature;
import org.junit.Test;

import static org.aeonbits.owner.Config.DisableableFeature.VARIABLE_EXPANSION;
import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class DisableFeatureTest {
    public static interface ConfigWithSubstitutionDisabledOnMethod extends Config {
        @DefaultValue("Earth")
        String world();

        @DisableFeature(VARIABLE_EXPANSION)
        @DefaultValue("Hello ${world}.")
        String sayHelloDisabled();

        @DefaultValue("Hello ${world}.")
        String sayHelloEnabled();
    }

    @Test
    public void shouldNotExpandWorldWhenDisabledOnMethodLevel() {
        ConfigWithSubstitutionDisabledOnMethod cfg = ConfigFactory.create(ConfigWithSubstitutionDisabledOnMethod.class);
        assertEquals("Hello ${world}.", cfg.sayHelloDisabled());
        assertEquals("Hello Earth.", cfg.sayHelloEnabled());
    }

    @DisableFeature(VARIABLE_EXPANSION)
    public static interface ConfigWithSubstitutionDisabledOnClass extends Config {
        @DefaultValue("Earth")
        String world();

        @DefaultValue("Hello ${world}.")
        String sayHelloDisabled();

        @DefaultValue("Hello ${world}.")
        String sayHelloEnabled();
    }

    @Test
    public void shouldNotExpandWorldWhenDisabledOnClassLevel() {
        ConfigWithSubstitutionDisabledOnClass cfg = ConfigFactory.create(ConfigWithSubstitutionDisabledOnClass.class);
        assertEquals("Hello ${world}.", cfg.sayHelloDisabled());
        assertEquals("Hello ${world}.", cfg.sayHelloEnabled());
    }

}
