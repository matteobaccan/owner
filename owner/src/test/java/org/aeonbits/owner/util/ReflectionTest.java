/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.annotation.Annotation;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.LoadType;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.TestConstants;
import org.junit.Test;

/**
 * @author Luigi R. Viggiano
 */
public class ReflectionTest {

    private static final String PARENT_SPEC = "file:" + TestConstants.RESOURCES_DIR + "/parent.properties";
    private static final String CHILD_SPEC = "file:" + TestConstants.RESOURCES_DIR + "/child.properties";
    
    /**
     * Sample test interfaces extending Config class to verify following use cases:
     * 1) parent class extending Config with annotations
     * 2) parent extending Config without annotation
     * 3) child class extending parent class with its own annotations
     * 4) child class extending parent class without annotation
     */
    @Sources(PARENT_SPEC)
    @LoadPolicy(LoadType.MERGE)
    @HotReload(value=1)
    interface parentClazz extends Config {}
    
    @Sources(CHILD_SPEC)
    @LoadPolicy(LoadType.FIRST)
    @HotReload(value=2)
    interface childClazz extends parentClazz {}

    interface noAnnotationClazz extends Config {}

    interface noAnnotationChildClazz extends parentClazz {}

    @Test
    public void testAvailableWithNonExistentClass() {
        boolean available = Reflection.isClassAvailable("foo.bar.baz.FooBar");
        assertFalse(available);
    }

    @Test
    public void testAvailableWithExistentClass() {
        boolean available = Reflection.isClassAvailable("java.lang.String");
        assertTrue(available);
    }
    
    @Test
    public void testGetAnnotationWithNonExistentAnnotation() {
        assertNull(Reflection.getAnnotation(parentClazz.class, Annotation.class));
    }

    @Test
    public void testGetAnnotationWithParentClass() {
        assertEquals(PARENT_SPEC, ((Sources)Reflection.getAnnotation(parentClazz.class, Sources.class)).value()[0]);
        assertEquals(LoadType.MERGE, ((LoadPolicy)Reflection.getAnnotation(parentClazz.class, LoadPolicy.class)).value());
        assertEquals(1, ((HotReload)Reflection.getAnnotation(parentClazz.class, HotReload.class)).value());
    }

    @Test
    public void testGetAnnotationWithChildClass() {
        assertEquals(CHILD_SPEC, ((Sources)Reflection.getAnnotation(childClazz.class, Sources.class)).value()[0]);
        assertEquals(LoadType.FIRST, ((LoadPolicy)Reflection.getAnnotation(childClazz.class, LoadPolicy.class)).value());
        assertEquals(2, ((HotReload)Reflection.getAnnotation(childClazz.class, HotReload.class)).value());
    }

    @Test
    public void testGetAnnotationWithNoAnnotationClass() {
        assertNull(Reflection.getAnnotation(noAnnotationClazz.class, Sources.class));
        assertNull(Reflection.getAnnotation(noAnnotationClazz.class, LoadPolicy.class));
        assertNull(Reflection.getAnnotation(noAnnotationClazz.class, HotReload.class));
    }

    @Test
    public void testGetAnnotationWithNoAnnotationChildClass() {
        assertEquals(PARENT_SPEC, ((Sources)Reflection.getAnnotation(noAnnotationChildClazz.class, Sources.class)).value()[0]);
        assertEquals(LoadType.MERGE, ((LoadPolicy)Reflection.getAnnotation(noAnnotationChildClazz.class, LoadPolicy.class)).value());
        assertEquals(1, ((HotReload)Reflection.getAnnotation(noAnnotationChildClazz.class, HotReload.class)).value());
    }

}
