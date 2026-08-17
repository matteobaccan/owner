/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Where a class-level annotation is looked for, for the annotations that describe the <b>configuration
 * object</b> rather than the methods of one interface.
 * <p>
 * A configuration object is not one interface: it is the interface handed to the
 * {@link ConfigFactory} together with everything that one extends, and a statement about the object as a
 * whole - which sources it reads, with which policy, reloaded how, decrypted by whom, described how, with
 * which features switched off - counts wherever in that hierarchy it is written. That is what this class
 * walks. It says nothing about the other family of class-level annotations, the ones that govern the
 * methods an interface <b>declares</b> - {@link Config.Prefix}, {@link Config.Mandatory},
 * {@link Config.Sensitive}, {@link Config.Separator}, {@link Config.TokenizerClass},
 * {@link Config.PreprocessorClasses} - which are read off {@link java.lang.reflect.Method#getDeclaringClass()}
 * and neither climb nor descend: an interface governs what it declares, so a sub-interface cannot change the
 * meaning of the keys its parent declared, and a parent cannot reach into methods declared below it.
 * </p>
 * <p>
 * Java itself is no help here. {@link java.lang.annotation.Inherited} applies to classes extending classes
 * and does nothing for an interface, so every lookup of this kind has to be written out - and until 2.0.0
 * each was written out separately, which is how they came to disagree: three stopped at the direct
 * super-interfaces, three read the interface handed to the factory and nothing above it, and
 * {@link Config.Prefix} counted at any depth.
 * </p>
 *
 * @author Matteo Baccan
 */
final class Annotations {

    private Annotations() {
    }

    /**
     * The interfaces a class-level annotation is looked for on: the given one, then the interfaces it
     * extends, then the ones those extend, breadth first and each of them visited once.
     * <p>
     * Breadth first is what makes the order mean something. A super-interface is asked before that
     * super-interface's own parents, and two interfaces extended by the same one are asked in the order of
     * the {@code extends} clause, so "the nearest declaration wins" holds and a hierarchy one level deep
     * behaves exactly as it did when the lookup stopped there. The set also keeps the diamond honest: an
     * interface reached by two paths is visited once, and so contributes what it declares once.
     * </p>
     *
     * @param clazz the interface to start from.
     * @return that interface and every one above it, nearest first.
     */
    static Collection<Class<?>> hierarchyOf(Class<?> clazz) {
        Collection<Class<?>> visited = new LinkedHashSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>();
        pending.add(clazz);
        while (!pending.isEmpty()) {
            Class<?> type = pending.poll();
            if (visited.add(type))
                Collections.addAll(pending, type.getInterfaces());
        }
        return visited;
    }

    /**
     * The nearest declaration of an annotation that describes a <b>single</b> setting of the configuration
     * object, such as {@link Config.LoadPolicy} or {@link Config.DecryptorClass}.
     *
     * @param clazz          the interface handed to the factory.
     * @param annotationType the annotation to look for.
     * @param <T>            the type of the annotation.
     * @return the nearest declaration of it, or <code>null</code> when nobody in the hierarchy declares it.
     */
    static <T extends Annotation> T findAnnotation(Class<?> clazz, Class<T> annotationType) {
        for (Class<?> type : hierarchyOf(clazz)) {
            T annotation = type.getAnnotation(annotationType);
            if (annotation != null)
                return annotation;
        }
        return null;
    }

    /**
     * The same walk for an annotation that describes a <b>set</b> and therefore accumulates rather than
     * being won - {@link Config.Sources} is the only one - keyed by the interface that carries it, in the
     * order the interfaces are visited. The key is there for the diagnostics, which name the interface a
     * source came from when it is not the one handed to the factory.
     *
     * @param clazz          the interface handed to the factory.
     * @param annotationType the annotation to look for.
     * @param <T>            the type of the annotation.
     * @return every declaration of it in the hierarchy, nearest first; empty when there is none.
     */
    static <T extends Annotation> Map<Class<?>, T> findAnnotations(Class<?> clazz, Class<T> annotationType) {
        Map<Class<?>, T> result = new LinkedHashMap<>();
        for (Class<?> type : hierarchyOf(clazz)) {
            T annotation = type.getAnnotation(annotationType);
            if (annotation != null)
                result.put(type, annotation);
        }
        return result;
    }
}
