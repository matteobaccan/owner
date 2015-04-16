/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;

import static org.junit.Assert.assertNull;

/**
 * @author Luigi R. Viggiano
 */
public class UndefinedPropertiesTest {
    
    public interface Person extends Config {
        String name();
        int age();
        Double weight();
    }

    @Test
    public void testNoValue() {
        Person config = ConfigFactory.create(Person.class);
        assertNull(config.name());
    }

    @Test(expected = NullPointerException.class)
    public void testNoValuePrimitive() {
        Person config = ConfigFactory.create(Person.class);
        config.age();
    }

    @Test
    public void testNoValueDouble() {
        Person config = ConfigFactory.create(Person.class);
        assertNull(config.weight());
    }

}
