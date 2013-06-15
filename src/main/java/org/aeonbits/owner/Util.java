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

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;

import static java.lang.String.format;
import static java.net.URLDecoder.decode;
import static java.util.Arrays.asList;

/**
 * This class contains utility methods used all over the library.
 *
 * @author Luigi R. Viggiano
 */
abstract class Util {
    interface Time {
        long getTime();
    }

    static Time time = new Time() {
        @Override
        public long getTime() {
            return System.currentTimeMillis();
        }
    };

    Util() {
        prohibitInstantiation();
    }

    static void prohibitInstantiation() {
        throw new UnsupportedOperationException("This class is not supposed to be instantiated.");
    }

    @SuppressWarnings("unchecked")
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

    static <T> T ignore() {
        // the ignore method does absolutely nothing, but it helps to shut up warnings by pmd and other reporting tools
        // complaining about empty catch methods.
        return null;
    }

    static boolean isFeatureDisabled(Method method, DisableableFeature feature) {
        Class<DisableFeature> annotation = DisableFeature.class;
        return isFeatureDisabled(feature, method.getDeclaringClass().getAnnotation(annotation)) ||
                isFeatureDisabled(feature, method.getAnnotation(annotation));
    }

    private static boolean isFeatureDisabled(DisableableFeature feature, DisableFeature annotation) {
        return annotation != null && asList(annotation.value()).contains(feature);
    }

    static UnsupportedOperationException unsupported(Throwable cause, String msg, Object... args) {
        return new UnsupportedOperationException(format(msg, args), cause);
    }

    static UnsupportedOperationException unsupported(String msg, Object... args) {
        return new UnsupportedOperationException(format(msg, args));
    }

    static <T> T unreachable() {
        throw new AssertionError("this code should never be reached");
    }

    static String asString(Object result) {
        if (result == null) return null;
        return String.valueOf(result);
    }

    static long now() {
        return time.getTime();
    }

    static File fileFromURL(URL url) {
        if ("file".equalsIgnoreCase(url.getProtocol())) {
            String path = url.getPath();
            try {
                path = decode(path, "utf-8");
                return new File(path);
            } catch (UnsupportedEncodingException e) {
                return unreachable(/* it can't happen since utf-8 is supported in jdk */);
            }
        } else if ("jar".equalsIgnoreCase(url.getProtocol())) {
            String path = url.getPath();
            try {
                return fileFromURL(new URL(path.substring(0, path.indexOf('!'))));
            } catch (MalformedURLException e) {
                return ignore(/* non critical */);
            }
        }
        return null;
    }
}