/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.util.IsolatedClassLoader;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for the {@link Converters} enum, targeting the conversion branches that are not exercised
 * by the higher level type conversion tests: collections of enums, the {@code EnumSet} error path,
 * and the {@code PRIMITIVE} converter which is only reachable when the JavaBeans property editors
 * are disabled.
 *
 * @author Matteo Baccan
 */
public class ConvertersTest {

    private static final String PROPERTY_EDITOR_DISABLED_PROPERTY = "org.aeonbits.owner.property.editor.disabled";

    enum SampleEnum {
        ALPHA, BETA
    }

    interface EnumCollectionConfig extends Config {
        @DefaultValue("ALPHA, BETA")
        List<SampleEnum> enumList();

        @DefaultValue("BETA")
        EnumSet<SampleEnum> enumSet();

        @DefaultValue("pink, black")
        Set<String> stringSet();
    }

    interface PrimitiveConfig {
        byte aByte();
        short aShort();
        int anInt();
        long aLong();
        boolean aBoolean();
        float aFloat();
        double aDouble();
        Integer anInteger();
    }

    @Test
    public void testListOfEnumsIsConvertedToOrdinaryCollection() {
        EnumCollectionConfig cfg = ConfigFactory.create(EnumCollectionConfig.class);

        List<SampleEnum> result = cfg.enumList();

        assertTrue(result instanceof ArrayList);
        assertEquals(Arrays.asList(SampleEnum.ALPHA, SampleEnum.BETA), result);
    }

    @Test
    public void testEnumSetReturnTypeProducesEnumSet() {
        EnumCollectionConfig cfg = ConfigFactory.create(EnumCollectionConfig.class);

        EnumSet<SampleEnum> result = cfg.enumSet();

        assertEquals(EnumSet.of(SampleEnum.BETA), result);
    }

    @Test
    public void testSetOfStringsIsConvertedToOrdinarySet() {
        EnumCollectionConfig cfg = ConfigFactory.create(EnumCollectionConfig.class);

        Set<String> result = cfg.stringSet();

        assertTrue(result instanceof LinkedHashSet);
        assertEquals(new LinkedHashSet<>(Arrays.asList("pink", "black")), result);
    }

