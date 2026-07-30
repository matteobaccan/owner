/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.PreprocessorClasses;
import org.junit.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Matteo Baccan
 */
public class PreprocessorResolverTest {

    // support classes

    public static class Upper implements Preprocessor {
        public String process(String input) {
            return input.toUpperCase();
        }
    }

    public static class Lower implements Preprocessor {
        public String process(String input) {
            return input.toLowerCase();
        }
    }

    @PreprocessorClasses(Lower.class)
    interface AnnotatedConfig extends Config {
        @PreprocessorClasses(Upper.class)
        String annotatedProperty();

        String plainProperty();
    }

    interface PlainConfig extends Config {
        String plainProperty();
    }

    /**
     * A hand-made {@link PreprocessorClasses} implementation returning <code>null</code> from {@link #value()}: real
     * annotations can never do that, since the JVM guarantees non-null results for annotation members.
     */
    private static class NullValuePreprocessorClasses implements PreprocessorClasses {
        public Class<? extends Preprocessor>[] value() {
            return null;
        }

        public Class<? extends Annotation> annotationType() {
            return PreprocessorClasses.class;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Preprocessor> getPreprocessor(PreprocessorClasses annotation) throws Exception {
        Method method = PreprocessorResolver.class.getDeclaredMethod("getPreprocessor", PreprocessorClasses.class);
        method.setAccessible(true);
        return (List<Preprocessor>) method.invoke(null, annotation);
    }

    @Test
    public void testReturnsEmptyListWhenAnnotationValueIsNull() throws Exception {
        List<Preprocessor> result = getPreprocessor(new NullValuePreprocessorClasses());
        assertTrue(result.isEmpty());
    }

    @Test
    public void testResolvesMethodPreprocessorsBeforeClassPreprocessors() throws Exception {
        Method method = AnnotatedConfig.class.getMethod("annotatedProperty");
        List<Preprocessor> result = PreprocessorResolver.resolvePreprocessors(method);
        assertEquals(2, result.size());
        assertTrue(result.get(0) instanceof Upper);
        assertTrue(result.get(1) instanceof Lower);
    }

    @Test
    public void testResolvesClassPreprocessorsWhenMethodIsNotAnnotated() throws Exception {
        Method method = AnnotatedConfig.class.getMethod("plainProperty");
        List<Preprocessor> result = PreprocessorResolver.resolvePreprocessors(method);
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof Lower);
    }

    @Test
    public void testResolvesNoPreprocessorsWhenNothingIsAnnotated() throws Exception {
        Method method = PlainConfig.class.getMethod("plainProperty");
        List<Preprocessor> result = PreprocessorResolver.resolvePreprocessors(method);
        assertTrue(result.isEmpty());
    }

}
