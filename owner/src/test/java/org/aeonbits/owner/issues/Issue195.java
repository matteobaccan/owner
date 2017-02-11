package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class Issue195 {

    private static final String KEY = "some.key";

    public interface MyConfig extends Config {
        @Key(KEY)
        @DefaultValue("1")
        Integer getSomeValue();
    }

    @Test
    public void testConfigImportMapWithNonStringValue() throws Exception {
        HashMap<String, Integer> propsMapWithIntegerValue = new HashMap<String,Integer>();
        propsMapWithIntegerValue.put(KEY, new Integer("42"));

        try {
            ConfigFactory.create(MyConfig.class, propsMapWithIntegerValue);
            fail("A non-string value should result in an exception");
        }
        catch(IllegalArgumentException e){
            assertTrue(e.getMessage().contains(KEY));
        }
    }
}