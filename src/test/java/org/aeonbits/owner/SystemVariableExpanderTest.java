/*
 * Copyright (c) 2012, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;

import static java.lang.System.getProperty;
import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class SystemVariableExpanderTest {
    @Test
    public void testExpandMultipleTilde() {
        SystemVariablesExpander expander = new SystemVariablesExpander();
        String result = expander.expand("foo-~-bar-~-baz");
        String expected = "foo-" + getProperty("user.home") + "-bar-" + getProperty("user.home") + "-baz";
        assertEquals(expected, result);
    }
}
