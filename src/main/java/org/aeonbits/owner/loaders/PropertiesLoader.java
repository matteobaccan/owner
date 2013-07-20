package org.aeonbits.owner.loaders;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

/**
 * @author luigi
 */
public class PropertiesLoader implements Loader {
    public boolean accept(URL url) {
        return true;
    }

    public void load(Properties result, InputStream input) throws IOException {
        result.load(input);
    }
}
