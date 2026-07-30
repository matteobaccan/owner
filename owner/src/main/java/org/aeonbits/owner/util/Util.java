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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;

import static java.lang.String.format;
import static java.net.URLDecoder.decode;
import static java.util.Arrays.asList;

/**
 * This class contains utility methods used all over the library.
 *
 * @author Luigi R. Viggiano
 */
public abstract class Util {

    /** Abstraction over the source of the current time, to allow overriding it in tests. */
    @FunctionalInterface
    public interface TimeProvider {
        /**
         * Returns the current time in milliseconds.
         *
         * @return the current time in milliseconds.
         */
        long getTime();
    }

    /** Abstraction over system properties and environment access, to allow overriding it in tests. */
    public interface SystemProvider {
        /**
         * Returns the system property for the given key.
         *
         * @param key the system property name.
         * @return the property value, or <code>null</code> if not set.
         */
        String getProperty(String key);

        /**
         * Returns the environment variables.
         *
         * @return a map of the environment variables.
         */
        Map<String, String> getenv();

        /**
         * Returns a copy of the system properties.
         *
         * @return the system properties.
         */
        Properties getProperties();
    }

    static TimeProvider timeProvider = System::currentTimeMillis;

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

    /**
     * Returns a reversed copy of the given list, leaving the original untouched.
     *
     * @param src the list to reverse.
     * @param <T> the type of the elements.
     * @return a new list with the elements in reverse order.
     */
    public static <T> List<T> reverse(List<T> src) {
        List<T> copy = new ArrayList<>(src);
        Collections.reverse(copy);
        return copy;
    }

    /**
     * Returns a reversed copy of the given array, leaving the original untouched.
     *
     * @param array the array to reverse.
     * @param <T>   the type of the elements.
     * @return a new array with the elements in reverse order.
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] reverse(T[] array) {
        T[] copy = array.clone();
        Collections.reverse(asList(copy));
        return copy;
    }

    /**
     * Expands a leading <code>~</code> in the given text to the user home directory, supporting
     * plain, <code>file:</code> and <code>jar:file:</code> prefixes.
     *
     * @param text the text possibly starting with <code>~</code>.
     * @return the text with the user home expanded, or the original text if no expansion applies.
     */
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

    /**
     * Replaces backslashes with forward slashes in the given path.
     *
     * @param path the path to fix.
     * @return the path with backslashes replaced by forward slashes.
     */
    public static String fixBackslashesToSlashes(String path) {
        return path.replace('\\', '/');
    }

    /**
     * Replaces spaces with <code>%20</code> in the given path.
     *
     * @param path the path to fix.
     * @return the path with spaces replaced by <code>%20</code>.
     */
    public static String fixSpacesToPercentTwenty(String path) {
        return path.replace(" ", "%20");
    }

    /**
     * Does nothing and returns <code>null</code>; used to silence reporting tools complaining about
     * empty catch blocks while making the intent explicit.
     *
     * @param <T> the expected return type.
     * @return always <code>null</code>.
     */
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

    /**
     * Tells whether the given feature is disabled for the given method, considering both the method
     * and its declaring class {@link DisableFeature} annotations.
     *
     * @param method  the method to inspect.
     * @param feature the feature to check.
     * @return <code>true</code> if the feature is disabled, <code>false</code> otherwise.
     */
    public static boolean isFeatureDisabled(Method method, DisableableFeature feature) {
        Class<DisableFeature> annotation = DisableFeature.class;
        return isFeatureDisabled(feature, method.getDeclaringClass().getAnnotation(annotation)) ||
                isFeatureDisabled(feature, method.getAnnotation(annotation));
    }

    private static boolean isFeatureDisabled(DisableableFeature feature, DisableFeature annotation) {
        return annotation != null && asList(annotation.value()).contains(feature);
    }

    /**
     * Builds an {@link UnsupportedOperationException} with a formatted message and a cause.
     *
     * @param cause the cause of the exception.
     * @param msg   the message format, as per {@link String#format(String, Object...)}.
     * @param args  the arguments for the message format.
     * @return the built exception.
     */
    public static UnsupportedOperationException unsupported(Throwable cause, String msg, Object... args) {
        return new UnsupportedOperationException(format(msg, args), cause);
    }

    /**
     * Builds an {@link UnsupportedOperationException} with a formatted message.
     *
     * @param msg  the message format, as per {@link String#format(String, Object...)}.
     * @param args the arguments for the message format.
     * @return the built exception.
     */
    public static UnsupportedOperationException unsupported(String msg, Object... args) {
        return new UnsupportedOperationException(format(msg, args));
    }

    /**
     * Always throws an {@link AssertionError}; used to satisfy the compiler on branches that can
     * never be reached.
     *
     * @param <T> the expected return type.
     * @return never returns normally.
     */
    public static <T> T unreachableButCompilerNeedsThis() {
        throw new AssertionError("this code should never be reached");
    }

