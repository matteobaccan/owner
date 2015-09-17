package org.aeonbits.owner.crypto;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import static org.aeonbits.owner.Config.*;

public class CryptoConfigTest {
    public static final String SECRET_KEY =        "ABCDEFGH12345678";
    public static final String PASSWORD_EXPECTED = "This is my key.";
    public static final String SALUTATION_EXPECTED = "Good Morning";

    @DecrypterManagerClass(SampleDecrypter.class)
    public interface SampleConfig extends Config {
        String hello(String param);

        @DefaultValue("Bohemian Rapsody - Queen")
        String favoriteSong();

        String unspecifiedProperty();

        @Key("server.http.port")
        int httpPort();

        @Key("salutation.text")
        @DefaultValue("Good Morning")
        String salutation();

        @DefaultValue("foo")
        void voidMethodWithValue();

        void voidMethodWithoutValue();

        @Key("crypto.password")
        @EncryptedKey
        @DefaultValue("tzH7IKLCVc0AC72fh5DiZA==")
        String password();
    }

    public static class SampleDecrypter implements Decrypter, Encrypter {
        final AESEncryption encrypter = CryptoUtils.newEncryptionSilently( "AES", SECRET_KEY );

        public String decrypt( String value ) {
            try {
                return this.encrypter.decrypt( value );
            } catch ( Exception cause ) {
                cause.printStackTrace();
                return value;
            }
        }

        public String encrypt( String value ) {
            try {
                return this.encrypter.encrypt( value );
            } catch ( Exception cause ) {
                cause.printStackTrace();
                return value;
            }
        }
    }

    /**
     * We test that the decrypt works as expected.
     */
    @Test
    public void passwordDecryptedTest() {
        SampleConfig config = ConfigFactory.create( SampleConfig.class );
        String decryptedPassword = config.password();
        assertEquals( "Property password wasn't decrypted.", PASSWORD_EXPECTED, decryptedPassword );
    }

    /**
     * This test checks that the decrypted value is not cached.
     * So we recover it twice.
     */
    @Test
    public void passwordDecryptedTwiceTest() {
        SampleConfig config = ConfigFactory.create( SampleConfig.class );
        String decryptedPassword = config.password();
        decryptedPassword = config.password();
        assertEquals( "Property password wasn't decrypted second time.", PASSWORD_EXPECTED, decryptedPassword );
    }

    /**
     * We test the value of non encripted properties.
     */
    @Test
    public void salutationNotDecryptedTest() {
        SampleConfig config = ConfigFactory.create( SampleConfig.class );
        String salutation = config.salutation();
        assertEquals( "Salutation value is not expected", SALUTATION_EXPECTED, salutation );
    }
}
