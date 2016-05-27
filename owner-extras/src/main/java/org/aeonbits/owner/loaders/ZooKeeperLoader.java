/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loaders;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.Properties;

import org.kohsuke.MetaInfServices;

import sun.net.www.protocol.zookeeper.ZooKeeperURLConnection.ZooKeeperStream;
import sun.net.www.protocol.zookeeper.ZooKeeperURLStreamHandler;

/**
 * @author Koray Sariteke
 * @author Luigi R. Viggiano
 */
@SuppressWarnings("serial")
@MetaInfServices(Loader.class)
public class ZooKeeperLoader implements Loader {

	static {
		// Registering ZooKeeperURLHandler
		URL.setURLStreamHandlerFactory(new URLStreamHandlerFactory() {
			
			public URLStreamHandler createURLStreamHandler(String protocol) {
		        if ( protocol.equalsIgnoreCase(ZooKeeperURLStreamHandler.PROTOCOL) ) 
		            return new ZooKeeperURLStreamHandler(); 
		        else 
		            return null; 
			}
		});
	}

    public boolean accept(URI uri) {
        return uri.getScheme().equals(ZooKeeperURLStreamHandler.PROTOCOL);
    }

    public void load(Properties result, URI uri) throws IOException {
        InputStream input = uri.toURL().openStream();
        try {
            load(result, input);
        } finally {
            input.close();
        }
    }

    void load(Properties result, InputStream input) throws IOException {
    	if (input instanceof ZooKeeperStream) {
            ZooKeeperStream zkStream = (ZooKeeperStream) input;
            result.putAll(zkStream.pairs());
		}
    }

    public String defaultSpecFor(String urlPrefix) {
        return urlPrefix;
    }
}
