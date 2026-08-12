/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A collection whose elements are themselves collections is refused, rather than ending in a
 * {@link StackOverflowError} as it did until 2.0.0.
 *
 * <p>
 * The recursion was real and had no smaller case to reach: {@link Converters#COLLECTION} reads the element
 * type off the <em>method</em>, so with an element type that is another collection it handed
 * {@link Converters#ARRAY} an array of collections, whose component sent the same method back. Three shapes
 * reached it — <code>List&lt;List&lt;String&gt;&gt;</code>, <code>List&lt;String&gt;[]</code>, and either of
 * them carrying a {@link Config.ConverterClass}, which never even got to be called.
 * </p>
 * <p>
 * The refusal is not a limitation waiting to be lifted: a property value is tokenized once, by one
 * separator, so it has no second level for the inner collections to be told apart by. The value that does
 * have a shape of its own is read with {@link Config.CollectionConverterClass}, which is consulted before
 * this link and is therefore untouched by the guard — the last test here is what pins that down.
 * </p>
 *
 * @author Matteo Baccan
 */
public class NestedCollectionsTest {

    public interface ArrayOfLists extends Config {
        @DefaultValue("a,b")
        List<String>[] value();
    }

    public interface ListOfLists extends Config {
        @DefaultValue("a,b")
        List<List<String>> value();
    }

    public interface ListOfListsWithElementConverter extends Config {
        @DefaultValue("a|b,c|d")
        @ConverterClass(OneListPerElement.class)
        List<List<String>> value();
    }

    public interface ListOfListsWithCollectionConverter extends Config {
        @DefaultValue("a|b,c|d")
        @CollectionConverterClass(TheWholeListOfLists.class)
        List<List<String>> value();
    }

    public interface ListOfArrays extends Config {
        @DefaultValue("a,b")
        List<String[]> value();
    }

    public interface ArrayOfArrays extends Config {
        @DefaultValue("a,b")
        String[][] value();
    }

    public static class OneListPerElement implements Converter<List<String>> {
        @Override
        public List<String> convert(Method method, String input) {
            return asList(input.split("\\|"));
        }
    }

    public static class TheWholeListOfLists implements Converter<List<List<String>>> {
        @Override
        public List<List<String>> convert(Method method, String input) {
            List<List<String>> result = new ArrayList<List<String>>();
            for (String chunk : input.split(","))
                result.add(asList(chunk.split("\\|")));
            return result;
        }
    }

    @Test
    public void anArrayOfListsIsRefusedInsteadOfOverflowingTheStack() {
        assertRefused(ArrayOfLists.class);
    }

    @Test
    public void aListOfListsIsRefusedInsteadOfOverflowingTheStack() {
        assertRefused(ListOfLists.class);
    }

    /**
     * The annotation converts one element at a time and an element here is a list, so this looks like the
     * way to ask for it — but the recursion happened before the converter was ever reached, and the message
     * has to send the reader to the annotation that does work rather than leave them with a refusal of the
     * thing they just asked for.
     */
    @Test
    public void anElementConverterDoesNotMakeTheShapeReadable() {
        UnsupportedOperationException refused = assertRefused(ListOfListsWithElementConverter.class);
        assertTrue(refused.getMessage(), refused.getMessage().contains("@CollectionConverterClass"));
    }

    @Test
    public void theRefusalNamesTheMethodAndTheTypeItReturns() {
        UnsupportedOperationException refused = assertRefused(ListOfLists.class);
        assertTrue(refused.getMessage(), refused.getMessage().contains("'value'"));
        assertTrue(refused.getMessage(), refused.getMessage().contains("java.util.List<java.util.List<java.lang.String>>"));
    }

    /**
     * The guard is on the element type being a collection and on nothing else, so an element that is an
     * array still converts. It reads oddly — every element is the whole value tokenized again — but it
     * terminates, it is what this library did before the guard, and changing it is a separate decision.
     */
    @Test
    public void anElementThatIsAnArrayIsNotRefused() {
        List<String[]> value = ConfigFactory.create(ListOfArrays.class).value();
        assertEquals(2, value.size());
    }

    @Test
    public void anArrayOfArraysIsNotRefused() {
        String[][] value = ConfigFactory.create(ArrayOfArrays.class).value();
        assertEquals("[[a], [b]]", Arrays.deepToString(value));
    }

    /**
     * The shape is readable when the value has a syntax of its own and something is told how to read it,
     * which is the whole reason {@link Config.CollectionConverterClass} exists. It is consulted first, so
     * the guard never sees this one.
     */
    @Test
    public void theWholeValueMayStillBeConvertedIntoAListOfLists() {
        List<List<String>> value = ConfigFactory.create(ListOfListsWithCollectionConverter.class).value();
        assertEquals(asList(asList("a", "b"), asList("c", "d")), value);
    }

    private UnsupportedOperationException assertRefused(Class<? extends Config> type) {
        Config config = ConfigFactory.create(type);
        try {
            type.getMethod("value").invoke(config);
            fail("a collection of collections was expected to be refused");
            return null;
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (!(cause instanceof UnsupportedOperationException))
                throw new AssertionError("expected a refusal, got " + cause, cause);
            return (UnsupportedOperationException) cause;
        }
    }
}
