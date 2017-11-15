/*
 * Copyright (c) 2012-2017, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import org.junit.Test;

import java.util.Date;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class Base64Test {

    @Test
    public void testEncode() {
        String input = "Hello World!";
        String result = Base64.encode(input.getBytes());
        assertEquals("SGVsbG8gV29ybGQh", result);
    }

    @Test
    public void testDecode() throws Exception {
        String input = "SGVsbG8gV29ybGQh";
        byte[] result = Base64.decode(input);
        assertEquals("Hello World!", new String(result));
    }

    @Test
    public void encodeAndDecode() {
        String input = "The date is: " + new Date() + ", a random number is: " + new Random().nextLong();

        String encoded = Base64.encode(input.getBytes());
        byte[] result = Base64.decode(encoded);

        assertEquals(input, new String(result));
    }
}
