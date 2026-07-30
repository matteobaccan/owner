/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * @author Luigi R. Viggiano
 */
public class EqualsAndHashCodeTest {

    interface MyConfig extends Config {
        @DefaultValue("foo")
        String foo();
        @DefaultValue("bar")
        String bar();
    }

    interface MySubConfig extends MyConfig {
    }

    interface UnrelatedConfig extends Config {
        @DefaultValue("foo")
        String foo();
        @DefaultValue("bar")
        String bar();
    }


    @Test
    public void testWhenTwoObjectsAreEqual() {
        MyConfig cfg1 = ConfigFactory.create(MyConfig.class);
        MyConfig cfg2 = ConfigFactory.create(MyConfig.class);

        assertEquals(cfg1, cfg2);
        assertEquals(cfg2, cfg1);
        assertEquals(cfg1.hashCode(), cfg2.hashCode());
    }

    @Test
    public void testWhenTwoObjectsAreNotEqual() {
        MyConfig cfg1 = ConfigFactory.create(MyConfig.class);
        MyConfig cfg2 = ConfigFactory.create(MyConfig.class, new Properties() {{
            setProperty("bar", "baz");
        }});

        assertNotEquals(cfg1, cfg2);
        assertNotEquals(cfg2, cfg1);
        assertNotEquals(cfg1.hashCode(), cfg2.hashCode());
    }

    @Test
    public void testWhenObjectsAreNotRelated() {
        MyConfig cfg1 = ConfigFactory.create(MyConfig.class);
        Object unrelated = new Object();

        assertNotEquals(cfg1, unrelated);
        assertNotEquals(unrelated, cfg1);
    }

    @Test
    public void testWhenConfigsAreNotRelated() {
        MyConfig cfg1 = ConfigFactory.create(MyConfig.class);
        UnrelatedConfig cfg2 = ConfigFactory.create(UnrelatedConfig.class);

        assertNotEquals(cfg1, cfg2);
        assertNotEquals(cfg2, cfg1);

        // hashCodes equality doesn't imply objects equality
        assertEquals(cfg1.hashCode(), cfg2.hashCode());
    }

    @Test
    public void testWhenTwoObjectsAreSimilarProxies() {
        MyConfig cfg1 = ConfigFactory.create(MyConfig.class);
        MyConfig cfg2 = (MyConfig) Proxy
                .newProxyInstance(getClass().getClassLoader(), new Class[] { MyConfig.class }, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                return null;
            }
        });

        assertNotEquals(cfg1, cfg2);
    }

    @Test
    public void testWhenObjectIsSubclass() {
        MyConfig cfg1 = ConfigFactory.create(MyConfig.class);
        MySubConfig cfg2 = ConfigFactory.create(MySubConfig.class);

        assertEquals(cfg1, cfg2);
        assertEquals(cfg2, cfg1);
        assertEquals(cfg1.hashCode(), cfg2.hashCode());
    }

}
