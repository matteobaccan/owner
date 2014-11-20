/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.LoadType;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.TestConstants;
import org.junit.Test;

import java.lang.annotation.Annotation;

import static org.junit.Assert.*;

/**
 * @author Luigi R. Viggiano
 */
public class ReflectionTest {

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


    private static final String PARENT_SPEC = "file:" + TestConstants.RESOURCES_DIR + "/parent.properties";
    private static final String CHILD_SPEC = "file:" + TestConstants.RESOURCES_DIR + "/child.properties";

    @Sources(PARENT_SPEC)
    @LoadPolicy(LoadType.MERGE)
    @HotReload(value=1)
    interface ParentConfig extends Config {}

    @Sources(CHILD_SPEC)
    @LoadPolicy(LoadType.FIRST)
    @HotReload(value=2)
    interface ChildConfig extends ParentConfig {}

    interface NonAnnotatedConfig extends Config {}

    interface NonAnnotatedChildConfig extends ParentConfig {}

    @Test
    public void testGetAnnotationWithNonExistentAnnotation() {
        assertNull(Reflection.getAnnotation(ParentConfig.class, Annotation.class));
        assertNull(Reflection.getAnnotation(ChildConfig.class, Annotation.class));
    }

    @Test
    public void testGetAnnotationWithParentConfig() {
        assertEquals(PARENT_SPEC, (Reflection.getAnnotation(ParentConfig.class, Sources.class)).value()[0]);
        assertEquals(LoadType.MERGE, (Reflection.getAnnotation(ParentConfig.class, LoadPolicy.class)).value());
        assertEquals(1, (Reflection.getAnnotation(ParentConfig.class, HotReload.class)).value());
    }

    @Test
    public void testGetAnnotationWithChildConfig() {
        assertEquals(CHILD_SPEC, (Reflection.getAnnotation(ChildConfig.class, Sources.class)).value()[0]);
        assertEquals(LoadType.FIRST, (Reflection.getAnnotation(ChildConfig.class, LoadPolicy.class)).value());
        assertEquals(2, (Reflection.getAnnotation(ChildConfig.class, HotReload.class)).value());
    }

    @Test
    public void testGetAnnotationWithNonAnnotatedConfig() {
        assertNull(Reflection.getAnnotation(NonAnnotatedConfig.class, Sources.class));
        assertNull(Reflection.getAnnotation(NonAnnotatedConfig.class, LoadPolicy.class));
        assertNull(Reflection.getAnnotation(NonAnnotatedConfig.class, HotReload.class));
    }

    @Test
    public void testGetAnnotationWithNonAnnotatedChildConfig() {
        assertEquals(PARENT_SPEC, (Reflection.getAnnotation(NonAnnotatedChildConfig.class, Sources.class)).value()[0]);
        assertEquals(LoadType.MERGE, (Reflection.getAnnotation(NonAnnotatedChildConfig.class, LoadPolicy.class)).value());
        assertEquals(1, (Reflection.getAnnotation(NonAnnotatedChildConfig.class, HotReload.class)).value());
    }

}
