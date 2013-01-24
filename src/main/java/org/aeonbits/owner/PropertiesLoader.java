/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.Properties;

import static org.aeonbits.owner.ConfigURLStreamHandler.CLASSPATH_PROTOCOL;
import static org.aeonbits.owner.PropertiesMapper.defaults;

/**
 * @author Luigi R. Viggiano
 */
public class PropertiesLoader {
    private static final SystemVariablesExpander expander = new SystemVariablesExpander();

    static Properties loadPropertiesFor(Class<? extends Config> clazz, Map<?, ?>... imports) {
        ConfigURLStreamHandler handler = new ConfigURLStreamHandler(clazz.getClassLoader(), expander);
        try {
            Properties props = defaults(clazz);
            merge(props, imports);
            Properties loadedFromFile = getPropertiesFor(clazz, handler);
            merge(props, loadedFromFile);
            return props;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static Properties getPropertiesFor(Class<?> clazz, ConfigURLStreamHandler handler) throws IOException {
        Sources sources = clazz.getAnnotation(Sources.class);
        LoadPolicy loadPolicy = clazz.getAnnotation(LoadPolicy.class);
        LoadType loadType = (loadPolicy != null) ? loadPolicy.value() : LoadType.FIRST;
        if (sources == null)
            return getDefaultProperties(clazz, handler);
        else
            return loadType.load(sources, handler);
    }

    private static Properties getDefaultProperties(Class<?> clazz,
                                                   ConfigURLStreamHandler handler) throws IOException {
        String spec = CLASSPATH_PROTOCOL + ":" + clazz.getName().replace('.', '/') + ".properties";
        return properties(getInputStream(new URL(null, spec, handler)));
    }


    static InputStream getInputStream(URL url) throws IOException {
        URLConnection conn = url.openConnection();
        if (conn == null)
            return null;
        return conn.getInputStream();
    }

    private static void merge(Properties results, Map<?, ?>... inputs) {
        for (Map<?, ?> input : inputs)
            results.putAll(input);
    }

    static Properties properties(InputStream stream) throws IOException {
        Properties props = new Properties();
        load(props, stream);
        return props;
    }

    private static void load(Properties props, InputStream stream) throws IOException {
        if (stream != null) {
            try {
                props.load(stream);
            } finally {
                stream.close();
            }
        }
    }

}
