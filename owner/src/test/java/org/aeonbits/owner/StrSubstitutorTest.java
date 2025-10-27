/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
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
    public void testNestedRecursiveResolution() {
        Properties values = new Properties();
        values.setProperty("environment", "dev");
        values.setProperty("environments.dev.browser", "chrome");
        values.setProperty("template", "environments.${environment}.webdriver.${environments.${environment}.browser}.switches.${environment}");
        String templateString = "${template}";
        StrSubstitutor sub = new StrSubstitutor(values);
        String resolvedString = sub.replace(templateString);
        assertEquals("environments.dev.webdriver.chrome.switches.dev", resolvedString);
    }

    @Test
    public void testPropertyWithCurlyBracketsInName() {
        Properties values = new Properties();
        values.setProperty("{foo}", "bar");
        values.setProperty("template", "foo.${{foo}}");
        String templateString = "${template}";
        StrSubstitutor sub = new StrSubstitutor(values);
        String resolvedString = sub.replace(templateString);
        assertEquals("foo.bar", resolvedString);
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
