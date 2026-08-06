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
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * See: https://github.com/lviggiano/owner/issues/146
 * <p>
 * A {@code @ConverterClass} returning a collection was losing to OWNER's own collection conversion. It still
 * does, deliberately — {@code @ConverterClass} converts one element at a time, and a converter returning the
 * whole collection has nothing to do with the element the tokenizer just produced — but the failure used to be
 * an {@code IllegalArgumentException: array element type mismatch} from deep inside the reflection call, which
 * said nothing about what to change. It now names the method and points at {@code @CollectionConverterClass},
 * added in this release for exactly this.
 */
public class Issue146Test {

    public interface MyList extends List<String> {
        void foo();
    }

    public static class MyArrayList extends ArrayList<String> implements MyList {
        @Override
        public void foo() {
            // the extra method that makes the type worth having
        }
    }

    public static class MyListConverter implements Converter<MyList> {
        @Override
        public MyList convert(Method method, String input) {
            MyArrayList result = new MyArrayList();
            for (String chunk : input.split(",", -1))
                result.add(chunk.trim());
            return result;
        }
    }

    /** The gist attached to the issue, with the element type spelled out. */
    public interface WithConverterClass extends Config {
        @DefaultValue("1, 2, 3, 4, 5, 6, 7")
        List<Integer> list();

        @ConverterClass(MyListConverter.class)
        @DefaultValue("1, 2, 3, 4, 5, 6, 7")
        MyList myList();
    }

    @Test
    public void theOrdinaryCollectionConversionIsUnaffected() {
        assertEquals(7, ConfigFactory.create(WithConverterClass.class).list().size());
    }

    @Test
    public void aConverterClassReturningTheWholeCollectionIsReported() {
        try {
            ConfigFactory.create(WithConverterClass.class).myList();
            fail("UnsupportedOperationException is expected");
        } catch (UnsupportedOperationException e) {
            String message = e.getMessage();
            assertTrue("unexpected message: " + message,
                    message.contains("The @ConverterClass of 'myList' returned a"));
            assertTrue("unexpected message: " + message,
                    message.contains("converts one element at a time"));
            assertTrue("unexpected message: " + message,
                    message.contains("Use @CollectionConverterClass"));
        }
    }

    public interface WithCollectionConverterClass extends Config {
        @CollectionConverterClass(MyListConverter.class)
        @DefaultValue("1, 2, 3, 4, 5, 6, 7")
        MyList myList();
    }

    @Test
    public void theCollectionConverterClassIsTheAnswer() {
        MyList myList = ConfigFactory.create(WithCollectionConverterClass.class).myList();

        assertEquals(MyArrayList.class, myList.getClass());
        assertEquals(7, myList.size());
        assertEquals("1", myList.get(0));
        assertEquals("7", myList.get(6));
    }
}
