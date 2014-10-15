/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.java8;

import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class Java8Test {


    @Test
    public void defaultMethodsShouldWork() {
        ConfigWithDefaultMethods cfg = ConfigFactory.create(ConfigWithDefaultMethods.class);
        assertEquals(new Integer(8), cfg.sum(3, 5));
    }
}
