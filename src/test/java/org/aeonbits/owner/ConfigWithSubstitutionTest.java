/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;

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

}
