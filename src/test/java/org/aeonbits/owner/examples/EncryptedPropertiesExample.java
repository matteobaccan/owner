package org.aeonbits.owner.examples;
import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Converter;
import sun.misc.BASE64Decoder;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * To encrypt the password:
 *   System.out.println(new BASE64Encoder().encode(xor("tiger".getBytes(), "secret".getBytes())));
 */
public class EncryptedPropertiesExample {

    interface EncryptedConfiguration extends Config {
        @ConverterClass(DecryptConverter.class)
        @DefaultValue("BwwEFxc=")
        String scottPassword();
    }

    public static class DecryptConverter implements Converter {
        public Object convert(Method method, String input) {
            try {
                String key = System.getProperty("example.encryption.key");
                return new String(xor(new BASE64Decoder().decodeBuffer(input), key.getBytes()));
            } catch (IOException e) {
                throw new UnsupportedOperationException(e);
            }
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

