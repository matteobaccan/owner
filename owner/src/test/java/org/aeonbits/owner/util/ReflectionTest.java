/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Luigi R. Viggiano
 */
public class ReflectionTest {

    @Test
    public void testAvailableWithNonExistentClass() {
        boolean available = Reflection.isClassAvailable("foo.bar.baz.FooBar");
        assertFalse(available);
    }

    @Test
    public void testAvailableWithExistentClass(){
        boolean available = Reflection.isClassAvailable("java.lang.String");
        assertTrue(available);
    }

}
