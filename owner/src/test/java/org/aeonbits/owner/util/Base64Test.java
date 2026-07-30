/*
 * Copyright (c) 2012-2017, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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

    @Test(expected = UnsupportedOperationException.class)
    public void testDecodeWithInvalidInput() {
        Base64.decode("this is not base64!!!");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testEncodeWithNullInput() {
        Base64.encode(null);
    }

    /**
     * Simulates a runtime with no Base64 implementation available (neither
     * java.util.Base64 nor javax.xml.bind.DatatypeConverter) by calling the
     * private reset(); encode and decode must fail fast. The original state
     * is restored afterwards.
     */
    @Test
    public void testEncodeAndDecodeWhenNoImplementationIsAvailable() throws Exception {
        String[] fieldNames = {"decoderMethod", "encoderMethod", "decoderObject", "encoderObject"};
        Object[] saved = new Object[fieldNames.length];
        Field[] fields = new Field[fieldNames.length];
        for (int i = 0; i < fieldNames.length; i++) {
            fields[i] = Base64.class.getDeclaredField(fieldNames[i]);
            fields[i].setAccessible(true);
            saved[i] = fields[i].get(null);
        }

        Method reset = Base64.class.getDeclaredMethod("reset");
        reset.setAccessible(true);
        reset.invoke(null);
        try {
            try {
                Base64.decode("SGVsbG8gV29ybGQh");
                fail("decode should fail when no decoder is available");
            } catch (UnsupportedOperationException expected) {
                assertEquals("Cannot find Base64 decoder.", expected.getMessage());
            }
            try {
                Base64.encode("Hello World!".getBytes());
                fail("encode should fail when no encoder is available");
            } catch (UnsupportedOperationException expected) {
                assertEquals("Cannot find Base64 encoder.", expected.getMessage());
            }
        } finally {
            for (int i = 0; i < fieldNames.length; i++)
                fields[i].set(null, saved[i]);
        }
    }
}
