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

/**
 * Provides Base64 encoding and decoding relying on whatever implementation is available on the
 * runtime classpath: <code>java.util.Base64</code> on Java 8 and later, falling back to
 * <code>javax.xml.bind.DatatypeConverter</code> on Java 6 and 7.
 */
public class Base64 {
    private static Method decoderMethod;
    private static Method encoderMethod;
    private static Object decoderObject;
    private static Object encoderObject;

    // Suppresses default constructor, ensuring no one instantiate this class.
    private Base64() {}

    static {
        Class<?>[] decodeParameterTypes = { String.class };
        Class<?>[] encodeParameterTypes = { byte[].class };

        // Java 8, Java 9
        Class<?> javaUtilBase64Class = forName("java.util.Base64");
        if (javaUtilBase64Class != null) {
            try {
                decoderObject = javaUtilBase64Class.getMethod("getDecoder").invoke(null);
                decoderMethod = decoderObject.getClass().getMethod("decode", decodeParameterTypes);
                encoderObject = javaUtilBase64Class.getMethod("getEncoder").invoke(null);
                encoderMethod = encoderObject.getClass().getMethod("encodeToString", encodeParameterTypes);
            } catch (Exception e) {
                reset();
            }
        }

        // Java 6, 7
        if (decoderMethod == null) {
            decoderObject = null;
            encoderObject = null;
            Class<?> javaxXmlBindDatatypeConverterClass = forName("javax.xml.bind.DatatypeConverter");
            if (javaxXmlBindDatatypeConverterClass != null) {
                try {
                    decoderMethod = javaxXmlBindDatatypeConverterClass.getMethod("parseBase64Binary", decodeParameterTypes);
                    encoderMethod = javaxXmlBindDatatypeConverterClass.getMethod("printBase64Binary", encodeParameterTypes);
                } catch (NoSuchMethodException e) {
                    reset();
                }
            }
        }
    }

    private static void reset() {
        encoderObject = null;
        encoderMethod = null;
        decoderMethod = null;
        decoderObject = null;
    }


    /**
     * Decodes a Base64 encoded string back into the original bytes.
     *
     * @param data the Base64 encoded string.
     * @return the decoded bytes.
     * @throws UnsupportedOperationException if no Base64 decoder is available on the classpath.
     */
    public static byte[] decode(String data) {
        if (decoderMethod == null) throw new UnsupportedOperationException("Cannot find Base64 decoder.");
        try {
            return (byte[]) decoderMethod.invoke(decoderObject, data);
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

    /**
     * Encodes the given bytes into a Base64 string.
     *
     * @param data the bytes to encode.
     * @return the Base64 encoded string.
     * @throws UnsupportedOperationException if no Base64 encoder is available on the classpath.
     */
    public static String encode(byte[] data) {
        if (encoderMethod == null) throw new UnsupportedOperationException("Cannot find Base64 encoder.");
        try {
            return (String) encoderMethod.invoke(encoderObject, data);
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

}
