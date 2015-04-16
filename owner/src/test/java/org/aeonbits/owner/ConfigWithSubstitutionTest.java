/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class ConfigWithSubstitutionTest {

    public interface ConfigWithSubstitutionFile extends Config {
        String story();
    }

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
        assertEquals("Please grandma, tell me the story of 'The quick brown fox jumped over the lazy dog'",
                conf.tellmeTheStory());
    }

    public static interface ConfigWithSubtstitutionAnnotationsSubInterface extends ConfigWithSubstitutionAnnotations {
        @DefaultValue("grandma")
        public String teller();

        @DefaultValue("Please ${teller}, tell me the story of '${story}'")
        public String tellmeTheStory();
    }

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

}
