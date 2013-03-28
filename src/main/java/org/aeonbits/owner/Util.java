/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.DisableFeature;
import org.aeonbits.owner.Config.DisableableFeature;

import java.lang.reflect.Method;
import java.util.Collections;

import static java.util.Arrays.asList;

/**
 * @author Luigi R. Viggiano
 */
class Util {
    Util() {
        prohibitInstantiation();
    }

    static void prohibitInstantiation() {
        throw new UnsupportedOperationException("This class is not supposed to be instantiated.");
    }

    static <T> T[] reverse(T[] array) {
        T[] copy = array.clone();
        Collections.reverse(asList(copy));
        return copy;
    }

    static String expandUserHome(String text) {
        if (text.indexOf('~') != -1)
            return text.replace("~", System.getProperty("user.home"));
        return text;
    }

    static void ignore() {
        // the ignore method does absolutely nothing, but it helps to shut up warnings by pmd and other reporting tools
        // complaining about empty catch methods.
    }

    static boolean isFeatureDisabled(Method method, DisableableFeature feature) {
        Class<DisableFeature> annotation = DisableFeature.class;
        if (isFeatureDisabled(feature, method.getDeclaringClass().getAnnotation(annotation)) ||
                isFeatureDisabled(feature, method.getAnnotation(annotation)))
            return true;
        return false;
    }

    private static boolean isFeatureDisabled(DisableableFeature feature, DisableFeature annotation) {
        return annotation != null && asList(annotation.value()).contains(feature);
    }
}
