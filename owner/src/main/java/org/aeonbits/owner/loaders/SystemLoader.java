package org.aeonbits.owner.loaders;

import java.io.IOException;
import java.net.URI;
import java.util.Properties;

public class SystemLoader implements Loader {

    private static final String SYSTEM_PROPERTIES_URI = "system:properties";
    private static final String ENVIRONMENT_VARIABLES_URI = "system:env";

    public boolean accept(URI uri) {
        String path = uri.toString();
        return SYSTEM_PROPERTIES_URI.equals(path) || ENVIRONMENT_VARIABLES_URI.equals(path);
    }

    public void load(Properties result, URI uri) throws IOException {
        String path = uri.toString();
        if (SYSTEM_PROPERTIES_URI.equals(path)) {
            result.putAll(System.getProperties());
        } else if (ENVIRONMENT_VARIABLES_URI.equals(path)) {
            result.putAll(System.getenv());
        } else {
            throw new IOException("Cannot identify URI " + path);
        }
    }

    public String defaultSpecFor(String uriPrefix) {
        return null;
    }
}
