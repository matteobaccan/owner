/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.typeconversion;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Converter;
import org.aeonbits.owner.Factory;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Created by stefan on 7.9.2016.
 */
public class ConverterRegistryTest {
    private static final String LEET_SPEEK = "1'm 50 l337";
    private static final String LEET_TRANSLATION = "I'm so leet";
    private static final String FOOBAR_RESPONSE = "FooBar";

    public static class LeetTranslatorConverter implements Converter<String> {
        Map<Character,Character> lookup = new HashMap<>();
        public LeetTranslatorConverter(){
            lookup.put('1', 'I');
            lookup.put('5', 's');
            lookup.put('0', 'o');
            lookup.put('3', 'e');
            lookup.put('7', 't');
        }

        @Override
        public String convert(Method targetMethod, String text) {
            StringBuilder sb = new StringBuilder(text);
            for( int i=0 ; i<text.length(); i++) {
                if( lookup.containsKey(text.charAt(i))){
                    sb.setCharAt(i, lookup.get(text.charAt(i)));
                }
            }
            return sb.toString();
        }
    }

    public static class FooBarConverter implements Converter<String> {
        @Override
        public String convert(Method method, String input) {
            return FOOBAR_RESPONSE;
        }
    }

    interface MyConfig extends Config {
        @DefaultValue(LEET_SPEEK)
        String leetSpeek();

        @DefaultValue(LEET_SPEEK)
        @ConverterClass(FooBarConverter.class)
        String leetSpeekWithConverterClassAnnotation();
    }

    @Test
    public void testBasicConverterRegistry(){
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        assertEquals("Converter class has not been registered yet.", LEET_SPEEK, cfg.leetSpeek());
        ConfigFactory.setTypeConverter(String.class, LeetTranslatorConverter.class);
        assertEquals("Registered converter class should have been used but wasn't.", LEET_TRANSLATION, cfg.leetSpeek());
        ConfigFactory.removeTypeConverter(String.class);
        assertEquals("Converter class should have been removed.", LEET_SPEEK, cfg.leetSpeek());
    }

    @Test
    public void testConverterClassAnnotationOverride(){
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        assertEquals("Expected a response from the annotated converter class.", FOOBAR_RESPONSE, cfg.leetSpeekWithConverterClassAnnotation());
        ConfigFactory.setTypeConverter(String.class, LeetTranslatorConverter.class);
        assertEquals("Still expecting a response from the annotated converter class", FOOBAR_RESPONSE, cfg.leetSpeekWithConverterClassAnnotation());
        ConfigFactory.removeTypeConverter(String.class);
    }

    /**
     * <b>A converter belongs to the factory it was registered on.</b> Until 2.0.0 it did not: the registry
     * was a static field, so this - an instance method on {@link org.aeonbits.owner.Factory} - wrote a map
     * every factory in the JVM shared. A configuration created by {@code ConfigFactory.newInstance()} is
     * isolated in its properties, its loaders, its value handlers, its prefix and its strictness, and
     * conversion was the one thing that leaked out of it.
     */
    @Test
    public void aConverterRegisteredOnOneFactoryDoesNotReachAnother() {
        Factory one = ConfigFactory.newInstance();
        Factory two = ConfigFactory.newInstance();

        one.setTypeConverter(String.class, LeetTranslatorConverter.class);
        try {
            assertEquals("the factory it was registered on converts",
                    LEET_TRANSLATION, one.create(MyConfig.class).leetSpeek());
            assertEquals("and the one it was not registered on does not",
                    LEET_SPEEK, two.create(MyConfig.class).leetSpeek());
            assertEquals("nor does the default factory",
                    LEET_SPEEK, ConfigFactory.create(MyConfig.class).leetSpeek());
        } finally {
            one.removeTypeConverter(String.class);
        }
    }

    /** And removing it where it was never registered takes nothing away from anybody. */
    @Test
    public void removingItOnOneFactoryDoesNotRemoveItOnAnother() {
        Factory one = ConfigFactory.newInstance();
        Factory two = ConfigFactory.newInstance();

        one.setTypeConverter(String.class, LeetTranslatorConverter.class);
        try {
            two.removeTypeConverter(String.class);

            assertEquals(LEET_TRANSLATION, one.create(MyConfig.class).leetSpeek());
        } finally {
            one.removeTypeConverter(String.class);
        }
    }

    /**
     * The static methods of {@code ConfigFactory} are the default factory and nothing more, which is what
     * they are for every other setting: {@code setProperty}, {@code registerLoader},
     * {@code registerValueHandler}. <b>This is the behaviour that changed in 2.0.0</b> — code that
     * registered a converter statically and then created its configurations from a factory of its own was
     * relying on the leak.
     */
    @Test
    public void theStaticMethodsAreTheDefaultFactoryAndNothingMore() {
        Factory fresh = ConfigFactory.newInstance();

        ConfigFactory.setTypeConverter(String.class, LeetTranslatorConverter.class);
        try {
            assertEquals(LEET_TRANSLATION, ConfigFactory.create(MyConfig.class).leetSpeek());
            assertEquals(LEET_SPEEK, fresh.create(MyConfig.class).leetSpeek());
        } finally {
            ConfigFactory.removeTypeConverter(String.class);
        }
    }
}
