/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.crypto;

import java.util.List;
import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import static org.aeonbits.owner.Config.*;

public class CryptoConfigTest {
    public static final String SECRET_KEY =        "ABCDEFGH12345678";
    public static final String PASSWORD_EXPECTED = "This is my key.";
    public static final String SALUTATION_EXPECTED = "Good Morning";

    @DecryptorClass(Decryptor1.class)
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
        @EncryptedValue
        @DefaultValue("tzH7IKLCVc0AC72fh5DiZA==")
        String password();

        @Key("crypto.list")
        @EncryptedValue
        @Separator(",")
        @DefaultValue("Pfzoiet5E5zN2/7tfgrGLQ==")
        List<String> cryptoList();

        @Key("encryptedValue")
        @DefaultValue("tzH7IKLCVc0AC72fh5DiZA==")
        String encryptedValue();

        @Key("password.variable.expanded")
        @EncryptedValue
        @DefaultValue("${encryptedValue}")
        String passwordVariableExpanded();
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
        assertEquals( "May be property password was decrypted twice.", PASSWORD_EXPECTED, decryptedPassword );
    }

    @Test
    public void listDecryptedTest() {
        SampleConfig config = ConfigFactory.create( SampleConfig.class );
        List<String> decryptedList = config.cryptoList();
        List<String> expectedList = Arrays.asList( "1", "2", "3", "4");
        assertEquals( "KKKK", expectedList, decryptedList );
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

    /**
     * This test checks that the decrypted value is not cached.
     * So we recover it twice.
     */
    @Test
    public void passwordDecryptedWhenVariableSubstitutionIsSet() {
        SampleConfig config = ConfigFactory.create( SampleConfig.class );
        String decryptedPassword = config.passwordVariableExpanded();
        assertEquals( "May be property password was decrypted twice.", PASSWORD_EXPECTED, decryptedPassword );
    }

    public interface NoDecryptorClassConfig extends Config {
        @Key("crypto.password")
        @EncryptedValue
        @DefaultValue("tzH7IKLCVc0AC72fh5DiZA==")
        String password();
    }

    /**
     * Without a DecryptorClass annotation the IdentityDecryptor applies,
     * so the encrypted value must be returned as is.
     */
    @Test
    public void encryptedValueWithoutDecryptorClassIsReturnedAsIs() {
        NoDecryptorClassConfig config = ConfigFactory.create( NoDecryptorClassConfig.class );
        assertEquals( "The value should pass through the IdentityDecryptor unchanged.",
                "tzH7IKLCVc0AC72fh5DiZA==", config.password() );
    }

    @Test
    public void decryptWithDefaultReturnsDecryptedValueWhenDecryptionSucceeds() {
        Decryptor identity = new IdentityDecryptor();
        assertEquals( "value", identity.decrypt( "value", "default" ) );
    }

    @Test
    public void decryptWithDefaultReturnsDefaultValueWhenDecryptionFails() {
        Decryptor failing = new AbstractDecryptor() {
            @Override
            public String decrypt( String value ) {
                throw new IllegalArgumentException( "cannot decrypt" );
            }
        };
        assertEquals( "default", failing.decrypt( "value", "default" ) );
    }

    public static class Decryptor1 extends SampleDecryptor {
        public Decryptor1() {
            super( "AES", SECRET_KEY );
        }
    }

    public static class Decryptor2 extends SampleDecryptor {
        public Decryptor2() {
            super( "AES", SECRET_KEY + SECRET_KEY );
        }
    }
}
