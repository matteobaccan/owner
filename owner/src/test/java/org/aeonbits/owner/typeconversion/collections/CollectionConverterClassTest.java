/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.typeconversion.collections;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.CollectionConverterClass;
import org.aeonbits.owner.Config.ConverterClass;
import org.aeonbits.owner.Config.Separator;
import org.aeonbits.owner.Config.TokenizerClass;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Converter;
import org.aeonbits.owner.Tokenizer;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for {@link CollectionConverterClass}, which hands the raw property value to a single converter instead of
 * letting OWNER tokenize it and convert one element at a time.
 *
 * @author Adam Huječek
 */
public class CollectionConverterClassTest {

    private MyConfig cfg;

    public static class Server {
        private final String name;
        private final Integer port;

        public Server(String name, Integer port) {
            this.name = name;
            this.port = port;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Server)) return false;
            Server other = (Server) obj;
            return Objects.equals(name, other.name) && Objects.equals(port, other.port);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, port);
        }

        @Override
        public String toString() {
            return name + ":" + port;
        }
    }

    public interface MyConfig extends Config {
        @DefaultValue("google.com, yahoo.com:8080, owner.aeonbits.org:4000")
        @CollectionConverterClass(UnmodifiableListConverter.class)
        @ConverterClass(ServerConverter.class)
        Collection<Server> serversWithoutSeparatorOrTokenizer();

        @DefaultValue("google.com; yahoo.com:8080; owner.aeonbits.org:4000")
        @CollectionConverterClass(UnmodifiableListConverter.class)
        @ConverterClass(ServerConverter.class)
        @Separator(";")
        Collection<Server> serversWithSeparator();

        @DefaultValue("google.com^yahoo.com:8080^owner.aeonbits.org:4000")
        @CollectionConverterClass(UnmodifiableListConverter.class)
        @ConverterClass(ServerConverter.class)
        @TokenizerClass(SimpleTokenizer.class)
        Collection<Server> serversWithTokenizer();

        /**
         * The point of the annotation: the value is a single indivisible document, and the built-in tokenization
         * would tear it apart before the converter ever sees it.
         */
        @DefaultValue("[a, b], [c, d]")
        @CollectionConverterClass(PairListConverter.class)
        List<String> pairsNotTokenized();
    }

    public static class ServerConverter implements Converter<Server> {
        @Override
        public Server convert(Method targetMethod, String text) {
            String[] split = text.split(":", -1);
            String name = split[0];
            Integer port = 80;
            if (split.length >= 2)
                port = Integer.valueOf(split[1]);
            return new Server(name, port);
        }
    }

    /**
     * Shows that a collection converter can still honour {@link TokenizerClass}, {@link Separator} and
     * {@link ConverterClass} if it chooses to: OWNER hands over the raw text and stays out of the way.
     */
    public static class UnmodifiableListConverter implements Converter<List<?>> {
        @Override
        public List<?> convert(Method targetMethod, String text) {
            String[] tokens = tokenize(targetMethod, text);
            ConverterClass converterClass = targetMethod.getAnnotation(ConverterClass.class);
            try {
                Converter<?> elementConverter = converterClass.value().getDeclaredConstructor().newInstance();
                List<Object> list = new ArrayList<>(tokens.length);
                for (String token : tokens)
                    list.add(elementConverter.convert(targetMethod, token.trim()));
                return Collections.unmodifiableList(list);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        private static String[] tokenize(Method targetMethod, String text) {
            TokenizerClass tokenizerClass = targetMethod.getAnnotation(TokenizerClass.class);
            if (tokenizerClass != null) {
                try {
                    Tokenizer tokenizer = tokenizerClass.value().getDeclaredConstructor().newInstance();
                    return tokenizer.tokens(text);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
            Separator separator = targetMethod.getAnnotation(Separator.class);
            return text.split(separator != null ? separator.value() : ",", -1);
        }
    }

    /** Splits on "], " only, so the commas inside each pair survive. */
    public static class PairListConverter implements Converter<List<String>> {
        @Override
        public List<String> convert(Method targetMethod, String text) {
            List<String> result = new ArrayList<>();
            for (String chunk : text.split("(?<=\\]), "))
                result.add(chunk.trim());
            return result;
        }
    }

    public static class SimpleTokenizer implements Tokenizer {
        @Override
        public String[] tokens(String values) {
            return values.split("\\^", -1);
        }
    }

    private static List<Server> expectedServers() {
        return Arrays.asList(
                new Server("google.com", 80),
                new Server("yahoo.com", 8080),
                new Server("owner.aeonbits.org", 4000));
    }

    @Before
    public void setUp() {
        cfg = ConfigFactory.create(MyConfig.class);
    }

    @Test
    public void itShouldWorkWithoutSeparatorOrTokenizer() {
        assertEquals(expectedServers(), cfg.serversWithoutSeparatorOrTokenizer());
    }

    @Test
    public void shouldWorkWithSeparator() {
        assertEquals(expectedServers(), cfg.serversWithSeparator());
    }

    @Test
    public void shouldWorkWithTokenizer() {
        assertEquals(expectedServers(), cfg.serversWithTokenizer());
    }

    @Test
    public void theConverterReceivesTheRawValueWithoutTokenization() {
        assertEquals(Arrays.asList("[a, b]", "[c, d]"), cfg.pairsNotTokenized());
    }

    @Test
    public void theReturnedCollectionIsTheOneBuiltByTheConverter() {
        Collection<Server> servers = cfg.serversWithoutSeparatorOrTokenizer();
        try {
            servers.clear();
            fail("the converter returned an unmodifiable list, so it should not be modifiable");
        } catch (UnsupportedOperationException expected) {
            // the collection built by the converter is handed back untouched
        }
    }

    public interface NotACollectionConfig extends Config {
        @DefaultValue("a, b")
        @CollectionConverterClass(PairListConverter.class)
        String notACollection();

        @DefaultValue("a, b")
        @CollectionConverterClass(PairListConverter.class)
        String[] anArray();
    }

    @Test
    public void shouldReportAClearErrorOnANonCollectionReturnType() {
        NotACollectionConfig config = ConfigFactory.create(NotACollectionConfig.class);
        try {
            config.notACollection();
            fail("@CollectionConverterClass on a String should be rejected");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("can only be used on a method returning a Collection"));
            assertTrue(e.getMessage(), e.getMessage().contains("notACollection"));
        }
    }

    @Test
    public void shouldReportAClearErrorOnAnArrayReturnType() {
        NotACollectionConfig config = ConfigFactory.create(NotACollectionConfig.class);
        try {
            config.anArray();
            fail("@CollectionConverterClass on an array should be rejected");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("can only be used on a method returning a Collection"));
            assertTrue(e.getMessage(), e.getMessage().contains("anArray"));
        }
    }
}