    /**
     * The generics keep a non-enum from reaching this in ordinary use, so the branch is reached the only way
     * it can be. The method is a static of {@link Converters} since reading a list from indexed keys started
     * needing the same choice of collection; it used to live inside the body of the COLLECTION constant.
     */
    @Test
    public void testInstantiateEnumSetFailsWithNonEnumType() throws Exception {
        Method instantiateEnumSet = Converters.class.getDeclaredMethod("instantiateEnumSet", Class.class);
        assertNotNull(instantiateEnumSet);
        instantiateEnumSet.setAccessible(true);

        try {
            instantiateEnumSet.invoke(null, String.class);
            fail("an UnsupportedOperationException was expected");
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof UnsupportedOperationException);
            assertEquals("Cannot instantiate enumset of type 'java.lang.String'", e.getCause().getMessage());
            assertTrue(e.getCause().getCause() instanceof ClassCastException);
        }
    }

    @Test
    public void testPrimitiveConversionsWhenPropertyEditorsAreDisabled() throws Exception {
        // Make sure the regular Converters class is initialized *before* the system property is
        // changed, so the flag cannot leak into the copy used by the rest of the test suite.
        Converters.values();

        String oldValue = System.getProperty(PROPERTY_EDITOR_DISABLED_PROPERTY);
        System.setProperty(PROPERTY_EDITOR_DISABLED_PROPERTY, "true");
        try {
            ClassLoader isolated = new IsolatedClassLoader(Converters.class.getClassLoader());
            Class<?> converters = Class.forName("org.aeonbits.owner.Converters", true, isolated);
            assertNotSame(Converters.class, converters);

            Method convert = converters.getDeclaredMethod(
                    "convert", Method.class, Class.class, String.class, String.class);
            convert.setAccessible(true);

            assertEquals(Byte.valueOf((byte) 1), convert.invoke(null, method("aByte"), Byte.TYPE, "1", "aByte"));
            assertEquals(Short.valueOf((short) 2), convert.invoke(null, method("aShort"), Short.TYPE, "2", "aShort"));
            assertEquals(Integer.valueOf(3), convert.invoke(null, method("anInt"), Integer.TYPE, "3", "anInt"));
            assertEquals(Long.valueOf(4L), convert.invoke(null, method("aLong"), Long.TYPE, "4", "aLong"));
            assertEquals(Boolean.TRUE, convert.invoke(null, method("aBoolean"), Boolean.TYPE, "true", "aBoolean"));
            assertEquals(Float.valueOf(1.5f), convert.invoke(null, method("aFloat"), Float.TYPE, "1.5", "aFloat"));
            assertEquals(Double.valueOf(2.5d), convert.invoke(null, method("aDouble"), Double.TYPE, "2.5", "aDouble"));

            // non primitive types skip the PRIMITIVE converter and fall through to the next one
            assertEquals(Integer.valueOf(42),
                    convert.invoke(null, method("anInteger"), Integer.class, "42", "anInteger"));
        } finally {
            if (oldValue == null)
                System.clearProperty(PROPERTY_EDITOR_DISABLED_PROPERTY);
            else
                System.setProperty(PROPERTY_EDITOR_DISABLED_PROPERTY, oldValue);
        }
    }

    /**
     * On exotic runtimes (e.g. some compact profiles) the JavaBeans property editors may not be
     * on the classpath at all: conversions must keep working through the remaining converters.
     * The scenario is simulated by hiding java.beans.PropertyEditorManager from the class loader.
     */
    @Test
    public void testConversionsKeepWorkingWhenPropertyEditorsAreNotAvailable() throws Exception {
        ClassLoader isolated = new IsolatedClassLoader(
                Converters.class.getClassLoader(), "java.beans.PropertyEditorManager");
        Class<?> converters = Class.forName("org.aeonbits.owner.Converters", true, isolated);
        assertNotSame(Converters.class, converters);

        Method convert = converters.getDeclaredMethod(
                "convert", Method.class, Class.class, String.class, String.class);
        convert.setAccessible(true);

        // primitives are handled by the PRIMITIVE converter
        assertEquals(Integer.valueOf(3), convert.invoke(null, method("anInt"), Integer.TYPE, "3", "anInt"));
        // wrappers fall through to the CLASS_WITH_STRING_CONSTRUCTOR converter
        assertEquals(Integer.valueOf(42), convert.invoke(null, method("anInteger"), Integer.class, "42", "anInteger"));
    }

    /**
     * Without the property editors the parsing falls to the PRIMITIVE converter, whose
     * {@code NumberFormatException} says nothing about where the bad value came from. It has to fail the
     * way the editor path fails, naming the value, the type and the key.
     */
    @Test
    public void testUnparsablePrimitiveNamesThePropertyWhenPropertyEditorsAreNotAvailable() throws Exception {
        ClassLoader isolated = new IsolatedClassLoader(
                Converters.class.getClassLoader(), "java.beans.PropertyEditorManager");
        Class<?> converters = Class.forName("org.aeonbits.owner.Converters", true, isolated);

        Method convert = converters.getDeclaredMethod(
                "convert", Method.class, Class.class, String.class, String.class);
        convert.setAccessible(true);

        try {
            convert.invoke(null, method("anInt"), Integer.TYPE, "abc", "server.port");
            fail("an UnsupportedOperationException was expected");
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof UnsupportedOperationException);
            assertEquals("Cannot convert 'abc' to int for property 'server.port'", e.getCause().getMessage());
            assertTrue(e.getCause().getCause() instanceof NumberFormatException);
        }
    }

    private static Method method(String name) throws NoSuchMethodException {
        return PrimitiveConfig.class.getMethod(name);
    }

}
