package org.aeonbits.owner.issues;

import org.aeonbits.owner.Accessible;
import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * See: https://github.com/matteobaccan/owner/issues/405
 *
 * {@link Accessible#fill(Map)} exports every property loaded from the sources; only the keys mapped by the
 * methods declared in the config interface can be extracted with {@link Accessible#getProperty(String)}.
 *
 * @author Matteo Baccan
 */
public class Issue405 {

    interface MyProperties extends Config, Accessible {
        @Key("declared.key")
        String declaredKey();

        @DefaultValue("default value")
        String declaredWithDefault();
    }

    private static Properties importedProperties() {
        Properties props = new Properties();
        props.setProperty("declared.key", "declared value");
        props.setProperty("undeclared.key", "undeclared value");
        return props;
    }

    @Test
    public void fillExportsEveryLoadedPropertyIncludingUndeclaredOnes() {
        MyProperties cfg = ConfigFactory.create(MyProperties.class, importedProperties());

        Map<String, String> map = new HashMap<String, String>();
        cfg.fill(map);

        assertEquals(3, map.size());
        assertEquals("declared value", map.get("declared.key"));
        assertEquals("default value", map.get("declaredWithDefault"));
        assertEquals("undeclared value", map.get("undeclared.key"));
    }

    @Test
    public void onlyDeclaredPropertiesCanBeExtractedThroughTheInterfaceMethods() {
        MyProperties cfg = ConfigFactory.create(MyProperties.class, importedProperties());

        Map<String, String> map = fillDeclaredOnly(MyProperties.class, cfg);

        assertEquals(2, map.size());
        assertEquals("declared value", map.get("declared.key"));
        assertEquals("default value", map.get("declaredWithDefault"));
        assertFalse(map.containsKey("undeclared.key"));
    }

    /**
     * Fills a map with only the properties mapped by the methods declared in the given config interface:
     * the key of each method is the {@link Config.Key} annotation value, or the method name when the
     * annotation is absent.
     */
    private static Map<String, String> fillDeclaredOnly(Class<?> configInterface, Accessible config) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (Method method : configInterface.getDeclaredMethods()) {
            Config.Key key = method.getAnnotation(Config.Key.class);
            String name = (key != null) ? key.value() : method.getName();
            String value = config.getProperty(name);
            if (value != null)
                map.put(name, value);
        }
        return map;
    }
}
