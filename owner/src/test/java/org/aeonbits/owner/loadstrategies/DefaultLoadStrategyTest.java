/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loadstrategies;

import org.aeonbits.owner.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.net.URI;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
    private final LoadersManagerForTest loaders = new LoadersManagerForTest();
    private final VariablesExpanderForTest expander = new VariablesExpanderForTest(new Properties());

    static interface SampleConfig extends Config {
        String testKey();
    }

    @Test
    public void shouldReturnTheResourceForAClass() throws IOException {
        PropertiesManagerForTest manager = 
                new PropertiesManagerForTest(SampleConfig.class, new Properties(), scheduler, expander, loaders);

        manager.load();
        
        verify(loaders, times(1)).findLoader(any(URI.class));
        verify(loaders, times(1)).findLoader(argThat(uriMatches(
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
