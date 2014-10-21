/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.java8;

import org.aeonbits.owner.ConfigFactory;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class Java8Test {

    private ConfigWithJava8Features cfg;

    @Before
    public void before() {
        cfg = ConfigFactory.create(ConfigWithJava8Features.class);
    }

    @Test
    public void defaultMethodsShouldWork() {
        assertEquals(new Integer(8), cfg.sum(3, 5));
    }


    @Test
    public void staticMethodsShouldWork() {
        assertEquals(3, ConfigWithJava8Features.min(3, 10));
    }

    @Test
    public void regularInterfaceMethodsShouldWork() {
        assertEquals(100, cfg.oneHundred());
    }
}
