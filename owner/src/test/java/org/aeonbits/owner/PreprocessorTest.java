/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.PreprocessorClasses;
import org.junit.Test;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Issue #120
 *
 * @author Luigi R. Viggiano
 */
public class PreprocessorTest {

    @PreprocessorClasses({ SkipInlineComments.class, Trim.class })
    public interface ConfigWithPreprocessors extends Config {
        @DefaultValue("  300  ")
        Integer pollingPeriod();

        @PreprocessorClasses(ToLowerCase.class)
        @DefaultValue("  HelloWorld  ")
        String helloWorld();

        @DefaultValue("  This is the value  ! this is an inline comment # this is the inline comment too")
        String skipsInlineComments();

        @PreprocessorClasses(ToLowerCase.class)
        String nullValue();
    }

    @Test
    public void shouldReturnTrimmedValue() {
        ConfigWithPreprocessors cfg = ConfigFactory.create(ConfigWithPreprocessors.class);
        assertEquals(new Integer(300), cfg.pollingPeriod());
    }

    @Test
    public void shouldReturnLowercasedTrimmedHelloWorld() {
        ConfigWithPreprocessors cfg = ConfigFactory.create(ConfigWithPreprocessors.class);
        assertEquals("helloworld", cfg.helloWorld());
    }

    @Test
    public void shouldSkipInlineComments() {
        ConfigWithPreprocessors cfg = ConfigFactory.create(ConfigWithPreprocessors.class);
        assertEquals("This is the value", cfg.skipsInlineComments());
    }

    @Test
    public void shouldNotThrowExceptions() {
        ConfigWithPreprocessors cfg = ConfigFactory.create(ConfigWithPreprocessors.class);
        assertNull(cfg.nullValue());
    }


    // preprocessors implementation

    public static class Trim implements Preprocessor {
        public String process(String input) {
            return input.trim();
        }
    }

    public static class ToLowerCase implements Preprocessor {
        public String process(String input) {
            return input.toLowerCase();
        }
    }

    public static class SkipInlineComments implements Preprocessor {
        public String process(String input) {
            int hashTagIndex = input.indexOf('#');
            int exclamationMarkIndex = input.indexOf("!");
            if (hashTagIndex == -1 && exclamationMarkIndex == -1)
                return input; // comments not present.

            int commentIndex = min(hashTagIndex, exclamationMarkIndex);
            if (commentIndex == -1)
                commentIndex = max(hashTagIndex, exclamationMarkIndex);

            return input.substring(0, commentIndex);
        }
    }

}
