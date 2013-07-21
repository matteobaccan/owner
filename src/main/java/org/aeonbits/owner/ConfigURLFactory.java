package org.aeonbits.owner;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author Luigi R. Viggiano
 */
class ConfigURLFactory {
    private static final String CLASSPATH_PROTOCOL = "classpath:";
    private final ClassLoader classLoader;
    private final VariablesExpander expander;

    ConfigURLFactory(ClassLoader classLoader, VariablesExpander expander) {
        this.classLoader = classLoader;
        this.expander = expander;
    }

    URL newURL(String spec) throws MalformedURLException {
        String expanded = expand(spec);
        URL url;
        if (expanded.startsWith(CLASSPATH_PROTOCOL)) {
            String path = expanded.substring(CLASSPATH_PROTOCOL.length());
            url = classLoader.getResource(path);
            if (url == null)
                return null;
        } else {
            url = new URL(expanded);
        }
        return new URL(url.getProtocol(), url.getHost(), url.getPort(), expand(url.getPath()));
    }
    
    private String expand(String path) {
        return expander.expand(path);
    }

    public String toClasspathURLSpec(String name) {
        return CLASSPATH_PROTOCOL + name.replace('.', '/');
    }
}
