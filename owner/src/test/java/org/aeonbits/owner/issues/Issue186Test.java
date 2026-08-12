/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Converter;
import org.aeonbits.owner.Preprocessor;
import org.aeonbits.owner.Tokenizer;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * See: https://github.com/lviggiano/owner/issues/186
 * <p>
 * A class named in an annotation — a {@link Preprocessor}, a {@link Converter}, a {@link Tokenizer}, a
 * decryptor — is an implementation detail of the configuration that names it, and it no longer has to be
 * <code>public</code>. The reporter wanted a <code>ToUpperCase</code> preprocessor that his library's users
 * would never see, and had to widen his own API to satisfy ours.
 * <p>
 * The class was not being asked to be visible <em>to us</em>, which would be fair: the instantiation lives
 * in <code>org.aeonbits.owner.util</code>, so "the same package" is never true of anybody else's code, and
 * even a package-private class sitting beside the interface that named it was refused. What was being asked
 * for was that it be visible to everyone.
 * <p>
 * Four shapes are covered here because the reporter's case was only the first of them, and the last is a
 * different code path: the converter used <code>Class.newInstance()</code>, which demands a public class
 * <em>and</em> a public constructor of its own.
 */
public class Issue186Test {

    /** The reporter's own example. */
    private static class ToUpperCase implements Preprocessor {
        @Override
        public String process(String input) {
            return input.toUpperCase();
        }
    }

    static class Trim implements Preprocessor {
        @Override
        public String process(String input) {
            return input.trim();
        }
    }

    /** Public, and built through a constructor that is not — the other half of the same question. */
    public static class Reverse implements Preprocessor {
        private Reverse() {
        }

        @Override
        public String process(String input) {
            return new StringBuilder(input).reverse().toString();
        }
    }

    private static class Exclaiming implements Converter<String> {
        @Override
        public String convert(Method method, String input) {
            return input + "!";
        }
    }

    private static class OnSemicolons implements Tokenizer {
        @Override
        public String[] tokens(String values) {
            return values.split(";");
        }
    }

    public interface WithAPrivatePreprocessor extends Config {
        @DefaultValue("a")
        @PreprocessorClasses(ToUpperCase.class)
        String propA();
    }

    public interface WithAPackagePrivatePreprocessor extends Config {
        @DefaultValue("  spaced  ")
        @PreprocessorClasses(Trim.class)
        String propA();
    }

    public interface WithAPrivateConstructor extends Config {
        @DefaultValue("abc")
        @PreprocessorClasses(Reverse.class)
        String propA();
    }

    public interface WithAPrivateConverter extends Config {
        @DefaultValue("hello")
        @ConverterClass(Exclaiming.class)
        String propA();
    }

    public interface WithAPrivateTokenizer extends Config {
        @DefaultValue("1;2;3")
        @TokenizerClass(OnSemicolons.class)
        int[] numbers();
    }

    @Test
    public void aPrivatePreprocessorIsUsed() {
        assertEquals("A", ConfigFactory.create(WithAPrivatePreprocessor.class).propA());
    }

    @Test
    public void aPackagePrivatePreprocessorIsUsed() {
        assertEquals("spaced", ConfigFactory.create(WithAPackagePrivatePreprocessor.class).propA());
    }

    @Test
    public void aPrivateConstructorIsUsed() {
        assertEquals("cba", ConfigFactory.create(WithAPrivateConstructor.class).propA());
    }

    @Test
    public void aPrivateConverterIsUsed() {
        assertEquals("hello!", ConfigFactory.create(WithAPrivateConverter.class).propA());
    }

    @Test
    public void aPrivateTokenizerIsUsed() {
        assertArrayEquals(new int[]{1, 2, 3}, ConfigFactory.create(WithAPrivateTokenizer.class).numbers());
    }

    // -------------------------------------------------------------------------------------------------
    // what is still refused, and has to be: visibility was never the reason a class could not be built
    // -------------------------------------------------------------------------------------------------

    public static class NeedsAnArgument implements Preprocessor {
        @SuppressWarnings("unused")
        public NeedsAnArgument(String reason) {
        }

        @Override
        public String process(String input) {
            return input;
        }
    }

    public interface WithAPreprocessorThatNeedsAnArgument extends Config {
        @DefaultValue("a")
        @PreprocessorClasses(NeedsAnArgument.class)
        String propA();
    }

    @Test
    public void aClassWithNoNoArgumentConstructorIsStillRefusedAndStillNamed() {
        try {
            ConfigFactory.create(WithAPreprocessorThatNeedsAnArgument.class).propA();
            fail("a class with no no-argument constructor cannot be built");
        } catch (UnsupportedOperationException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("cannot be instantiated"));
            assertTrue(refused.getMessage(), refused.getMessage().contains("NeedsAnArgument"));
            assertTrue("the cause says what reflection found: " + refused.getCause(),
                    refused.getCause() instanceof NoSuchMethodException);
        }
    }
}
