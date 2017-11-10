/*
 * Copyright (c) 2012-2017, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import java.lang.reflect.Method;

import static org.aeonbits.owner.util.Reflection.forName;

public class Base64 {
    private static Method decoderMethod;
    private static Object decoderObject;

    static {
        Class<?>[] parameterTypes = { String.class };

        // Java 8, Java 9
        Class<?> javaUtilBase64Class = forName("java.util.Base64");
        if (javaUtilBase64Class != null) {
            try {
                decoderObject = javaUtilBase64Class.getMethod("getDecoder").invoke(null);
                decoderMethod = decoderObject.getClass().getMethod("decode", parameterTypes);
            } catch (Exception e) {
                decoderMethod = null;
                decoderObject = null;
            }
        }

        // Java 6, 7
        if (decoderMethod == null) {
            decoderObject = null;
            Class<?> javaxXmlBindDatatypeConverterClass = forName("javax.xml.bind.DatatypeConverter");
            if (javaxXmlBindDatatypeConverterClass != null) {
                try {
                    decoderMethod = javaxXmlBindDatatypeConverterClass.getMethod("parseBase64Binary", parameterTypes);
                } catch (NoSuchMethodException e) {
                    decoderMethod = null;
                    decoderObject = null;
                }
            }
        }
    }


    public static byte[] decode(String encryptedData) {
        if (decoderMethod == null) throw new UnsupportedOperationException("Cannot find Base64 Decoder class.");
        try {
            return (byte[]) decoderMethod.invoke(decoderObject, encryptedData);
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static String printBase64Binary(byte[] input) {
        return _printBase64Binary(input, 0, input.length);
    }

    private static String _printBase64Binary(byte[] input, int offset, int len) {
        char[] buf = new char[((len + 2) / 3) * 4];
        int ptr = _printBase64Binary(input, offset, len, buf, 0);
        assert ptr == buf.length;
        return new String(buf);
    }

    private static int _printBase64Binary(byte[] input, int offset, int len, char[] buf, int ptr) {
        // encode elements until only 1 or 2 elements are left to encode
        int remaining = len;
        int i;
        for (i = offset;remaining >= 3; remaining -= 3, i += 3) {
            buf[ptr++] = encode(input[i] >> 2);
            buf[ptr++] = encode(
                    ((input[i] & 0x3) << 4)
                            | ((input[i + 1] >> 4) & 0xF));
            buf[ptr++] = encode(
                    ((input[i + 1] & 0xF) << 2)
                            | ((input[i + 2] >> 6) & 0x3));
            buf[ptr++] = encode(input[i + 2] & 0x3F);
        }
        // encode when exactly 1 element (left) to encode
        if (remaining == 1) {
            buf[ptr++] = encode(input[i] >> 2);
            buf[ptr++] = encode(((input[i]) & 0x3) << 4);
            buf[ptr++] = '=';
            buf[ptr++] = '=';
        }
        // encode when exactly 2 elements (left) to encode
        if (remaining == 2) {
            buf[ptr++] = encode(input[i] >> 2);
            buf[ptr++] = encode(((input[i] & 0x3) << 4)
                    | ((input[i + 1] >> 4) & 0xF));
            buf[ptr++] = encode((input[i + 1] & 0xF) << 2);
            buf[ptr++] = '=';
        }
        return ptr;
    }

    private static final char[] encodeMap = initEncodeMap();

    private static char[] initEncodeMap() {
        char[] map = new char[64];
        int i;
        for (i = 0; i < 26; i++) {
            map[i] = (char) ('A' + i);
        }
        for (i = 26; i < 52; i++) {
            map[i] = (char) ('a' + (i - 26));
        }
        for (i = 52; i < 62; i++) {
            map[i] = (char) ('0' + (i - 52));
        }
        map[62] = '+';
        map[63] = '/';

        return map;
    }

    static char encode(int i) {
        return encodeMap[i & 0x3F];
    }

}
