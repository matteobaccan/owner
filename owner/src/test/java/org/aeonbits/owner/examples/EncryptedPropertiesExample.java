/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.examples;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Converter;
import org.apache.commons.codec.binary.Base64;

import java.lang.reflect.Method;

/**
 *  To encrypt the password:
 *  <pre>
 *  System.out.println(Base64.encodeBase64String(xor("tiger".getBytes(), "secret".getBytes())));
 *  </pre>
 * @author Luigi R. Viggiano
 */
public class EncryptedPropertiesExample {

    interface EncryptedConfiguration extends Config {
        @ConverterClass(DecryptConverter.class)
        @DefaultValue("BwwEFxc=")
        String scottPassword();
    }

    public static class DecryptConverter implements Converter {
        public Object convert(Method method, String input) {
            String key = System.getProperty("example.encryption.key");
            return new String(xor(Base64.decodeBase64(input), key.getBytes()));
        }
    }

    public static void main(String[] args) {
        System.setProperty("example.encryption.key", "secret");
        EncryptedConfiguration example = ConfigFactory.create(EncryptedConfiguration.class);
        System.out.println(example.scottPassword());
    }

    private static byte[] xor(final byte[] input, final byte[] secret) {
        final byte[] output = new byte[input.length];
        if (secret.length == 0) {
            throw new IllegalArgumentException("empty encryption key");
        }
        int spos = 0;
        for (int pos = 0; pos < input.length; ++pos) {
            output[pos] = (byte) (input[pos] ^ secret[spos]);
            ++spos;
            if (spos >= secret.length) {
                spos = 0;
            }
        }
        return output;
    }
}

