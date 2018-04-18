/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.loaders.Loader;
import org.aeonbits.owner.loaders.PropertiesLoader;
import org.aeonbits.owner.loaders.XMLLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import static org.aeonbits.owner.util.UtilTest.fileFromURI;
import static org.junit.Assert.assertEquals;

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

    interface MyConfigDefaultSpec extends Config {
        String foo();
    }

    @Before
    public void before() throws URISyntaxException, IOException {
        target = fileFromURI(SPEC);
        target.getParentFile().mkdirs();
        target.createNewFile();
    }

    @After
    public void after() {
        target.delete();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testProxyCreationWhenLoaderCantBeRisolvedForGivenURL() {
        Factory factory = new DefaultFactory(scheduler, new Properties()) {{
            loadersManager.clear();
        }};
        factory.registerLoader(new XMLLoader());
        factory.create(MyConfig.class);
    }


    @Test
    public void testProxyCreationInNormalSituation() {
        Factory factory = new DefaultFactory(scheduler, new Properties());
        MyConfigDefaultSpec cfg = factory.create(MyConfigDefaultSpec.class);
        assertEquals("bar", cfg.foo());
    }

    @Test
    public void testProxyCreationWhenLoaderReturnsFooBarAsDefaultSpec() {
        Factory factory = new DefaultFactory(scheduler, new Properties()) {{
            loadersManager.clear();
        }};

        factory.registerLoader(new PropertiesLoader());
        factory.registerLoader(new PropertiesLoader() {
            @Override
            public String defaultSpecFor(String uriPrefix) {
                return uriPrefix + ".foobar";
            }
        });

        MyConfigDefaultSpec cfg = factory.create(MyConfigDefaultSpec.class);
        assertEquals("foobar", cfg.foo());
    }

    @Test
    public void testProxyCreationWhenLoaderReturnsNullAsDefaultSpec() {
        Factory factory = new DefaultFactory(scheduler, new Properties()) {{
            loadersManager.clear();
        }};

        factory.registerLoader(new PropertiesLoader());
        factory.registerLoader(new PropertiesLoader() {
            @Override
            public String defaultSpecFor(String uriPrefix) {
                return null;
            }
        });

        MyConfigDefaultSpec cfg = factory.create(MyConfigDefaultSpec.class);
        assertEquals("bar", cfg.foo());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRegisterNull() {
        Factory factory = ConfigFactory.newInstance();
        factory.registerLoader(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRegisterNullOnSingleton() {
        ConfigFactory.registerLoader(null);
    }

    @Test
    public void testRegisterNonNullLoaderOnSingleton() {
        ConfigFactory.registerLoader(new LoaderThatDoesNothing());
    }

    public static class LoaderThatDoesNothing implements Loader {
        public boolean accept(URI uri) {
            return false;
        }

        public void load(Properties result, URI uri) throws IOException {
        }

        public String defaultSpecFor(String urlPrefix) {
            return null;
        }
    }

}
