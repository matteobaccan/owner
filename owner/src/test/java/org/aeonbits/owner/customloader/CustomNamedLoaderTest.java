
package org.aeonbits.owner.customloader;

import static org.junit.Assert.*;

import java.util.Properties;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.loaders.AbstractNamedCustomLoader;
import org.aeonbits.owner.loaders.ConfigurationSourceNotFoundException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author pgaschuetz
 */
public class CustomNamedLoaderTest {
	
    
    public static interface SampleConfig extends Config {
        public String favoriteColor();
        @DefaultValue("Bob")
        public String favoriteName();
    }
    
    public static interface SampleConfigForSourceNotFoundException extends Config {
        public String favoriteColor();
        @DefaultValue("Bob")
        public String favoriteName();
    }
    
    @Sources({"owner://customloader"})
    public static interface SampleConfigWithSources extends Config {
        public String favoriteColor();
    }
    
    static class CustomNamedLoader extends AbstractNamedCustomLoader {

    	public CustomNamedLoader() {
			super("customloader");
		}
    	
    	@Override
    	protected void doLoadInternal(Properties result, String arg) throws ConfigurationSourceNotFoundException {
    		
    		if("org/aeonbits/owner/customloader/CustomNamedLoaderTest$SampleConfigForSourceNotFoundException".equals(arg)) {
    			throw new ConfigurationSourceNotFoundException();
    		} else {
        		String val = arg == null ? "lightblack" : "darkblack";
        		result.put("favoriteColor", val);
        		result.put("favoriteName", "Alice");	
    		}
    	}
    }
    
    @BeforeClass
    public static void beforeTestClass() {
    	ConfigFactory.resetLoaders();
    	ConfigFactory.registerLoader( new CustomNamedLoader() );
    }
    
    @AfterClass
    public static void afterTestClass() {
    	ConfigFactory.resetLoaders();
    }

    @Test
    public void testCustomNamedLoader() {
    	SampleConfig config = ConfigFactory.create(SampleConfig.class);
        assertEquals("darkblack", config.favoriteColor());
        assertEquals("Alice", config.favoriteName());
    }
    
    @Test
    public void testCustomNamedLoaderWithSources() {
    	SampleConfigWithSources config = ConfigFactory.create(SampleConfigWithSources.class);
        assertEquals("lightblack", config.favoriteColor());
    }
    
    @Test
    public void testCustomNamedLoaderWithInvalidSource() {
    	SampleConfigForSourceNotFoundException config = ConfigFactory.create(SampleConfigForSourceNotFoundException.class);
        assertNull(config.favoriteColor());
        assertEquals("Bob", config.favoriteName());
    }

}
