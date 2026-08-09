/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.util;

import org.aeonbits.owner.util.Util.SystemProvider;
import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarOutputStream;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;

import static java.lang.String.format;
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

    /** Named after the library rather than after this class, so one switch covers the tests and the code. */
    private static final Logger LOGGER = Logger.getLogger("org.aeonbits.owner");

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

    /**
     * Saves the given properties to the target file, writing atomically (via a temp file and rename)
     * on non-POSIX platforms; if the target file already exists, its permissions are preserved.
     * This and the other file helpers below used to live in {@link Util}, but the library itself
     * never needed them: they are test support code.
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
        return Util.system().getProperty("os.name").toLowerCase().contains("win");
    }

    public static void delete(File target) {
        target.delete();
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

    private static void store(File target, Properties p) throws IOException {
        try (OutputStream out = new FileOutputStream(target)) {
            store(out, p);
        }
    }

    private static void store(OutputStream out, Properties p) throws IOException {
        p.store(out, "saved for test");
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
     * Traces what a test is doing, for whoever is chasing one that fails now and then.
     * <p>
     * This was a hand-rolled logger — a level check on a <code>debug</code> system property, guarding a
     * <code>printf</code> — which is what {@link java.util.logging} does, only with one switch for the whole
     * test suite and none of the configuration. Now that the library itself reports through
     * <code>java.util.logging</code>, the tests may as well use the same thing. Turn it on with
     * <code>-Djava.util.logging.config.file=…</code> holding
     * <code>org.aeonbits.owner.level = FINE</code> and a handler at the same level, or from code with
     * {@code Logger.getLogger("org.aeonbits.owner").setLevel(Level.FINE)}.
     * </p>
     *
     * @param format the format string, as {@link String#format}.
     * @param args   its arguments.
     */
    public static void debug(String format, Object... args) {
        // formatted lazily: a trace inside a loop of a concurrency test is called far more often than it is
        // read, and it costs nothing at all while it is switched off
        LOGGER.fine(() -> String.format(format, args));
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

    @Test
    public void testFileFromURIWithUnsupportedScheme() throws URISyntaxException {
        assertNull(Util.fileFromURI("http://example.com/foo.properties"));
    }

    @Test
    public void testFileFromURIWithBackslashes() throws URISyntaxException {
        // backslashes are illegal in URIs: the first parse fails and fileFromURI must retry
        // with the backslashes converted to slashes, as it happens with Windows paths
        File result = Util.fileFromURI("file:/foo\\bar\\baz.properties");
        assertEquals(new File("/foo/bar/baz.properties"), result);
    }

    @Test
    public void testFileFromURIWithJarURIHavingUnparsableInnerPath() throws URISyntaxException {
        // '^' is illegal in URIs: the inner path extracted from the jar URI cannot be
        // parsed, and fileFromURI must give up returning null
        URI uri = new URI("jar", "file:/foo^bar.jar!/baz.properties", null);
        assertNull(Util.fileFromURI(uri));
    }

    private static Properties loadProperties(File source) throws IOException {
        Properties loaded = new Properties();
        try (InputStream in = new FileInputStream(source)) {
            loaded.load(in);
        }
        return loaded;
    }

    @Test
    public void testSaveOnNonWindows() throws IOException {
        SystemProvider save = setSystem(new SystemProviderForTest(
                new Properties() {{
                    setProperty("os.name", "Linux");
                }}, new HashMap<>()
        ));
        File target = new File("target/utiltest/UtilTest_saveOnNonWindows.properties");
        delete(target);
        try {
            save(target, new Properties() {{
                setProperty("foo", "bar");
            }});
        } finally {
            setSystem(save);
        }
        assertEquals("bar", loadProperties(target).getProperty("foo"));
    }

    @Test
    public void testSaveOnWindows() throws IOException {
        SystemProvider save = setSystem(new SystemProviderForTest(
                new Properties() {{
                    setProperty("os.name", "Windows 11");
                }}, new HashMap<>()
        ));
        File target = new File("target/utiltest/UtilTest_saveOnWindows.properties");
        delete(target);
        try {
            save(target, new Properties() {{
                setProperty("foo", "baz");
            }});
        } finally {
            setSystem(save);
        }
        assertEquals("baz", loadProperties(target).getProperty("foo"));
    }

    /**
     * When the target file already exists, the non-Windows code path must preserve its
     * permissions by copying them onto the temporary file before the atomic rename.
     * The test needs a real POSIX platform: on Windows, renaming over an existing file
     * is not supported by the filesystem, regardless of the mocked os.name.
     */
    @Test
    public void testSaveOnNonWindowsOverwritesExistingTarget() throws IOException {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().contains("win"));
        SystemProvider save = setSystem(new SystemProviderForTest(
                new Properties() {{
                    setProperty("os.name", "Linux");
                }}, new HashMap<>()
        ));
        File target = new File("target/utiltest/UtilTest_saveOverExisting.properties");
        delete(target);
        try {
            save(target, new Properties() {{
                setProperty("foo", "first");
            }});
            save(target, new Properties() {{
                setProperty("foo", "second");
            }});
        } finally {
            setSystem(save);
        }
        assertEquals("second", loadProperties(target).getProperty("foo"));
    }

    @Test(expected = IOException.class)
    public void testSaveFailsWhenTempFileCannotBeRenamed() throws IOException {
        SystemProvider save = setSystem(new SystemProviderForTest(
                new Properties() {{
                    setProperty("os.name", "Linux");
                }}, new HashMap<>()
        ));
        try {
            // renaming a file over an existing non-empty directory fails on every platform
            File target = new File("target/utiltest/UtilTest_renameFail.dir");
            target.mkdirs();
            File obstacle = new File(target, "obstacle.txt");
            // the directory must be non-empty for the rename to fail: it may already be, if a
            // previous run left it behind (the test target directory was not cleaned)
            assertTrue(obstacle.createNewFile() || obstacle.isFile());
            save(target, new Properties());
        } finally {
            setSystem(save);
        }
    }

    @Test
    public void testExpandUserHomeOnUnix() {
        SystemProvider save = UtilTest.setSystem(new SystemProviderForTest(
                new Properties() {{
                    setProperty("user.home", "/home/john");
                }},  new HashMap<>()
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
                }}, new HashMap<>()
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
