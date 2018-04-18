/*
 * Copyright (c) 2012-2018, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import org.aeonbits.owner.util.Util.SystemProvider;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.aeonbits.owner.util.Util.ignore;
import static org.aeonbits.owner.util.Util.unreachableButCompilerNeedsThis;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * This class contains tests for the {@link Util} class as well utility methods used for test classes.
 *
 * @author Luigi R. Viggiano
 */
public class UtilTest {

    public static SystemProvider setSystem(Object system) {
        SystemProvider save = Util.system;
        Util.system = (SystemProvider)system;
        return save;
    }

    public static Properties getSystemProperties() {
        return Util.system().getProperties();
    }

    public SystemProvider system() {
        return Util.system();
    }

    @Test
    public void testReverse() {
        Integer[] i = {1, 2, 3, 4, 5};
        Integer[] result = Util.reverse(i);
        assertTrue(Arrays.equals(new Integer[] {1, 2, 3, 4, 5}, i));
        assertTrue(Arrays.equals(new Integer[] {5, 4, 3, 2, 1}, result));
    }

    @Test
    public void testIgnoreAndReturnNull() {
        Object result = ignoreAndReturnNull();
        assertNull(result);
    }

    @Test
    public void testIgnore() {
        ignore();
    }

    @Test
    public void testUnreachable() {
        try {
            unreachableButCompilerNeedsThis();
        } catch (AssertionError err) {
            assertEquals("this code should never be reached", err.getMessage());
        }
    }

    public static void save(File target, Properties p) throws IOException {
        Util.save(target, p);
    }

    public static void delete(File target) {
        Util.delete(target);
    }

    public static void saveJar(File target, String entryName, Properties props) throws IOException {
        Util.saveJar(target, entryName, props);
    }

    public static void debug(String format, Object... args) {
        if (Boolean.getBoolean("debug"))
            System.out.printf(format, args);
    }

    public static <T> T ignoreAndReturnNull() {
        return Util.ignoreAndReturnNull();
    }

    public static File fileFromURI(String spec) throws URISyntaxException {
        return Util.fileFromURI(spec);
    }

    public static String getSystemProperty(String key) {
        return Util.system().getProperty(key);
    }

    public static String getenv(String home) {
        return Util.system().getenv().get(home);
    }

    public static Map<String, String> getenv() {
        return Util.system().getenv();
    }

    public interface MyCloneable extends Cloneable {
        // for some stupid reason java.lang.Cloneable doesn't define this method...
        Object clone() throws CloneNotSupportedException;
    }

    @SuppressWarnings("unchecked")
    public static <T extends MyCloneable> T[] newArray(int size, T cloneable) throws CloneNotSupportedException {
        Object array = Array.newInstance(cloneable.getClass(), size);
        Array.set(array, 0, cloneable);
        for (int i = 1; i < size; i++)
            Array.set(array, i, cloneable.clone());
        return (T[]) array;
    }

    public static boolean eq(Object o1, Object o2) {
        return Util.eq(o1, o2);
    }

    @Test
    public void testExpandUserHomeOnUnix() {
        SystemProvider save = UtilTest.setSystem(new SystemProviderForTest(
                new Properties() {{
                    setProperty("user.home", "/home/john");
                }},  new HashMap<String, String>()
        ));

        try {
            assertEquals("/home/john", Util.expandUserHome("~"));
            assertEquals("/home/john/foo/bar/", Util.expandUserHome("~/foo/bar/"));
            assertEquals("file:/home/john/foo/bar/", Util.expandUserHome("file:~/foo/bar/"));
            assertEquals("jar:file:/home/john/foo/bar/", Util.expandUserHome("jar:file:~/foo/bar/"));

            assertEquals("/home/john\\foo\\bar\\", Util.expandUserHome("~\\foo\\bar\\"));
            assertEquals("file:/home/john\\foo\\bar\\", Util.expandUserHome("file:~\\foo\\bar\\"));
            assertEquals("jar:file:/home/john\\foo\\bar\\", Util.expandUserHome("jar:file:~\\foo\\bar\\"));
        } finally {
            UtilTest.setSystem(save);
        }
    }

    @Test
    public void testExpandUserHomeOnWindows() {
        SystemProvider save = UtilTest.setSystem(new SystemProviderForTest(
                new Properties() {{
                    setProperty("user.home", "C:\\Users\\John");
                }}, new HashMap<String, String>()
        ));
        try {
            assertEquals("C:\\Users\\John", Util.expandUserHome("~"));
            assertEquals("C:\\Users\\John/foo/bar/", Util.expandUserHome("~/foo/bar/"));
            assertEquals("file:C:\\Users\\John/foo/bar/", Util.expandUserHome("file:~/foo/bar/"));
            assertEquals("jar:file:C:\\Users\\John/foo/bar/", Util.expandUserHome("jar:file:~/foo/bar/"));

            assertEquals("C:\\Users\\John\\foo\\bar\\", Util.expandUserHome("~\\foo\\bar\\"));
            assertEquals("file:C:\\Users\\John\\foo\\bar\\", Util.expandUserHome("file:~\\foo\\bar\\"));
            assertEquals("jar:file:C:\\Users\\John\\foo\\bar\\", Util.expandUserHome("jar:file:~\\foo\\bar\\"));
        } finally {
            UtilTest.setSystem(save);
        }
    }

}
