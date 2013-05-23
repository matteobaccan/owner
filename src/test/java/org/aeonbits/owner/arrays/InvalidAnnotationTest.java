/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.arrays;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Tokenizer;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Luigi R. Viggiano
 */
public class InvalidAnnotationTest {
    private InvalidAnnotationConfig cfg;
    
    @Before
    public void before() {
        cfg = ConfigFactory.create(InvalidAnnotationConfig.class);
    }

    public static interface InvalidAnnotationConfig extends Config {
        @TokenizerClass(NonInstantiableTokenizer.class)
        @DefaultValue("1,2,3")
        public int[] nonInstantiableTokenizer(); // throws an exception since the Tokenizer class is declared as private
    }

    // it's private, it cannot be instantiated by the OWNER library
    private static class NonInstantiableTokenizer extends CustomCommaTokenizer implements Tokenizer {
    }

    @Test
    public void testNonInstantiableTokenizer() throws Exception {
        try {
            cfg.nonInstantiableTokenizer();
            fail("UnsupportedOperationException expected");
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getCause() instanceof IllegalAccessException); // since NonInstantiableTokenizer is private.
        }
    }
}
