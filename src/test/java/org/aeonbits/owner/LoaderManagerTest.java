/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.loaders.XMLLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import static org.aeonbits.owner.UtilTest.fileFromURL;

/**
 * @author Luigi R. Viggiano
 */
public class LoaderManagerTest implements TestConstants {
    private static final String SPEC = "file:" + RESOURCES_DIR + "/LoaderManagerTest.properties";

    @Mock
    private ScheduledExecutorService scheduler;
    private File target;

    @Sources(SPEC)
    interface MyConfig extends Config {
        String foo();
    }

    @Before
    public void before() throws IOException {
        target = fileFromURL(SPEC);
        target.createNewFile();
    }

    @After
    public void after() {
        target.delete();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testProxyCreationWhenLoaderCantBeRisolvedForGivenURL() {
        ConfigFactoryInstanceImpl instance = new ConfigFactoryInstanceImpl(scheduler, new Properties()) {
            @Override
            LoadersManager newLoadersManager() {
                return new LoadersManager() {{
                    loaders.clear();
                    registerLoader(new XMLLoader());
                }};
            }
        };
        instance.create(MyConfig.class);
    }

}
