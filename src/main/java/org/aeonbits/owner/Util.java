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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static java.lang.String.format;
import static java.net.URLDecoder.decode;
import static java.util.Arrays.asList;

/**
 * This class contains utility methods used all over the library.
 *
 * @author Luigi R. Viggiano
 */
abstract class Util {

    interface TimeProvider {
        long getTime();
    }

    interface SystemProvider {
        String getProperty(String key);

        Map<?, ?> getenv();

        Properties getProperties();
    }

    static TimeProvider timeProvider = new TimeProvider() {
        public long getTime() {
            return System.currentTimeMillis();
        }
    };

    static SystemProvider system = new SystemProvider() {
        public String getProperty(String key) {
            return System.getProperty(key);
        }

        public java.util.Map<String, String> getenv() {
            return System.getenv();
        }

        public Properties getProperties() {
            return System.getProperties();
        }
    };

    Util() {
        prohibitInstantiation();
    }

    static void prohibitInstantiation() {
        throw new UnsupportedOperationException("This class is not supposed to be instantiated.");
    }

    static <T> List<T> reverse(List<T> src) {
        List<T> copy = new ArrayList<T>(src);
        Collections.reverse(copy);
        return copy;
    }

    @SuppressWarnings("unchecked")
    static <T> T[] reverse(T[] array) {
        T[] copy = array.clone();
        Collections.reverse(asList(copy));
        return copy;
    }

    static String expandUserHome(String text) {
        if (text.equals("~")) {
            return system.getProperty("user.home");
        } else if (text.indexOf("~/") == 0 || text.indexOf("file:~/") == 0 || text.indexOf("jar:file:~/") == 0) {
            String safeHome = system.getProperty("user.home").replace("\\", "\\\\");
            return text.replaceFirst("~/", safeHome + "/");
        }
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

    static <T> T unreachableButCompilerNeedsThis() {
        throw new AssertionError("this code should never be reached");
    }

    static String asString(Object result) {
        if (result == null) return null;
        return String.valueOf(result);
    }

    static long now() {
        return timeProvider.getTime();
    }

    static File fileFromURL(URL url) {
        if ("file".equalsIgnoreCase(url.getProtocol())) {
            String path = url.getPath();
            try {
                path = decode(path, "utf-8");
                return new File(path);
            } catch (UnsupportedEncodingException e) {
                return unreachableButCompilerNeedsThis(/* utf-8 is supported in jre libraries */);
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

    static boolean eq(Object o1, Object o2) {
        return o1 == o2 || o1 != null && o1.equals(o2);
    }

    static SystemProvider system() {
        return system;
    }
}
