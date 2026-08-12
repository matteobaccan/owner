/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.typeconversion.arrays;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Tokenizer;
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
        // it throws an exception since the Tokenizer class cannot be built with no arguments
        @TokenizerClass(NonInstantiableTokenizer.class)
        @DefaultValue("1,2,3")
        public int[] nonInstantiableTokenizer();
    }

    /**
     * There is no constructor to call without knowing what to pass, which is a reason no annotation can
     * work around. Being <b>private</b> was a reason too until 2.0.0, and is not one any more: a class
     * named in an annotation is an implementation detail of the interface that names it and needs no
     * visibility beyond it. See
     * <a href="https://github.com/matteobaccan/owner/issues/186">#186</a> and
     * {@code Issue186Test}, where a private tokenizer of this same kind is used.
     */
    private static class NonInstantiableTokenizer extends CustomCommaTokenizer implements Tokenizer {
        @SuppressWarnings("unused")
        NonInstantiableTokenizer(String separator) {
        }
    }

    @Test
    public void testNonInstantiableTokenizer() throws Exception {
        try {
            cfg.nonInstantiableTokenizer();
            fail("UnsupportedOperationException expected");
        } catch (UnsupportedOperationException ex) {
            // there is no no-argument constructor to find, which is what reflection reports
            assertTrue(String.valueOf(ex.getCause()), ex.getCause() instanceof NoSuchMethodException);
        }
    }
}
