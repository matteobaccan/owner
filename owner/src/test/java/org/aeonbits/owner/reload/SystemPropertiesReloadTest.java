package org.aeonbits.owner.reload;

import org.aeonbits.owner.*;
import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.event.ReloadEvent;
import org.aeonbits.owner.event.ReloadListener;
import org.aeonbits.owner.util.SystemProviderForTest;
import org.aeonbits.owner.util.UtilTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Properties;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.aeonbits.owner.Config.HotReloadType.ASYNC;
import static org.junit.Assert.assertEquals;

public class SystemPropertiesReloadTest extends AsyncReloadSupport {
    private SystemProviderForTest systemForTest = new SystemProviderForTest(new Properties(), new HashMap<String, String>());
    private Object save;

    @Sources("system:properties")
    @HotReload(value = 1, unit = MILLISECONDS, type = ASYNC)
    interface AsyncAutoReloadConfig extends Config, Reloadable {
        @DefaultValue("5")
        Integer someValue();
    }

    @Before
    public void before() {
        this.save = UtilTest.setSystem(systemForTest);
    }

    @After
    public void after() {
        UtilTest.setSystem(save);
    }

    @Test
    public void testReload() throws Throwable {

        AsyncAutoReloadConfig cfg = ConfigFactory.create(AsyncAutoReloadConfig.class);

        cfg.addReloadListener(new ReloadListener() {
            public void reloadPerformed(ReloadEvent event) {
                notifyReload();
            }
        });

        assertEquals(Integer.valueOf(5), cfg.someValue());

        String propKey = "someValue";
        systemForTest.setProperty(propKey, "5");
        waitForReload(10);
        assertEquals(Integer.valueOf(5), cfg.someValue());

        systemForTest.setProperty(propKey, "20");
        waitForReload(10);
        assertEquals(Integer.valueOf(20), cfg.someValue());

        systemForTest.remove(propKey);
        waitForReload(10);
        assertEquals(Integer.valueOf(5), cfg.someValue());

        systemForTest.setProperty(propKey, "30");
        waitForReload(10);
        assertEquals(Integer.valueOf(30), cfg.someValue());
    }
}