    /**
     * Returns the string representation of the given object, or <code>null</code> if it is <code>null</code>.
     *
     * @param result the object to convert.
     * @return the string representation, or <code>null</code>.
     */
    public static String asString(Object result) {
        if (result == null) return null;
        return String.valueOf(result);
    }

    /**
     * Returns the current time in milliseconds, as provided by the configured {@link TimeProvider}.
     *
     * @return the current time in milliseconds.
     */
    public static long now() {
        return timeProvider.getTime();
    }

    /**
     * Resolves a {@link File} from the given URI, supporting <code>file:</code> and <code>jar:</code>
     * schemes.
     *
     * @param uri the URI to resolve.
     * @return the resolved file, or <code>null</code> if the scheme is not supported.
     */
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

    /**
     * Resolves a {@link File} from the given URI string, retrying with backslashes converted to
     * forward slashes if the initial parse fails.
     *
     * @param uriSpec the URI string to resolve.
     * @return the resolved file, or <code>null</code> if the scheme is not supported.
     * @throws URISyntaxException if the string cannot be parsed as a URI.
     */
    public static File fileFromURI(String uriSpec) throws URISyntaxException {
        try {
            return fileFromURI(new URI(uriSpec));
        } catch (URISyntaxException e) {
            // Perhaps the path contains backslashes
            uriSpec = uriSpec.replace('\\', '/');
            return fileFromURI(new URI(uriSpec));
        }
    }

    /**
     * Returns the configured {@link SystemProvider}.
     *
     * @return the system provider.
     */
    public static SystemProvider system() {
        return system;
    }

    /**
     * Saves the given properties to the target file, writing atomically (via a temp file and rename)
     * on non-Windows platforms.
     * <p>
     * If the target file already exists, its permissions are preserved; a newly created file is
     * readable and writable only by the owner.
     *
     * @param target the destination file.
     * @param p      the properties to save.
     * @throws IOException if the file cannot be written.
     */
    public static void save(File target, Properties p) throws IOException {
        File parent = target.getParentFile();
        parent.mkdirs();
        if (isWindows()) {
            store(target, p);
        } else {
            File tempFile = Files.createTempFile(parent.toPath(), target.getName(), ".temp").toFile();
            store(tempFile, p);
            if (target.exists()) {
                try {
                    Files.setPosixFilePermissions(tempFile.toPath(),
                            Files.getPosixFilePermissions(target.toPath()));
                } catch (UnsupportedOperationException ignored) {
                    // non-POSIX filesystem: keep the restrictive defaults
                }
            }
            rename(tempFile, target);
        }

    }

    private static boolean isWindows() {
        return system.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * Deletes the given file.
     *
     * @param target the file to delete.
     */
    public static void delete(File target) {
        target.delete();
    }

    private static void store(File target, Properties p) throws IOException {
        try (OutputStream out = new FileOutputStream(target)) {
            store(out, p);
        }
    }

    private static void store(OutputStream out, Properties p) throws IOException {
        p.store(out, "saved for test");
    }

    /**
     * Saves the given properties into a jar file as the given entry.
     *
     * @param target    the destination jar file.
     * @param entryName the name of the entry to create inside the jar.
     * @param props     the properties to save.
     * @throws IOException if the jar cannot be written.
     */
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
        try (InputStream input = new ByteArrayInputStream(bytes);
             FileOutputStream fileOutputStream = new FileOutputStream(target);
             JarOutputStream output = new JarOutputStream(fileOutputStream)) {
            ZipEntry entry = new ZipEntry(entryName);
            output.putNextEntry(entry);
            byte[] buffer = new byte[4096];
            int size;
            while ((size = input.read(buffer)) != -1)
                output.write(buffer, 0, size);
        }
    }

    private static byte[] toBytes(Properties props) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            store(out, props);
            return out.toByteArray();
        }
    }

    /**
     * Instantiates the given class using its no-argument constructor.
     *
     * @param clazz the class to instantiate.
     * @param <T>   the type of the instance.
     * @return a new instance of the given class.
     * @throws UnsupportedOperationException if the class cannot be instantiated.
     */
    public static <T> T newInstance(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw unsupported(e,
                    "Class '%s' cannot be instantiated; see the cause below in the stack trace",
                    clazz.getCanonicalName());
        }
    }

    /**
     * Instantiates each of the given classes and appends the instances to the given list.
     *
     * @param classes the classes to instantiate.
     * @param result  the list the new instances are added to.
     * @param <T>     the common type of the instances.
     * @return the same list passed as <code>result</code>, with the new instances appended.
     */
    public static <T> List<T> newInstance(Class<? extends T>[] classes, List<T> result) {
        for (Class<? extends T> clazz : classes)
            result.add(newInstance(clazz));
        return result;
    }
}
