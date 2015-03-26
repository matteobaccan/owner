/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.typeconversion;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class PrimitiveTypesTest {

    public static interface PrimitiveTypesConfig extends Config {
        @DefaultValue("42")
        int answerToLifeUniverseAndEverything();

        @DefaultValue("3.141592653589793")
        double pi();

        @DefaultValue("0.5")
        float half();

        @DefaultValue("false")
        boolean worldIsFlat();

        @DefaultValue("7")
        Integer daysInWeek();

        @DefaultValue("invalid")
        Integer invalid();
    }

    @Test
    public void testDefaultIntValue() {
        PrimitiveTypesConfig config = ConfigFactory.create(PrimitiveTypesConfig.class);
        assertEquals(42, config.answerToLifeUniverseAndEverything());
    }

    @Test
    public void testDefautDoubleValue() {
        PrimitiveTypesConfig config = ConfigFactory.create(PrimitiveTypesConfig.class);
        assertEquals(3.141592653589793D, config.pi(), 0.000000000000001D) ;
    }

    @Test
    public void testDefautFloatValue() {
        PrimitiveTypesConfig config = ConfigFactory.create(PrimitiveTypesConfig.class);
        assertEquals(0.5f, config.half(), 0.01f);
    }

    @Test
    public void testDefautBooleanValue() {
        PrimitiveTypesConfig config = ConfigFactory.create(PrimitiveTypesConfig.class);
        assertEquals(false, config.worldIsFlat());
    }

    @Test
    public void testDefaultIntegerValue() {
        PrimitiveTypesConfig config = ConfigFactory.create(PrimitiveTypesConfig.class);
        assertEquals(new Integer(7), config.daysInWeek());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testInvalid() {
        PrimitiveTypesConfig config = ConfigFactory.create(PrimitiveTypesConfig.class);
        config.invalid();
    }

}
