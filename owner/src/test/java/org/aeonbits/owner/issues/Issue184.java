package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class Issue184 {

    private static final String KEY = "null.value.key";

    public interface MyConfig extends Config {
        @Key(KEY)
        @DefaultValue("1")
        Integer getNullValueKey();
    }

    @Test
    public void testConfigImportWithNullValue() throws Exception {
        Map<String,String> propsMapWithNullValue = new HashMap<String,String>();
        propsMapWithNullValue.put("null.value.key", null);

        try {
            ConfigFactory.create(Issue184.MyConfig.class, propsMapWithNullValue);
        }
        catch(IllegalArgumentException e){
            assertTrue(e.getMessage().contains(KEY));
        }
    }

    @Test
    public void testConfigImportWithNullKey() throws Exception {
        Map<String,String> propsMapWithNullValue = new HashMap<String,String>();
        propsMapWithNullValue.put(null, "smurf");

        try {
            ConfigFactory.create(Issue184.MyConfig.class, propsMapWithNullValue);
        }
        catch(IllegalArgumentException e){
            assertTrue(e.getMessage().contains("null"));
        }
    }
}
