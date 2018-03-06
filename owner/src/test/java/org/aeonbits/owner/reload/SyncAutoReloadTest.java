/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.reload;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.TestConstants;
import org.aeonbits.owner.util.TimeProviderForTest;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.aeonbits.owner.util.UtilTest.fileFromURI;
import static org.aeonbits.owner.util.UtilTest.save;
import static org.aeonbits.owner.util.UtilTest.saveJar;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Luigi R. Viggiano
 */
public class SyncAutoReloadTest implements TestConstants {

    private static final String PROPERTY_FILE_NAME = "SyncAutoReloadConfig.properties";
    private static final String JAR_FILE = RESOURCES_DIR + "/SyncAutoReloadTest.jar";

    private static final String SPEC = "file:"+ RESOURCES_DIR + "/" + PROPERTY_FILE_NAME;
    private static final String SPEC_JAR = "jar:file:" + JAR_FILE + "!/" + PROPERTY_FILE_NAME;

    private static File target;
    private static File jarTarget;

    private static TimeProviderForTest time;

    @BeforeClass
    public static void beforeClass() throws URISyntaxException {
        target = fileFromURI(SPEC);
        jarTarget = new File(JAR_FILE);
    }

    @Before
    public void before() {
        time = new TimeProviderForTest();
        time.setup();                // become owner of time (now I can control the elapse of time in this test)
    }

    @Sources(SPEC)
    @HotReload(5)
    interface SyncAutoReloadConfig extends Config {
        @DefaultValue("5")
        Integer someValue();
    }

    @Test
    public void testAutoReload() throws IOException, InterruptedException {
        save(target, new Properties() {{
            setProperty("someValue", "10");
        }});
        boolean success = target.setLastModified(target.lastModified() - 15000); // make the file 15 seconds older.
        assertTrue(success);
        time.setTime(target.lastModified());                   // set the time for this test to match the file creation.

        SyncAutoReloadConfig cfg = ConfigFactory.create(SyncAutoReloadConfig.class);
        assertEquals(Integer.valueOf(10), cfg.someValue());

        save(target, new Properties() {{        // file updated, the update time is reflected in target.lastModified().
            setProperty("someValue", "20");
        }});

        time.elapse(4, SECONDS);                             // make 4 seconds elapse for the test.
        assertEquals(Integer.valueOf(10), cfg.someValue());  // change is not reflected yet since interval is 5 secs.

        time.elapse(1, SECONDS);                             // another second is elapsed for the test.
        assertEquals(Integer.valueOf(20), cfg.someValue());  // the changed file should be reloaded now.
    }

    @Sources(SPEC_JAR)
    @HotReload(5)
    interface AutoReloadJarConfig extends Config {
        Integer someValue();
    }

    @Test
    public void testAutoReloadOnJarFile() throws Throwable {
        saveJar(jarTarget, PROPERTY_FILE_NAME,
                new Properties() {{
                    setProperty("someValue", "10");
                }});

        boolean success = jarTarget.setLastModified(jarTarget.lastModified() - 15000); // make the file 15 seconds older
        assertTrue(success);

        time.setTime(jarTarget.lastModified());              // set the time for this test to match the file creation

        AutoReloadJarConfig cfg = ConfigFactory.create(AutoReloadJarConfig.class);
        assertEquals(Integer.valueOf(10), cfg.someValue());

        saveJar(jarTarget, PROPERTY_FILE_NAME,    // file updated, the update time is reflected in target.lastModified().
                new Properties() {{
                    setProperty("someValue", "20");
                }});

        time.elapse(4, SECONDS);                             // make 4 seconds elapse for the test.
        assertEquals(Integer.valueOf(10), cfg.someValue());  // change is not reflected yet since interval is 5 secs.

        time.elapse(1, SECONDS);                             // another second is elapsed for the test.
        assertEquals(Integer.valueOf(20), cfg.someValue());  // the changed file should be reloaded now.
    }

    @Test
    public void testAutoReloadWhenFileGetsDeleted() throws IOException, InterruptedException {
        save(target, new Properties() {{
            setProperty("someValue", "10");
        }});
        boolean success = target.setLastModified(target.lastModified() - 15000); // make the file 15 seconds older.
        assertTrue(success);
        time.setTime(target.lastModified());                   // set the time for this test to match the file creation.

        SyncAutoReloadConfig cfg = ConfigFactory.create(SyncAutoReloadConfig.class);
        assertEquals(Integer.valueOf(10), cfg.someValue());

        boolean deleted = target.delete();
        assertTrue(deleted);

        time.elapse(4, SECONDS);                             // make 4 seconds elapse for the test.
        assertEquals(Integer.valueOf(10), cfg.someValue());  // change is not reflected yet since interval is 5 secs.

        time.elapse(1, SECONDS);                             // another second is elapsed for the test.
        assertEquals(Integer.valueOf(5), cfg.someValue());   // the deleted file should be noted now,
                                                             // the default value is returned.
    }

    @HotReload(5)
    interface SyncAutoReloadConfigFromClasspath extends Config {
        @DefaultValue("5")
        Integer someValue();
    }

    @Test
    public void testAutoReloadFromClasspath() throws IOException, InterruptedException {
        File classpathTarget =
                new File("target/test-classes/" +
                        "org/aeonbits/owner/reload/" +
                        "SyncAutoReloadTest$SyncAutoReloadConfigFromClasspath.properties");
        classpathTarget.deleteOnExit();

        save(classpathTarget, new Properties() {{
            setProperty("someValue", "10");
        }});

        boolean success = classpathTarget.setLastModified(classpathTarget.lastModified() - 15000); // make the file 15 seconds older.
        assertTrue(success);
        time.setTime(classpathTarget.lastModified());                   // set the time for this test to match the file creation.

        SyncAutoReloadConfigFromClasspath cfg = ConfigFactory.create(SyncAutoReloadConfigFromClasspath.class);
        assertEquals(Integer.valueOf(10), cfg.someValue());

        save(classpathTarget, new Properties() {{        // file updated, the update time is reflected in target.lastModified().
            setProperty("someValue", "20");
        }});

        time.elapse(4, SECONDS);                             // make 4 seconds elapse for the test.
        assertEquals(Integer.valueOf(10), cfg.someValue());  // change is not reflected yet since interval is 5 secs.

        time.elapse(1, SECONDS);                             // another second is elapsed for the test.
        assertEquals(Integer.valueOf(20), cfg.someValue());  // the changed file should be reloaded now.
    }


    @After
    public void after() {
        time.tearDown();
    }

}
