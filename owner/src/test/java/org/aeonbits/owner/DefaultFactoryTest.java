/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;

import javax.management.DynamicMBean;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests the JMX related behavior of {@link DefaultFactory}: when JMX is available on the classpath the created
 * proxy also implements {@link DynamicMBean} and gets a {@link JMXSupport} delegate; when JMX is not available
 * the proxy implements the config interface only and no JMX support object is created.
 * <p>
 * Since <code>javax.management</code> is part of the JDK, the "JMX not available" scenario is reproduced by
 * reloading the <code>org.aeonbits.owner</code> classes in a child-first classloader which pretends that
 * {@link DynamicMBean} cannot be loaded, so that <code>DefaultFactory.JMX_AVAILABLE</code> is initialized to
 * <code>false</code> in that isolated world.
 * </p>
 *
 * @author Matteo Baccan
 */
public class DefaultFactoryTest {

    /**
     * Simple config interface, used both from the regular classloader and reloaded by
     * {@link JmxHidingClassLoader}. It intentionally has no annotations, so it behaves the same
     * in both worlds.
     */
    public interface MyConfig extends Config {
        String someValue();
    }

    @Test
    public void testCreateWhenJmxIsAvailable() {
        DefaultFactory factory = new DefaultFactory(null, new Properties());

        MyConfig cfg = factory.create(MyConfig.class);

        assertTrue(cfg instanceof DynamicMBean);
        Class<?>[] interfaces = cfg.getClass().getInterfaces();
        assertEquals(2, interfaces.length);
        assertEquals(MyConfig.class, interfaces[0]);
        assertEquals(DynamicMBean.class, interfaces[1]);
    }

    @Test
    public void testJmxSupportIsCreatedWhenJmxIsAvailable() throws Exception {
        DefaultFactory factory = new DefaultFactory(null, new Properties());

        MyConfig cfg = factory.create(MyConfig.class);

        assertTrue(jmxSupportOf(cfg) instanceof JMXSupport);
        assertNull(cfg.someValue()); // no sources and no default value: the property resolves to null
    }

    @Test
    public void testJmxIsDetectedAsUnavailableWhenDynamicMBeanCannotBeLoaded() throws Exception {
        Class<?> factoryClass = new JmxHidingClassLoader().loadClass(DefaultFactory.class.getName());

        Field jmxAvailable = factoryClass.getDeclaredField("JMX_AVAILABLE");
        jmxAvailable.setAccessible(true);

        assertFalse((Boolean) jmxAvailable.get(null));
    }

    @Test
    public void testCreateWhenJmxIsNotAvailable() throws Exception {
        ClassLoader loader = new JmxHidingClassLoader();
        Class<?> factoryClass = loader.loadClass(DefaultFactory.class.getName());
        Class<?> configClass = loader.loadClass(MyConfig.class.getName());

        Constructor<?> constructor =
                factoryClass.getDeclaredConstructor(ScheduledExecutorService.class, Properties.class);
        constructor.setAccessible(true);
        Object factory = constructor.newInstance(null, new Properties());

        Method create = factoryClass.getMethod("create", Class.class, Map[].class);
        create.setAccessible(true);
        Object proxy = create.invoke(factory, configClass, new Map<?, ?>[0]);

        assertNotNull(proxy);
        assertTrue(configClass.isInstance(proxy));
        assertFalse(proxy instanceof DynamicMBean);
        Class<?>[] interfaces = proxy.getClass().getInterfaces();
        assertEquals(1, interfaces.length);
        assertEquals(configClass, interfaces[0]);
        assertNull(jmxSupportOf(proxy));
    }

    private static Object jmxSupportOf(Object proxy) throws Exception {
        InvocationHandler handler = Proxy.getInvocationHandler(proxy);
        Field jmxSupport = handler.getClass().getDeclaredField("jmxSupport");
        jmxSupport.setAccessible(true);
        return jmxSupport.get(handler);
    }

    /**
     * A child-first classloader that hides <code>javax.management.DynamicMBean</code> and redefines every
     * <code>org.aeonbits.owner</code> class from the original class files. In the world defined by this loader
     * {@link org.aeonbits.owner.util.Reflection#isClassAvailable(String)} cannot load {@link DynamicMBean},
     * hence <code>DefaultFactory</code> initializes its <code>JMX_AVAILABLE</code> flag to <code>false</code>.
     */
    static class JmxHidingClassLoader extends ClassLoader {

        JmxHidingClassLoader() {
            super(DefaultFactoryTest.class.getClassLoader());
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals("javax.management.DynamicMBean"))
                throw new ClassNotFoundException(name + " is hidden by " + JmxHidingClassLoader.class.getName());
            if (name.startsWith("org.aeonbits.owner.")) {
                Class<?> result = findLoadedClass(name);
                if (result == null)
                    result = defineFromParentResource(name);
                if (resolve)
                    resolveClass(result);
                return result;
            }
            return super.loadClass(name, resolve);
        }

        private Class<?> defineFromParentResource(String name) throws ClassNotFoundException {
            InputStream input = getParent().getResourceAsStream(name.replace('.', '/') + ".class");
            if (input == null)
                throw new ClassNotFoundException(name);
            try {
                byte[] bytecode = readFully(input);
                // a non-null ProtectionDomain gives the class a code location, so JaCoCo instruments it
                // and the coverage recorded in this classloader is merged into the main report
                return defineClass(name, bytecode, 0, bytecode.length,
                        DefaultFactoryTest.class.getProtectionDomain());
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            } finally {
                try {
                    input.close();
                } catch (IOException ignored) {
                    // nothing to do here
                }
            }
        }

        private static byte[] readFully(InputStream input) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1)
                output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

}
