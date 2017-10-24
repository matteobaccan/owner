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
}
