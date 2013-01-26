/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.Sources;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import static org.aeonbits.owner.PropertiesLoader.getInputStream;
import static org.aeonbits.owner.PropertiesLoader.properties;
import static org.aeonbits.owner.Util.reverse;

/**
 * @author Luigi R. Viggiano
 */
public enum LoadType {
    FIRST {
        @Override
        Properties load(Sources sources, ConfigURLStreamHandler handler) throws MalformedURLException {
            String[] values = sources.value();
            for (String source : values) {
                URL url = new URL(null, source, handler);
                try {
                    InputStream stream = getInputStream(url);
                    if (stream != null)
                        return properties(stream);
                } catch (IOException ex) {
                    // ignore: happens when a file specified in the sources is not found or cannot be read.
                }
            }
            return new Properties();
        }
    },

    MERGE {
        @Override
        Properties load(Sources sources, ConfigURLStreamHandler handler) throws MalformedURLException {
            String[] values = reverse(sources.value());
            Properties result = new Properties();
            for (String source : values) {
                URL url = new URL(null, source, handler);
                try {
                    InputStream stream = getInputStream(url);
                    if (stream != null)
                        result.load(stream);
                } catch (IOException ex) {
                    // ignore: happens when a file specified in the sources is not found or cannot be read.
                }
            }
            return result;
        }
    };

    abstract Properties load(Sources sources, ConfigURLStreamHandler handler) throws MalformedURLException;
}
