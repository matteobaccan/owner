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
 * @author luigi
 */
public class ConfigWithSubstitutionTest {

    @Test
    public void testConfigWithSubstitutionFile() {
        ConfigWithSubstitutionFile conf = ConfigFactory.create(ConfigWithSubstitutionFile.class);
        assertEquals("The quick brown fox jumped over the lazy dog", conf.story());
    }

    @Test
    public void testConfigWithSubstitutionAnnotation() {
        ConfigWithSubstitutionAnnotations conf = ConfigFactory.create(ConfigWithSubstitutionAnnotations.class);
        assertEquals("The quick brown fox jumped over the lazy dog", conf.story());
    }

    @Test
    public void testSubInterface() {
        ConfigWithSubtstitutionAnnotationsSubInterface conf = ConfigFactory.create
                (ConfigWithSubtstitutionAnnotationsSubInterface.class);
        assertEquals("Please grandma, tell me the story of 'The quick brown fox jumped over the lazy dog'", conf.tellmeTheStory());
    }

    /**
     * @author luigi
     */
    public static interface ConfigWithSubstitutionFile  extends Config {
        String story();
    }

    /**
     * @author luigi
     */
    public static interface ConfigWithSubtstitutionAnnotationsSubInterface extends ConfigWithSubstitutionAnnotations {

        @DefaultValue("grandma")
        public String teller();

        @DefaultValue("Please ${teller}, tell me the story of '${story}'")
        public String tellmeTheStory();
    }

    /**
     * @author luigi
     */
    public static interface ConfigWithSubstitutionAnnotations extends Config {

        @DefaultValue("The ${animal} jumped over the ${target}")
        String story();

        @DefaultValue("quick ${color} fox")
        String animal();

        @DefaultValue("${target.attribute} dog")
        String target();

        @Key("target.attribute")
        @DefaultValue("lazy")
        String targetAttribute();

        @DefaultValue("brown")
        String color();
    }

    public static interface ConfigWithSubstitutionDisabledOnMethod extends Config {
        @DefaultValue("Earth")
        String world();

        @DisableFeature(VARIABLE_EXPANSION)
        @DefaultValue("Hello ${world}.")
        String sayHelloDisabled();
    }

    @Test
    public void shouldNotExpandWorldWhenDisabledOnMethodLevel() {
        ConfigWithSubstitutionDisabledOnMethod cfg = ConfigFactory.create(ConfigWithSubstitutionDisabledOnMethod.class);
        assertEquals("Hello ${world}.", cfg.sayHelloDisabled());
    }

    @DisableFeature(VARIABLE_EXPANSION)
    public static interface ConfigWithSubstitutionDisabledOnClass extends Config {
        @DefaultValue("Earth")
        String world();

        @DefaultValue("Hello ${world}.")
        String sayHelloDisabled();
    }

    @Test
    public void shouldNotExpandWorldWhenDisabledOnClassLevel() {
        ConfigWithSubstitutionDisabledOnClass cfg = ConfigFactory.create(ConfigWithSubstitutionDisabledOnClass.class);
        assertEquals("Hello ${world}.", cfg.sayHelloDisabled());
    }
}
