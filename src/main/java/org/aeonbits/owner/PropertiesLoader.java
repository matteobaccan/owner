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
import static org.aeonbits.owner.Util.prohibitInstantiation;

/**
 * Loads properties for a class.
 * @author Luigi R. Viggiano
 */
abstract class PropertiesLoader {
    private static final SystemVariablesExpander expander = new SystemVariablesExpander();

    PropertiesLoader() {
        prohibitInstantiation();
    }

    static Properties load(Class<? extends Config> clazz, Map<?, ?>... imports) {
        try {
            Properties props = defaults(clazz);
            merge(props, imports);
            ConfigURLStreamHandler handler = new ConfigURLStreamHandler(clazz.getClassLoader(), expander);
            Properties loadedFromFile = doLoad(clazz, handler);
            merge(props, loadedFromFile);
            return props;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static Properties doLoad(Class<?> clazz, ConfigURLStreamHandler handler) throws IOException {
        Sources sources = clazz.getAnnotation(Sources.class);
        LoadPolicy loadPolicy = clazz.getAnnotation(LoadPolicy.class);
        LoadType loadType = (loadPolicy != null) ? loadPolicy.value() : LoadType.FIRST;
        if (sources == null)
            return loadDefaultProperties(clazz, handler);
        else
            return loadType.load(sources, handler);
    }

    private static Properties loadDefaultProperties(Class<?> clazz,
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
        if (stream != null) {
            try {
                props.load(stream);
            } finally {
                stream.close();
            }
        }
        return props;
    }
}
