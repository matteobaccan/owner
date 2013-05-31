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

import static org.aeonbits.owner.Config.LoadType;
import static org.aeonbits.owner.Config.LoadType.FIRST;
import static org.aeonbits.owner.ConfigURLStreamHandler.CLASSPATH_PROTOCOL;
import static org.aeonbits.owner.PropertiesMapper.defaults;
import static org.aeonbits.owner.Util.reverse;

/**
 * Loads properties for a class.
 *
 * @author Luigi R. Viggiano
 */
class PropertiesLoader {
    private static final SystemVariablesExpander expander = new SystemVariablesExpander();
    private final Class<? extends Config> clazz;
    private final Map<?, ?>[] imports;

    PropertiesLoader(Class<? extends Config> clazz, Map<?, ?>... imports) {
        this.clazz = clazz;
        this.imports = imports;
    }

    Properties load() {
        try {
            Properties props = defaults(clazz);
            merge(props, reverse(imports));
            ConfigURLStreamHandler handler = new ConfigURLStreamHandler(clazz.getClassLoader(), expander);
            Properties loadedFromFile = doLoad(handler);
            merge(props, loadedFromFile);
            return props;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Properties doLoad(ConfigURLStreamHandler handler) throws IOException {
        Sources sources = clazz.getAnnotation(Sources.class);
        LoadPolicy loadPolicy = clazz.getAnnotation(LoadPolicy.class);
        LoadType loadType = (loadPolicy != null) ? loadPolicy.value() : FIRST;
        if (sources == null)
            return loadDefaultProperties(handler);
        else
            return loadType.load(sources, handler);
    }

    private Properties loadDefaultProperties(ConfigURLStreamHandler handler) throws IOException {
        String spec = CLASSPATH_PROTOCOL + ":" + clazz.getName().replace('.', '/') + ".properties";
        InputStream inputStream = getInputStream(new URL(null, spec, handler));
        try {
            return properties(inputStream);
        } finally {
            close(inputStream);
        }
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
        if (stream != null)
            props.load(stream);
        return props;
    }

    static void close(InputStream inputStream) throws IOException {
        if (inputStream != null)
            inputStream.close();
    }

}
