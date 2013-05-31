/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;

/**
 * @author Luigi R. Viggiano
 */
public class UnsupportedInstantiationTest {

    @Test(expected = UnsupportedOperationException.class)
    public void testTokenizerResolverInstantiation() {
        new TokenizerResolver() {};
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPropertiesMapperInstantiation() {
        new PropertiesMapper(){};
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPropertiesLoaderInstantiation() {
        new PropertiesLoader(){};
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testConfigFactoryInstantiation() {
        new ConfigFactory(){};
    }

}
