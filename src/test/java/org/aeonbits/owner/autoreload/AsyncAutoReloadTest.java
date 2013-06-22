package org.aeonbits.owner.autoreload;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Reloadable;
import org.aeonbits.owner.event.ReloadEvent;
import org.aeonbits.owner.event.ReloadListener;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.aeonbits.owner.Config.HotReloadType.ASYNC;
import static org.aeonbits.owner.UtilTest.save;
import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class AsyncAutoReloadTest {
    private static final String propertyFileName = "AsyncAutoReloadConfig.properties";

    private static final String spec = "file:target/test-resources/" + propertyFileName;

    private static File target;

    @BeforeClass
    public static void beforeClass() throws MalformedURLException {
        target = new File(new URL(spec).getFile());
    }


    @Sources(spec)
    @HotReload(value=500, unit = MILLISECONDS, type = ASYNC)
    interface AsyncAutoReloadConfig extends Config, Reloadable {
        Integer someValue();
    }

    @Test
    public void testReload() throws Throwable {

        save(target, new Properties() {{
            setProperty("someValue", "10");
        }});

        AsyncAutoReloadConfig cfg = ConfigFactory.create(AsyncAutoReloadConfig.class);
        assertEquals(Integer.valueOf(10), cfg.someValue());

        final int[] reloadCount = {0};
        cfg.addReloadListener(new ReloadListener() {
            @Override
            public void reloadPerformed(ReloadEvent event) {
                reloadCount[0]++;
            }
        });

        assertEquals(0, reloadCount[0]);
        assertEquals(Integer.valueOf(10), cfg.someValue());

        Thread.sleep(2000);

        save(target, new Properties() {{
            setProperty("someValue", "20");
        }});

        Thread.sleep(2000);

        assertEquals(1, reloadCount[0]);
        assertEquals(Integer.valueOf(20), cfg.someValue());

        save(target, new Properties() {{
            setProperty("someValue", "30");
        }});

        Thread.sleep(2000);

        assertEquals(2, reloadCount[0]);
        assertEquals(Integer.valueOf(30), cfg.someValue());

    }

}
