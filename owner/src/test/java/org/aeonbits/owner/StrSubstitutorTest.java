/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Luigi R. Viggiano
 */
public class StrSubstitutorTest {

    @Test
    public void shouldReturnNullWhenNullIsProvided() {
        Properties props = new Properties();
        StrSubstitutor substitutor = new StrSubstitutor(props);
        assertNull(substitutor.replace(null));
    }

    @Test
    public void shouldReplaceVariables() {
        Properties values = new Properties();
        values.setProperty("animal", "quick brown fox");
        values.setProperty("target", "lazy dog");
        String templateString = "The ${animal} jumped over the ${target}.";
        StrSubstitutor sub = new StrSubstitutor(values);
        String resolvedString = sub.replace(templateString);
        assertEquals("The quick brown fox jumped over the lazy dog.", resolvedString);
    }

    @Test
    public void shouldApplyDefaultValues() {
        Properties values = new Properties();
        String templateString = "The ${animal:wolf} jumped over the ${target:sheep}.";
        StrSubstitutor sub = new StrSubstitutor(values);
        String resolvedString = sub.replace(templateString);
        assertEquals("The wolf jumped over the sheep.", resolvedString);
    }

    @Test
    public void shouldOverrideDefaultValues() {
        Properties values = new Properties();
        values.setProperty("animal", "quick brown fox");
        values.setProperty("target", "lazy dog");
        String templateString = "The ${animal:wolf} jumped over the ${target:sheep}.";
        StrSubstitutor sub = new StrSubstitutor(values);
        String resolvedString = sub.replace(templateString);
        assertEquals("The quick brown fox jumped over the lazy dog.", resolvedString);
    }

    @Test
    public void shouldApplyAnEmptyDefaultValue() {
        StrSubstitutor sub = new StrSubstitutor(new Properties());
        assertEquals("[]", sub.replace("[${missing:}]"));
    }

    /**
     * Everything past the first colon is the default value, so values that contain colons themselves - URLs,
     * Windows paths, host:port pairs - are not truncated.
     */
    @Test
    public void shouldKeepColonsInsideTheDefaultValue() {
        StrSubstitutor sub = new StrSubstitutor(new Properties());
        assertEquals("http://example.com/x", sub.replace("${missing:http://example.com/x}"));
        assertEquals("C:\\temp", sub.replace("${missing:C:\\temp}"));
        assertEquals("localhost:8080", sub.replace("${missing:localhost:8080}"));
    }

    /**
     * A property key may legitimately contain a colon, so the whole expression is looked up as a key before the
     * colon is read as a separator. This keeps configurations written before default values existed working.
     */
    @Test
    public void shouldPreferAKeyContainingAColonOverTheDefaultValueSyntax() {
        Properties values = new Properties();
        values.setProperty("jdbc:url", "jdbc:mysql://localhost/test");
        assertEquals("jdbc:mysql://localhost/test", new StrSubstitutor(values).replace("${jdbc:url}"));

        Properties twoColons = new Properties();
        twoColons.setProperty("a:b:c", "abc");
        assertEquals("abc", new StrSubstitutor(twoColons).replace("${a:b:c}"));
    }

    @Test
    public void shouldPreferTheWholeKeyOverItsPrefix() {
        Properties values = new Properties();
        values.setProperty("a:b", "the whole key");
        values.setProperty("a", "just the prefix");
        assertEquals("the whole key", new StrSubstitutor(values).replace("${a:b}"));
    }

    @Test
    public void shouldFallBackToThePrefixKeyWhenTheWholeKeyIsMissing() {
        Properties values = new Properties();
        values.setProperty("a", "just the prefix");
        assertEquals("just the prefix", new StrSubstitutor(values).replace("${a:b}"));
    }

    @Test
    public void shouldStillResolveToEmptyStringWithoutADefaultValue() {
        assertEquals("[]", new StrSubstitutor(new Properties()).replace("[${missing}]"));
    }

    @Test
    public void shouldExpandVariablesFoundInsideAResolvedDefault() {
        Properties values = new Properties();
        values.setProperty("a", "${b}");
        values.setProperty("b", "final");
        assertEquals("final", new StrSubstitutor(values).replace("${a:unused}"));
    }

    @Test
    public void shouldReplaceVariablesHavingBackslashes() {
        Properties values = new Properties();
        values.setProperty("animal", "quick\\brown\\fox");
        values.setProperty("target", "lazy\\dog");
        String templateString = "The\\${animal}\\jumped\\over\\the\\${target}.";
        StrSubstitutor sub = new StrSubstitutor(values);
        String resolvedString = sub.replace(templateString);
        assertEquals("The\\quick\\brown\\fox\\jumped\\over\\the\\lazy\\dog.", resolvedString);
    }

    @Test
    public void shouldReplaceVariablesWithBackSlashesAndShouldWorkWithRecursion() {
        Properties values = new Properties();
        values.setProperty("color", "bro\\wn");
        values.setProperty("animal", "qui\\ck\\${color}\\fo\\x");
        values.setProperty("target.attribute", "la\\zy");
        values.setProperty("target.animal", "do\\g");
        values.setProperty("target", "${target.attribute}\\${target.animal}");
        values.setProperty("template", "The ${animal} jum\\ped over the ${target}.");
        values.setProperty("wrapper", "\\foo\\${template}\\bar\\");
        values.setProperty("wrapper2", "\\baz\\${wrapper}\\qux\\");
        StrSubstitutor sub = new StrSubstitutor(values);
        String resolvedString = sub.replace("${wrapper2}");
        assertEquals("\\baz\\\\foo\\The qui\\ck\\bro\\wn\\fo\\x jum\\ped over the la\\zy\\do\\g.\\bar\\\\qux\\",
                resolvedString);
    }

    @Test
    public void testRecoursiveResolution() {
        Properties values = new Properties();
        values.setProperty("color", "brown");
        values.setProperty("animal", "quick ${color} fox");
        values.setProperty("target.attribute", "lazy");
        values.setProperty("target.animal", "dog");
        values.setProperty("target", "${target.attribute} ${target.animal}");
        values.setProperty("template", "The ${animal} jumped over the ${target}.");
        String templateString = "${template}";
        StrSubstitutor sub = new StrSubstitutor(values);
        String resolvedString = sub.replace(templateString);
        assertEquals("The quick brown fox jumped over the lazy dog.", resolvedString);
    }

    @Test
    public void testMissingPropertyIsReplacedWithEmptyString() {
        Properties values = new Properties() {{
            setProperty("foo", "fooValue");
            setProperty("baz", "bazValue");
        }};
        String template = "Test: ${foo} ${bar} ${baz} :Test";
        String expected = "Test: fooValue  bazValue :Test";
        String result = new StrSubstitutor(values).replace(template);
        assertEquals(expected, result);
    }

    @Test
    public void shouldReturnNullWhenNullIsProvidedWithParameters() {
        Properties props = new Properties();
        StrSubstitutor substitutor = new StrSubstitutor(props);
        assertNull(substitutor.replace(null, 1, "sfx"));
    }

    @Test
    public void testParametrization() {
        Properties values = new Properties() {{
            setProperty("foo", "fooValue");
            setProperty("baz", "bazValue");
        }};

        StrSubstitutor sub = new StrSubstitutor(values);
        assertEquals("foo1", sub.replace("foo%d", 1));
        assertEquals("baz", sub.replace("baz"));
        assertEquals("foo.1.sfx", sub.replace("foo.%d.%s", 1, "sfx"));
    }
}
