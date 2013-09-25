/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Util.SystemProvider;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.aeonbits.owner.Util.ignore;
import static org.aeonbits.owner.Util.unreachableButCompilerNeedsThis;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * This class contains tests for the {@link Util} class as well utility methods used for test classes.
 *
 * @author Luigi R. Viggiano
 */
public class UtilTest {
    @Test(expected = UnsupportedOperationException.class)
    public void testConstructor() {
        new Util() {
        };
    }

    @Test
    public void testReverse() {
        Integer[] i = {1, 2, 3, 4, 5};
        Integer[] result = Util.reverse(i);
        assertTrue(Arrays.equals(new Integer[] {1, 2, 3, 4, 5}, i));
        assertTrue(Arrays.equals(new Integer[] {5, 4, 3, 2, 1}, result));
    }

    @Test
    public void testIgnore() {
        Object result = ignore();
        assertNull(result);
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
        File tempFile = createTempFile(target);
        store(tempFile, p);
        rename(tempFile, target);
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

    public static void delete(File target) {
        target.delete();
    }

    public static void saveJar(File target, String entryName, Properties props) throws IOException {
        byte[] bytes = toBytes(props);
        InputStream input = new ByteArrayInputStream(bytes);
        FileOutputStream fos = new FileOutputStream(target);
        JarOutputStream output = new JarOutputStream(fos);
        try {
            ZipEntry entry = new ZipEntry(entryName);
            output.putNextEntry(entry);
            byte[] buffer = new byte[4096];
            int size;
            while ((size = input.read(buffer)) != -1)
                output.write(buffer, 0, size);
            output.flush();
        } finally {
            input.close();
            output.close();
            fos.close();
        }
    }

    private static File createTempFile(File target) throws IOException {
        File parent = target.getParentFile();
        parent.mkdirs();
        return File.createTempFile(target.getName(), ".temp", parent);
    }

    private static void rename(File source, File target) throws IOException {
        if (!source.renameTo(target))
            throw new IOException(String.format("Failed to overwrite %s to %s", source.toString(), target.toString()));
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

    public static void debug(String format, Object... args) {
        if (Boolean.getBoolean("debug"))
            System.out.printf(format, args);
    }

    public static interface MyCloneable extends Cloneable {
        // for some stupid reason java.lang.Cloneable doesn't define this method...
        public Object clone() throws CloneNotSupportedException;
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
        SystemProvider save = Util.system;
        try {
            Util.system = new SystemProviderForTest(
                    new Properties() {{
                        setProperty("user.home", "/home/john");
                    }},  new HashMap<String, String>()
            );
            assertEquals("/home/john", Util.expandUserHome("~"));
            assertEquals("/home/john/foo/bar/", Util.expandUserHome("~/foo/bar/"));
            assertEquals("file:/home/john/foo/bar/", Util.expandUserHome("file:~/foo/bar/"));
            assertEquals("jar:file:/home/john/foo/bar/", Util.expandUserHome("jar:file:~/foo/bar/"));

            assertEquals("/home/john\\foo\\bar\\", Util.expandUserHome("~\\foo\\bar\\"));
            assertEquals("file:/home/john\\foo\\bar\\", Util.expandUserHome("file:~\\foo\\bar\\"));
            assertEquals("jar:file:/home/john\\foo\\bar\\", Util.expandUserHome("jar:file:~\\foo\\bar\\"));
        } finally {
            Util.system = save;
        }
    }

    @Test
    public void testExpandUserHomeOnWindows() {
        SystemProvider save = Util.system;
        try {
            Util.system = new SystemProviderForTest(
                    new Properties() {{
                        setProperty("user.home", "C:\\Users\\John");
                    }}, new HashMap<String, String>()
            );
            assertEquals("C:\\Users\\John", Util.expandUserHome("~"));
            assertEquals("C:\\Users\\John/foo/bar/", Util.expandUserHome("~/foo/bar/"));
            assertEquals("file:C:\\Users\\John/foo/bar/", Util.expandUserHome("file:~/foo/bar/"));
            assertEquals("jar:file:C:\\Users\\John/foo/bar/", Util.expandUserHome("jar:file:~/foo/bar/"));

            assertEquals("C:\\Users\\John\\foo\\bar\\", Util.expandUserHome("~\\foo\\bar\\"));
            assertEquals("file:C:\\Users\\John\\foo\\bar\\", Util.expandUserHome("file:~\\foo\\bar\\"));
            assertEquals("jar:file:C:\\Users\\John\\foo\\bar\\", Util.expandUserHome("jar:file:~\\foo\\bar\\"));
        } finally {
            Util.system = save;
        }

    }

}
