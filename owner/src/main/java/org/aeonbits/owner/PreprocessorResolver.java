/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.PreprocessorClasses;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.aeonbits.owner.util.Util.newInstance;

/**
 * @author Luigi R. Viggiano
 */
final class PreprocessorResolver {

    /**
     * Don't let anyone instantiate this class
     */
    private PreprocessorResolver() {
    }

    public static List<Preprocessor> resolvePreprocessors(Method method) {
        List<Preprocessor> result = new ArrayList<Preprocessor>();
        List<Preprocessor> preprocessorsOnMethod = getPreprocessor(method.getAnnotation(PreprocessorClasses.class));
        result.addAll(preprocessorsOnMethod);

        List<Preprocessor> preprocessorsOnClass = getPreprocessor(method.getDeclaringClass()
                .getAnnotation(PreprocessorClasses.class));
        result.addAll(preprocessorsOnClass);

        return result;
    }

    private static List<Preprocessor> getPreprocessor(PreprocessorClasses preprocessorClassesAnnotation) {
        if (preprocessorClassesAnnotation == null) return emptyList();
        Class<? extends Preprocessor>[] preprocessorClasses = preprocessorClassesAnnotation.value();
        if (preprocessorClasses == null) return emptyList();
        List<Preprocessor> result = new LinkedList<Preprocessor>();
        return newInstance(preprocessorClassesAnnotation.value(), result);
    }

}
