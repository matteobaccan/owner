/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import java.lang.annotation.Annotation;

/**
 * @author Luigi R. Viggiano
 */
public class Reflection {
    
    // Suppresses default constructor, ensuring non-instantiability.
    private Reflection() {}

    public static boolean isClassAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Method to recursively find annotated config class from the current interface class or from its parents
     * @param clazz, annotationClazz
     * @return
     */
    public static Annotation getAnnotation(Class<?> clazz, Class<? extends Annotation> annotationClazz)
    {
        Annotation annotations = clazz.getAnnotation(annotationClazz);
        if (annotations == null && clazz.getInterfaces() != null)
        {
            for (Class<?> i : clazz.getInterfaces())
            {
                Annotation annotation = getAnnotation(i, annotationClazz);
                if (annotation != null)
                    return annotation;
            }
        }
        return annotations;
    }
    
}
