/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.typeconversion.collections;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author Dmytro Chyzhykov
 */
public class CollectionSupportTest {
    private CollectionConfig cfg;

    public interface CollectionConfig extends Config {
        String COLORS = "pink,black";
        String INTEGERS = "1, 2, 3";

        @DefaultValue(COLORS)
        Collection<String> colors();

        @DefaultValue(COLORS)
        Set<String> colorSet();

        @DefaultValue(INTEGERS)
        SortedSet<Integer> integerSortedSet();

        @DefaultValue(INTEGERS)
        List<Integer> integerList();

        @DefaultValue(COLORS)
        LinkedHashSet<String> colorLinkedHashSet();

        @DefaultValue(INTEGERS)
        LinkedList<Integer> integerLinkedList();

        @DefaultValue(INTEGERS)
        Collection rawCollection();

        @DefaultValue(INTEGERS)
        CollectionWithoutDefaultConstructor<Integer> badCollection();
    }

    static class CollectionWithoutDefaultConstructor<E> extends ArrayList<E> {
        public CollectionWithoutDefaultConstructor(int size) {
            super(size);
        }
    }

    @Before
    public void setUp() throws Exception {
        cfg = ConfigFactory.create(CollectionConfig.class);
    }

    @Test
    public void itShouldReadCollectionOfStrings() throws Exception {
        assertThat(cfg.colors(), containsInAnyOrder("pink", "black"));
    }

    @Test
    public void itShouldReadSetOfStrings() throws Exception {
        assertThat(cfg.colorSet(), containsInAnyOrder("pink", "black"));
    }

    @Test
    public void itShouldReadSortedSetOfIntegers() throws Exception {
        assertThat(cfg.integerSortedSet(), contains(1, 2, 3));
    }

    @Test
    public void itShouldReadListOfIntegers() throws Exception {
        assertThat(cfg.integerList(), contains(1, 2, 3));
    }

    @Test
    public void itShouldReadConcreteSetImplementation() throws Exception {
        assertThat(cfg.colorLinkedHashSet(), instanceOf(LinkedHashSet.class));
        assertThat(cfg.colorLinkedHashSet(), contains("pink", "black"));
    }

    @Test
    public void itShouldReadConcreteListImplementation() throws Exception {
        assertThat(cfg.integerLinkedList(), instanceOf(LinkedList.class));
        assertThat(cfg.integerLinkedList(), contains(1, 2, 3));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void itShouldThrowExceptionWithCollectionWithoutDefaultConstructor() throws Exception {
        cfg.badCollection();
    }

    @Test
    public void itShouldWorkWithRawCollectionAsWithCollectionOfStrings() throws Exception {
        assertEquals(Arrays.asList("1", "2", "3"), cfg.rawCollection());
    }

}

