package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;

public class Issue195 {

    public interface MyConfig extends Config {
        @Key("key.with.default")
        @DefaultValue("1")
        Integer getValueWithDefault();
    }

    @Test
    public void testConfigImportWithNonStringValue() throws Exception {
        HashMap<String, Integer> propsMapWithIntegerValue = new HashMap<String,Integer>();
        propsMapWithIntegerValue.put("key.with.default", new Integer("42"));

        MyConfig config = ConfigFactory.create(MyConfig.class, propsMapWithIntegerValue);

        // Actual value here will be null
        assertEquals(new Integer(42), config.getValueWithDefault());
    }
}
