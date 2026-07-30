/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
        assertEquals(new LinkedHashSet<String>(Arrays.asList("pink", "black")), result);
    }

    @Test
    public void testInstantiateEnumSetFailsWithNonEnumType() throws Exception {
        Method instantiateEnumSet = null;
        for (Method method : Converters.COLLECTION.getClass().getDeclaredMethods())
            if ("instantiateEnumSet".equals(method.getName()))
                instantiateEnumSet = method;
        assertNotNull(instantiateEnumSet);
        instantiateEnumSet.setAccessible(true);

        try {
            instantiateEnumSet.invoke(Converters.COLLECTION, String.class);
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

            Method convert = converters.getDeclaredMethod("convert", Method.class, Class.class, String.class);
            convert.setAccessible(true);

            assertEquals(Byte.valueOf((byte) 1), convert.invoke(null, method("aByte"), Byte.TYPE, "1"));
            assertEquals(Short.valueOf((short) 2), convert.invoke(null, method("aShort"), Short.TYPE, "2"));
            assertEquals(Integer.valueOf(3), convert.invoke(null, method("anInt"), Integer.TYPE, "3"));
            assertEquals(Long.valueOf(4L), convert.invoke(null, method("aLong"), Long.TYPE, "4"));
            assertEquals(Boolean.TRUE, convert.invoke(null, method("aBoolean"), Boolean.TYPE, "true"));
            assertEquals(Float.valueOf(1.5f), convert.invoke(null, method("aFloat"), Float.TYPE, "1.5"));
            assertEquals(Double.valueOf(2.5d), convert.invoke(null, method("aDouble"), Double.TYPE, "2.5"));

            // non primitive types skip the PRIMITIVE converter and fall through to the next one
            assertEquals(Integer.valueOf(42), convert.invoke(null, method("anInteger"), Integer.class, "42"));
        } finally {
            if (oldValue == null)
                System.clearProperty(PROPERTY_EDITOR_DISABLED_PROPERTY);
            else
                System.setProperty(PROPERTY_EDITOR_DISABLED_PROPERTY, oldValue);
        }
    }

    private static Method method(String name) throws NoSuchMethodException {
        return PrimitiveConfig.class.getMethod(name);
    }

    /**
     * A class loader that reloads the org.aeonbits.owner classes from their class files, so that
     * the {@link Converters} static state can be re-initialized while the property editor disabling
     * flag is set, without affecting the copy of the class used by the rest of the test suite.
     */
    private static class IsolatedClassLoader extends ClassLoader {

        IsolatedClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.aeonbits.owner.")) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null)
                        loaded = defineFromResource(name);
                    if (resolve)
                        resolveClass(loaded);
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }

        private Class<?> defineFromResource(String name) throws ClassNotFoundException {
            String path = name.replace('.', '/') + ".class";
            InputStream in = getParent().getResourceAsStream(path);
            if (in == null)
                throw new ClassNotFoundException(name);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1)
                    out.write(buffer, 0, count);
                byte[] bytes = out.toByteArray();
                // a non-null ProtectionDomain gives the class a code location, so JaCoCo instruments it
                // and the coverage recorded in this classloader is merged into the main report
                return defineClass(name, bytes, 0, bytes.length, ConvertersTest.class.getProtectionDomain());
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // nothing to do
                }
            }
        }
    }

}
