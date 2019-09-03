/*
 * Copyright (c) 2012-2018, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import org.aeonbits.owner.Config.DisableFeature;
import org.aeonbits.owner.Config.DisableableFeature;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;

import static java.io.File.createTempFile;
import static java.lang.String.format;
import static java.net.URLDecoder.decode;
import static java.util.Arrays.asList;

/**
 * This class contains utility methods used all over the library.
 *
 * @author Luigi R. Viggiano
 */
public abstract class Util {

    public interface TimeProvider {
        long getTime();
    }

    public interface SystemProvider {
        String getProperty(String key);

        Map<String, String> getenv();

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

        public Map<String, String> getenv() {
            return System.getenv();
        }

        public Properties getProperties() {
            return (Properties) System.getProperties().clone();
        }
    };

    /** Don't let anyone instantiate this class */
    private Util() {}

    public static <T> List<T> reverse(List<T> src) {
        List<T> copy = new ArrayList<T>(src);
        Collections.reverse(copy);
        return copy;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] reverse(T[] array) {
        T[] copy = array.clone();
        Collections.reverse(asList(copy));
        return copy;
    }

    public static String expandUserHome(String text) {
        if (text.equals("~"))
            return getUserHome();
        if (text.indexOf("~/") == 0 || text.indexOf("file:~/") == 0 || text.indexOf("jar:file:~/") == 0)
            return text.replaceFirst("~/", Matcher.quoteReplacement(getUserHome()) + "/");
        if (text.indexOf("~\\") == 0 || text.indexOf("file:~\\") == 0 || text.indexOf("jar:file:~\\") == 0)
            return text.replaceFirst("~\\\\", Matcher.quoteReplacement(getUserHome()) + "\\\\");
        return text;
    }

    private static String getUserHome() {
        return system.getProperty("user.home");
    }

    public static String fixBackslashesToSlashes(String path) {
        return path.replace('\\', '/');
    }

    public static String fixSpacesToPercentTwenty(String path) {
        return path.replace(" ", "%20");
    }

    public static <T> T ignoreAndReturnNull() {
        // the ignoreAndReturnNull method does absolutely nothing, but it helps to shut up warnings by pmd and other reporting tools
        // complaining about empty catch methods.
        return null;
    }

    /**
     * no operation
     */
    public static void ignore() {
    }

    public static boolean isFeatureDisabled(Method method, DisableableFeature feature) {
        Class<DisableFeature> annotation = DisableFeature.class;
        return isFeatureDisabled(feature, method.getDeclaringClass().getAnnotation(annotation)) ||
                isFeatureDisabled(feature, method.getAnnotation(annotation));
    }

    private static boolean isFeatureDisabled(DisableableFeature feature, DisableFeature annotation) {
        return annotation != null && asList(annotation.value()).contains(feature);
    }

    public static UnsupportedOperationException unsupported(Throwable cause, String msg, Object... args) {
        return new UnsupportedOperationException(format(msg, args), cause);
    }

    public static UnsupportedOperationException unsupported(String msg, Object... args) {
        return new UnsupportedOperationException(format(msg, args));
    }

    public static <T> T unreachableButCompilerNeedsThis() {
        throw new AssertionError("this code should never be reached");
    }

    public static String asString(Object result) {
        if (result == null) return null;
        return String.valueOf(result);
    }

    public static long now() {
        return timeProvider.getTime();
    }

    public static File fileFromURI(URI uri) {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getSchemeSpecificPart();
            try {
                path = decode(path, "utf-8");
                return new File(path);
            } catch (UnsupportedEncodingException e) {
                return unreachableButCompilerNeedsThis(/* utf-8 is supported in jre libraries */);
            }
        } else if ("jar".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getSchemeSpecificPart();
            try {
                return fileFromURI(path.substring(0, path.indexOf('!')));
            } catch (URISyntaxException e) {
                return ignoreAndReturnNull(/* non critical */);
            }
        }
        return null;
    }

    public static File fileFromURI(String uriSpec) throws URISyntaxException {
        try {
            return fileFromURI(new URI(uriSpec));
        } catch (URISyntaxException e) {
            // Perhaps the path contains backslashes
            uriSpec = uriSpec.replace('\\', '/');
            return fileFromURI(new URI(uriSpec));
        }
    }

    public static boolean eq(Object o1, Object o2) {
        return o1 == o2 || o1 != null && o1.equals(o2);
    }

    public static SystemProvider system() {
        return system;
    }

    public static void save(File target, Properties p) throws IOException {
        File parent = target.getParentFile();
        parent.mkdirs();
        if (isWindows()) {
            store(target, p);
        } else {
            File tempFile = createTempFile(target.getName(), ".temp", parent);
            store(tempFile, p);
            rename(tempFile, target);
        }

    }

    private static boolean isWindows() {
        return system.getProperty("os.name").toLowerCase().contains("win");
    }

    public static void delete(File target) {
        target.delete();
    }

    private static void store(File target, Properties p) throws IOException {
        OutputStream out = new FileOutputStream(target);
        try {
            store(out, p);
        } finally {
            out.close();
        }
    }

    private static void store(OutputStream out, Properties p) throws IOException {
        p.store(out, "saved for test");
    }

    public static void saveJar(File target, String entryName, Properties props) throws IOException {
        File parent = target.getParentFile();
        parent.mkdirs();
        storeJar(target, entryName, props);
    }

    private static void rename(File source, File target) throws IOException {
        if (!source.renameTo(target))
            throw new IOException(format("Failed to overwrite %s to %s", source.toString(), target.toString()));
    }

    private static void storeJar(File target, String entryName, Properties props) throws IOException {
        byte[] bytes = toBytes(props);
        InputStream input = new ByteArrayInputStream(bytes);
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(target);
            try {
                JarOutputStream output = new JarOutputStream(fileOutputStream);
                try {
                    ZipEntry entry = new ZipEntry(entryName);
                    output.putNextEntry(entry);
                    byte[] buffer = new byte[4096];
                    int size;
                    while ((size = input.read(buffer)) != -1)
                        output.write(buffer, 0, size);
                } finally {
                    output.close();
                }
            } finally {
                fileOutputStream.close();
            }
        } finally {
            input.close();
        }
    }

    private static byte[] toBytes(Properties props) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            store(out, props);
            return out.toByteArray();
        } finally {
            out.close();
        }
    }

    public static <T> T newInstance(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw unsupported(e,
                    "Class '%s' cannot be instantiated; see the cause below in the stack trace",
                    clazz.getCanonicalName());
        }
    }

    public static <T> List<T> newInstance(Class<? extends T>[] classes, List<T> result) {
        for (Class<? extends T> clazz : classes)
            result.add(newInstance(clazz));
        return result;
    }
}
