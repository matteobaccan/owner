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
public class TokenizerResolverTest {

    @Test(expected = UnsupportedOperationException.class)
    public void testInstantiation() {
        TokenizerResolver res = new TokenizerResolver() {};
    }
}
