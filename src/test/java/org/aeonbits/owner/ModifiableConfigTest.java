package org.aeonbits.owner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Luigi R. Viggiano
 */
public class ModifiableConfigTest {

    interface ModifiableConfig extends Config, Modifiable {
        @DefaultValue("18")
        public Integer minAge();
        public Integer maxAge();
    }

    @Test
    public void testSetProperty() {
        ModifiableConfig cfg = ConfigFactory.create(ModifiableConfig.class);
        assertEquals(Integer.valueOf(18), cfg.minAge());
        String oldValue = cfg.setProperty("minAge", "21");
        assertEquals("18", oldValue);
        assertEquals(Integer.valueOf(21), cfg.minAge());
    }

    @Test
    public void testSetPropertyThatWasNull() {
        ModifiableConfig cfg = ConfigFactory.create(ModifiableConfig.class);
        assertNull(cfg.maxAge());
        String oldValue = cfg.setProperty("maxAge", "999");
        assertNull(oldValue);
        assertEquals(Integer.valueOf(999), cfg.maxAge());
    }

    @Test
    public void testSetPropertyWithNull() {
        ModifiableConfig cfg = ConfigFactory.create(ModifiableConfig.class);
        assertEquals(Integer.valueOf(18), cfg.minAge());
        String oldValue = cfg.setProperty("minAge", null);
        assertEquals("18", oldValue);
        assertNull(cfg.minAge());
    }

    @Test
    public void testRemoveProperty() {
        ModifiableConfig cfg = ConfigFactory.create(ModifiableConfig.class);
        assertEquals(Integer.valueOf(18), cfg.minAge());
        String oldValue = cfg.removeProperty("minAge");
        assertEquals("18", oldValue);
        assertNull(cfg.minAge());
    }

}
