package org.aeonbits.owner.typeconversion;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Converter;
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
        Map<Character,Character> lookup = new HashMap<Character, Character>();
        public LeetTranslatorConverter(){
            lookup.put('1', 'I');
            lookup.put('5', 's');
            lookup.put('0', 'o');
            lookup.put('3', 'e');
            lookup.put('7', 't');
        }

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
}
