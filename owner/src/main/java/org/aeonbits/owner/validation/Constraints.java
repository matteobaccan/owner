/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.validation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Method;

/**
 * Recognises a Bean Validation constraint <b>by name</b>, which is how the core can know that a
 * configuration carries constraints while depending on no validation API at all.
 *
 * <p>
 * An annotation is a constraint when it is itself annotated with <code>javax.validation.Constraint</code> or
 * <code>jakarta.validation.Constraint</code> - which is what the specification requires of every constraint,
 * the built-in ones, the composed ones and the ones written by hand alike. Nothing here loads either class:
 * the meta-annotations are already there to be read, and only their names are compared. A class path without
 * a validation API has no constraint annotations on it either, since they would not resolve, and this
 * answers <code>false</code> for everything.
 * </p>
 *
 * <p>
 * <b>Why the core cares at all.</b> It cannot check a constraint, and it does not try. It has to be able to
 * see one, because a <code>&#64;Min(12)</code> that nothing validates is the failure this whole mechanism
 * exists to prevent: it reads like a guarantee and is not one. Seeing it is what lets the library say so.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
public final class Constraints {

    /**
     * The two names of the same annotation. <code>javax</code> is Bean Validation 1.1 and 2.0,
     * <code>jakarta</code> is Jakarta Validation 3.0 and later; a class path may hold either, and an
     * application in the middle of the rename may hold both.
     */
    private static final String[] CONSTRAINT_ANNOTATIONS = {
            "javax.validation.Constraint", "jakarta.validation.Constraint"
    };

    /**
     * How far to descend into a generic return type looking for a constraint on a type argument. Three
     * levels covers <code>Map&lt;String, List&lt;&#64;Min(1) Integer&gt;&gt;</code>, and the bound is there
     * because a recursive generic type can be walked forever.
     */
    private static final int MAX_TYPE_DEPTH = 3;

    /** Don't let anyone instantiate this class */
    private Constraints() {
    }

    /**
     * Tells whether the given method carries any constraint at all - on the method itself, as in
     * <code>&#64;Min(12) int port()</code>, or on a type argument of its return type, as in
     * <code>Optional&lt;&#64;Min(12) Integer&gt; port()</code>.
     *
     * <p>
     * Both shapes have to be looked at, and the second one is the easy one to forget: a constraint written
     * inside the angle brackets is not among {@link Method#getAnnotations()} at all, so a method whose only
     * constraint is a container element constraint would look unannotated and would be passed over in
     * silence - which is the one outcome not allowed here.
     * </p>
     *
     * @param method the method to inspect.
     * @return <code>true</code> if the method carries at least one Bean Validation constraint.
     */
    public static boolean anyOn(Method method) {
        return anyDeclaredOn(method) || anyOnTypeArgumentsOf(method);
    }

    /**
     * Tells whether the given method carries a constraint <b>written on the method itself</b>, as opposed to
     * one written on a type argument of its return type.
     *
     * <p>
     * The distinction is not academic for a method returning an {@link java.util.Optional} or a collection:
     * a constraint written on the method applies to the container, and a container is never null and never
     * absent. See {@link ConfigValidator} for what is reported about it.
     * </p>
     *
     * @param method the method to inspect.
     * @return <code>true</code> if a constraint annotation is present on the method itself.
     */
    public static boolean anyDeclaredOn(Method method) {
        for (Annotation annotation : method.getAnnotations())
            if (isConstraint(annotation.annotationType()))
                return true;
        return false;
    }

    /**
     * Tells whether the given annotation type is a Bean Validation constraint.
     *
     * @param type the annotation type to inspect, never <code>null</code>.
     * @return <code>true</code> if it is annotated with either spelling of <code>&#64;Constraint</code>.
     */
    public static boolean isConstraint(Class<? extends Annotation> type) {
        for (Annotation meta : type.getAnnotations())
            if (isNamedConstraint(meta.annotationType().getName()))
                return true;
        // @Min.List and its kind: the multi-value container of a repeatable constraint carries no
        // @Constraint of its own, and by convention is a member type of the constraint it repeats
        Class<?> enclosing = type.getEnclosingClass();
        return enclosing != null && enclosing.isAnnotation()
                && isConstraint(enclosing.asSubclass(Annotation.class));
    }

    private static boolean isNamedConstraint(String name) {
        for (String candidate : CONSTRAINT_ANNOTATIONS)
            if (candidate.equals(name))
                return true;
        return false;
    }

    private static boolean anyOnTypeArgumentsOf(Method method) {
        return anyOn(method.getAnnotatedReturnType(), 0, false);
    }

    /**
     * Walks a generic type looking for a constraint on it or on anything inside it.
     *
     * @param type    the type to walk.
     * @param depth   how deep the walk already is, against {@link #MAX_TYPE_DEPTH}.
     * @param counted whether an annotation written directly on this type counts - it does not at the top,
     *                where it is the annotation on the method itself and has already been read.
     */
    private static boolean anyOn(AnnotatedType type, int depth, boolean counted) {
        if (depth > MAX_TYPE_DEPTH)
            return false;
        if (counted)
            for (Annotation annotation : type.getAnnotations())
                if (isConstraint(annotation.annotationType()))
                    return true;
        if (type instanceof AnnotatedParameterizedType)
            for (AnnotatedType argument : ((AnnotatedParameterizedType) type).getAnnotatedActualTypeArguments())
                if (anyOn(argument, depth + 1, true))
                    return true;
        if (type instanceof AnnotatedArrayType)
            return anyOn(((AnnotatedArrayType) type).getAnnotatedGenericComponentType(), depth + 1, true);
        if (type instanceof AnnotatedWildcardType)
            for (AnnotatedType bound : ((AnnotatedWildcardType) type).getAnnotatedUpperBounds())
                if (anyOn(bound, depth + 1, true))
                    return true;
        return false;
    }
}
