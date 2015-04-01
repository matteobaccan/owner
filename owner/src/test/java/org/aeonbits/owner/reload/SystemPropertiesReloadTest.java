package org.aeonbits.owner.reload;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.aeonbits.owner.Config.HotReloadType.ASYNC;
import static org.junit.Assert.assertEquals;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Reloadable;
import org.aeonbits.owner.SystemPropertiesHelper;
import org.aeonbits.owner.event.ReloadEvent;
import org.aeonbits.owner.event.ReloadListener;
import org.junit.After;
import org.junit.Test;

public class SystemPropertiesReloadTest extends AsyncReloadSupport {

    private static final int DELAY = 1000;

    private SystemPropertiesHelper systemPropertiesHelper = new SystemPropertiesHelper();

    @Config.Sources("system:properties")
    @Config.HotReload(value = 10, unit = MILLISECONDS, type = ASYNC)
    interface AsyncAutoReloadConfig extends Config, Reloadable {
        @DefaultValue("5")
        Integer someValue();
    }

    @Test
    public void testReload() throws Throwable {

        String propKey = "someValue";

        AsyncAutoReloadConfig cfg = ConfigFactory.create(AsyncAutoReloadConfig.class);

        cfg.addReloadListener(new ReloadListener() {
            public void reloadPerformed(ReloadEvent event) {
                notifyReload();
            }
        });

        assertEquals(Integer.valueOf(5), cfg.someValue());

        systemPropertiesHelper.setProperty(propKey, "5");
        waitForReload(DELAY);

        assertEquals(Integer.valueOf(5), cfg.someValue());

        systemPropertiesHelper.setProperty(propKey, "20");
        waitForReload(DELAY);

        assertEquals(Integer.valueOf(20), cfg.someValue());

        systemPropertiesHelper.cleanProperty(propKey);
        waitForReload(DELAY);

        assertEquals(Integer.valueOf(5), cfg.someValue());

        systemPropertiesHelper.setProperty(propKey, "30");
        waitForReload(DELAY);

        assertEquals(Integer.valueOf(30), cfg.someValue());
    }

    @After
    public void cleanSystemProperties() {
        systemPropertiesHelper.cleanNonDefaultSysProps();
    }
}
