package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class Issue184 {

    private static final String KEY = "a.key";

    public interface MyConfig extends Config {
        @Key(KEY)
        @DefaultValue("1")
        Integer getValue();
    }

    @Test
    public void testConfigImportWithNullValue() throws Exception {
        Map<String,String> propsMapWithNullValue = new HashMap<String,String>();
        propsMapWithNullValue.put(KEY, null);

        try {
            ConfigFactory.create(Issue184.MyConfig.class, propsMapWithNullValue);
            fail("A null value should result in an exception");
        }
        catch(IllegalArgumentException e){
            assertTrue(e.getMessage().contains(KEY));
        }
    }

    @Test
    public void testConfigImportWithNullKey() throws Exception {
        Map<String,String> propsMapWithNullKey = new HashMap<String,String>();
        propsMapWithNullKey.put(null, "smurf");

        try {
            ConfigFactory.create(Issue184.MyConfig.class, propsMapWithNullKey);
            fail("A null key should result in an exception");
        }
        catch(IllegalArgumentException e){
            assertTrue(e.getMessage().contains("null"));
        }
    }
}
