package org.aeonbits.owner.loaders;

import java.net.URI;
import java.util.Properties;

import org.aeonbits.owner.Config;

/**
 * <p>Support class for custom named loaders, which expects the @Sources URI to be <code>owner://&lt;loadername&gt;#&lt;optional argument&gt;</code></p>
 * <p>the default spec for a {@link Loader} with a name of "myCustomLoader" and a {@link Config} <i>foo.bar.MyConfig</i> would become<br/>
 * <i>owner://myCustomLoader#foo/bar/MyConfig</i><br/>
 * 
 * 
 * @author philippgaschuetz
 *
 */
public abstract class AbstractNamedCustomLoader implements Loader {
	
	private static final long serialVersionUID = 838201064524631453L;

	private static final String CLASSPATH_PROTOCOL = "classpath:";
	public final static String CUSTOMLOADER_SCHEME = "owner";
	
	private final String loaderName;
	
	public AbstractNamedCustomLoader(String loaderName) {
		if(loaderName == null || loaderName.length() == 0) {
			throw new IllegalArgumentException("Loadername cannot be empty");
		}
		this.loaderName = loaderName;
	}
	
	public boolean accept(URI uri) {
		return CUSTOMLOADER_SCHEME.equals(uri.getScheme()) && loaderName.equals(uri.getHost());
	}
	
	public String defaultSpecFor(String urlPrefix) {
		if(urlPrefix.startsWith(CLASSPATH_PROTOCOL)) {
			urlPrefix = urlPrefix.substring( CLASSPATH_PROTOCOL.length() );
		}
		
		String spec = String.format("%s://%s#%s", CUSTOMLOADER_SCHEME, loaderName, urlPrefix);
		return spec;
	}
	
	public void load(Properties result, URI uri) throws ConfigurationSourceNotFoundException {
		String argument = uri.getFragment();
		doLoadInternal(result, argument);
	}
	
	protected abstract void doLoadInternal(Properties result, String arg) throws ConfigurationSourceNotFoundException;
	
}
