/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

/**
 * @author Luigi R. Viggiano
 */
public class SplitAndTrimTokenizerTest {

    @Test
    public void testTokensWithCommaSeparator() {
        Tokenizer tokenizer = new SplitAndTrimTokenizer(",");
        String[] result = tokenizer.tokens("foo, bar, baz");
        assertArrayEquals(new String[]{"foo", "bar", "baz"}, result);
    }

    @Test
    public void testTokensWithExtraWhitespace() {
        Tokenizer tokenizer = new SplitAndTrimTokenizer(",");
        String[] result = tokenizer.tokens("  foo  ,  bar \t , \n baz  ");
        assertArrayEquals(new String[]{"foo", "bar", "baz"}, result);
    }

    @Test
    public void testTokensWithSingleValue() {
        Tokenizer tokenizer = new SplitAndTrimTokenizer(",");
        String[] result = tokenizer.tokens("  single  ");
        assertArrayEquals(new String[]{"single"}, result);
    }

    @Test
    public void testTokensWithEmptyString() {
        Tokenizer tokenizer = new SplitAndTrimTokenizer(",");
        String[] result = tokenizer.tokens("");
        assertArrayEquals(new String[]{""}, result);
    }

    @Test
    public void testTokensWithConsecutiveSeparators() {
        Tokenizer tokenizer = new SplitAndTrimTokenizer(",");
        String[] result = tokenizer.tokens("foo,,bar");
        assertArrayEquals(new String[]{"foo", "", "bar"}, result);
    }

    @Test
    public void testTokensWithTrailingSeparator() {
        Tokenizer tokenizer = new SplitAndTrimTokenizer(",");
        String[] result = tokenizer.tokens("foo,bar,");
        assertArrayEquals(new String[]{"foo", "bar", ""}, result);
    }

    @Test
    public void testTokensWithLeadingSeparator() {
        Tokenizer tokenizer = new SplitAndTrimTokenizer(",");
        String[] result = tokenizer.tokens(",foo,bar");
        assertArrayEquals(new String[]{"", "foo", "bar"}, result);
    }

    @Test
    public void testTokensWithCustomRegexSeparator() {
        Tokenizer tokenizer = new SplitAndTrimTokenizer("\\s*\\|\\s*");
        String[] result = tokenizer.tokens("apple | banana | cherry");
        assertArrayEquals(new String[]{"apple", "banana", "cherry"}, result);
    }

    @Test
    public void testTokensWithSemicolonSeparator() {
        Tokenizer tokenizer = new SplitAndTrimTokenizer(";");
        String[] result = tokenizer.tokens("one; two ;three;");
        assertArrayEquals(new String[]{"one", "two", "three", ""}, result);
    }
}
