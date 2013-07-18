package org.aeonbits.owner.loadstrategies;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.ConfigURLStreamHandlerForTest;
import org.aeonbits.owner.PropertiesManagerForTest;
import org.aeonbits.owner.VariablesExpanderForTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Luigi R. Viggiano
 */
@RunWith(MockitoJUnitRunner.class)
public class DefaultLoadStrategyTest {
    @Mock
    private ScheduledExecutorService scheduler;
    private VariablesExpanderForTest expander = new VariablesExpanderForTest(new Properties());

    static interface SampleConfig extends Config {
        String testKey();
    }

    @Test
    public void shouldReturnTheResourceForAClass() throws IOException {
        ConfigURLStreamHandlerForTest handler = new ConfigURLStreamHandlerForTest(SampleConfig.class.getClassLoader(),
                new VariablesExpanderForTest(new Properties()));

        ConfigURLStreamHandlerForTest spy = spy(handler);

        PropertiesManagerForTest manager = new PropertiesManagerForTest(SampleConfig.class, new Properties(),
                scheduler, expander);

        manager.doLoad(spy);
        URL expected = new URL(null, 
                "classpath:org/aeonbits/owner/loadstrategies/DefaultLoadStrategyTest$SampleConfig.properties", handler);
        verify(spy, times(1)).openConnection(eq(expected));
    }

    @Test
    public void shouldReturnTheValueFromTheAssociatedProperties() {
        SampleConfig config = ConfigFactory.create(SampleConfig.class);
        assertEquals("testValue", config.testKey());
    }

    @Test
    public void shouldReturnThePropertiesForTheClass() {
        PropertiesManagerForTest manager = new PropertiesManagerForTest(SampleConfig.class, new Properties(), scheduler, expander);
        Properties props = manager.load();
        assertNotNull(props);
        assertEquals("testValue", props.getProperty("testKey"));
    }

}
