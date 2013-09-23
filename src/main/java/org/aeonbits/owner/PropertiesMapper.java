/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.Config.Key;

import java.lang.reflect.Method;
import java.util.*;

import static org.aeonbits.owner.Util.prohibitInstantiation;

/**
 * Maps methods to properties keys and defaultValues. Maps a class to default property values.
 *
 * @author Luigi R. Viggiano
 */
abstract class PropertiesMapper {

    PropertiesMapper() {
        prohibitInstantiation();
    }

    static String key(Method method) {
        Key key = method.getAnnotation(Key.class);
        return (key == null) ? method.getName() : key.value();
    }

    static String defaultValue(Method method) {
        DefaultValue defaultValue = method.getAnnotation(DefaultValue.class);
        return defaultValue != null ? defaultValue.value() : null;
    }

    static void defaults(Properties properties, Class<? extends Config> clazz) {
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            String key = key(method);
            String value = defaultValue(method);
            if (value != null)
                properties.put(key, value);
        }
    }

	static String mapValue(Method method) {
		Config.MapValue mapValue = method.getAnnotation(Config.MapValue.class);
		return mapValue != null ? mapValue.value() : null;
	}

	static void mapValues(Properties inputProperties, Properties outputProperties, Class<? extends Config> clazz) {
		Method[] methods = clazz.getMethods();
		for (Method method : methods) {
			String value = mapValue(method);
			if (value != null) {
				Map<String, String> map = new HashMap<String, String>();
				List<String> keysToRemove = new ArrayList<String>();
				for(java.util.Map.Entry<Object, Object> property : inputProperties.entrySet()) {
					String propKey = (String) property.getKey();
					if (propKey.startsWith(value)) {
						String realKey = propKey.replaceFirst(value + ".", "");
						map.put(realKey, (String) property.getValue());
						keysToRemove.add(propKey);
					}
				}

				for(String key : keysToRemove) {
					inputProperties.remove(key);
				}
				outputProperties.put(value, map);
			}
		}
	}
}
