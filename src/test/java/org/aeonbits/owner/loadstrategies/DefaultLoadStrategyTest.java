/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loadstrategies;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.LoadersManagerForTest;
import org.aeonbits.owner.PropertiesManagerForTest;
import org.aeonbits.owner.VariablesExpanderForTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Luigi R. Viggiano
 */
@RunWith(MockitoJUnitRunner.class)
public class DefaultLoadStrategyTest extends LoadStrategyTestBase {
    @Mock
    private ScheduledExecutorService scheduler;
    @Spy
    private LoadersManagerForTest loaders = new LoadersManagerForTest();
    private VariablesExpanderForTest expander = new VariablesExpanderForTest(new Properties());

    static interface SampleConfig extends Config {
        String testKey();
    }

    @Test
    public void shouldReturnTheResourceForAClass() throws IOException {
        PropertiesManagerForTest manager = 
                new PropertiesManagerForTest(SampleConfig.class, new Properties(), scheduler, expander, loaders);

        manager.load();
        
        verify(loaders, times(1)).findLoader(any(URL.class));
        verify(loaders, times(1)).findLoader(argThat(urlMatches(
                "org/aeonbits/owner/loadstrategies/DefaultLoadStrategyTest$SampleConfig.properties")));
    }

    @Test
    public void shouldReturnTheValueFromTheAssociatedProperties() {
        SampleConfig config = ConfigFactory.create(SampleConfig.class);
        assertEquals("testValue", config.testKey());
    }

    @Test
    public void shouldReturnThePropertiesForTheClass() {
        PropertiesManagerForTest manager = 
                new PropertiesManagerForTest(SampleConfig.class, new Properties(), scheduler, expander, loaders);
        Properties props = manager.load();
        assertNotNull(props);
        assertEquals("testValue", props.getProperty("testKey"));
    }

}
