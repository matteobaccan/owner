/*
 * Copyright (c) 2012-2017, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

/**
 * Provides Base64 encoding and decoding.
 *
 * @deprecated this class was a runtime-selection shim between <code>java.util.Base64</code> (Java 8+)
 * and <code>javax.xml.bind.DatatypeConverter</code> (Java 6/7); since OWNER requires Java 8, use
 * {@link java.util.Base64} directly instead. This class will be removed in a future release.
 */
@Deprecated
public class Base64 {

    // Suppresses default constructor, ensuring no one instantiate this class.
    private Base64() {}

    /**
     * Decodes a Base64 encoded string back into the original bytes.
     *
     * @param data the Base64 encoded string.
     * @return the decoded bytes.
     * @throws UnsupportedOperationException if the input is not valid Base64.
     */
    public static byte[] decode(String data) {
        try {
            return java.util.Base64.getDecoder().decode(data);
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

    /**
     * Encodes the given bytes into a Base64 string.
     *
     * @param data the bytes to encode.
     * @return the Base64 encoded string.
     * @throws UnsupportedOperationException if the input cannot be encoded.
     */
    public static String encode(byte[] data) {
        try {
            return java.util.Base64.getEncoder().encodeToString(data);
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

}
