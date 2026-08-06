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
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * See: https://github.com/lviggiano/owner/issues/269
 * <p>
 * A value made of nothing but whitespace produces an empty collection, and every element is trimmed. Both are
 * deliberate — they are what makes <code>prop=</code> an empty list and <code>a, b</code> a clean two-element
 * one — but together they make a single element consisting of a newline impossible to express. Opting out of
 * the tokenization is what {@code @CollectionConverterClass} is for.
 */
public class Issue269Test {

    /** The reporter's interface, verbatim. */
    public interface WithSingleElementList extends Config {
        @DefaultValue("\n")
        List<String> getList();
    }

    @Test
    public void aWhitespaceOnlyValueIsAnEmptyCollection() {
        assertTrue(ConfigFactory.create(WithSingleElementList.class).getList().isEmpty());
    }

    public interface WhitespaceConfig extends Config {
        @DefaultValue("   ")
        List<String> spaces();

        @DefaultValue("")
        List<String> empty();

        @DefaultValue(" a ")
        List<String> padded();

        @DefaultValue("a, b")
        List<String> normal();
    }

    @Test
    public void theRuleIsTheSameForEveryKindOfWhitespace() {
        WhitespaceConfig cfg = ConfigFactory.create(WhitespaceConfig.class);

        assertTrue(cfg.spaces().isEmpty());
        assertTrue(cfg.empty().isEmpty());
        assertEquals(Collections.singletonList("a"), cfg.padded());     // the element is trimmed
        assertEquals(2, cfg.normal().size());
    }

    public static class WholeValueConverter implements Converter<List<String>> {
        @Override
        public List<String> convert(Method method, String input) {
            return Collections.singletonList(input);
        }
    }

    public interface UntokenizedConfig extends Config {
        @CollectionConverterClass(WholeValueConverter.class)
        @DefaultValue("\n")
        List<String> getList();
    }

    /** The answer: the converter receives the value untouched, newline included. */
    @Test
    public void aCollectionConverterClassKeepsTheValueAsItIs() {
        List<String> list = ConfigFactory.create(UntokenizedConfig.class).getList();

        assertEquals(1, list.size());
        assertEquals("\n", list.get(0));
    }
}
