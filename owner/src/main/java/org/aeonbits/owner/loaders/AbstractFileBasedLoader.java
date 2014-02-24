package org.aeonbits.owner.loaders;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Properties;

public abstract class AbstractFileBasedLoader implements Loader {
	
	private static final long serialVersionUID = -7207237322627631047L;

	public void load(Properties result, InputStream input) throws IOException {
        result.load(input);
    }
	
    public void load(Properties result, URI uri) throws ConfigurationSourceNotFoundException {
    	try {
    		InputStream stream = uri.toURL().openStream();
    		doLoadInternal(result, stream);
    	} catch(IOException ioe) {
    		throw new ConfigurationSourceNotFoundException(ioe);
    	}
    }
    
	protected abstract void doLoadInternal(Properties result, InputStream input) throws IOException;
}
