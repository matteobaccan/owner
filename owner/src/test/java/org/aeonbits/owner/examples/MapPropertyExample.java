/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.examples;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Converter;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Luigi R. Viggiano
 */
public class MapPropertyExample {
    interface MyConfig extends Config {
        @Separator(";")
        @DefaultValue(
                "name : Dante Alighieri,    book : Divine Comedy, birth_year: 1265, death_year: 1321;" +
                "name : Alessandro Manzoni, book : The Betrothed, birth_year: 1785, death_year: 1873")
        @ConverterClass(MapPropertyConverter.class)
        Map<String, String>[] authors();
    }

    public static class MapPropertyConverter implements Converter<Map<String,String>> {
        public Map<String, String> convert(Method method, String input) {
            Map<String, String> result = new LinkedHashMap<String, String>();
            String[] chunks = input.split(",", -1);
            for (String chunk : chunks) {
                String[] entry = chunk.split(":", -1);
                String key = entry[0].trim();
                String value = entry[1].trim();
                result.put(key, value);
            }
            return result;
        }
    }

    public static void main(String[] args) {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        Map<String, String>[] authors = cfg.authors();
        for (Map<String,String> author : authors) {
            for (Map.Entry<String, String> entry : author.entrySet())
                System.out.printf("%s:\t%s\n", entry.getKey(), entry.getValue());
            System.out.println();
        }
    }
}
