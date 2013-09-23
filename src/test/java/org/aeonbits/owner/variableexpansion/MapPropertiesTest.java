package org.aeonbits.owner.variableexpansion;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Config.Sources;
import org.junit.Test;

import java.util.Map;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;

public class MapPropertiesTest {
	@Sources({"file:${user.dir}/src/test/resources/maps.properties"})
	public static interface MapConfig extends Config {
		public String nonmap();
		
		@MapValue("map1")
		public Map<String, String> map1();
		
		@MapValue("map2")
		public Map<String, String> map2();
	}

	@Test
	public void testPropertyMaps() {
		MapConfig config = ConfigFactory.create(MapConfig.class);
		
		assertEquals("nope", config.nonmap());
		
		assertNotNull(config.map1());
		assertNotNull(config.map2());
		
		assertEquals("a", config.map1().get("value1"));
		assertEquals("b", config.map1().get("value2"));
		assertEquals("1", config.map2().get("value3"));
		assertEquals("2", config.map2().get("value4"));
	}
}
