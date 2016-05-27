/**
 *
 */
package org.aeonbits.owner.loaders;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Properties;

import org.aeonbits.owner.loaders.Loader;
import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * A {@link Loader loader} able to read properties from YAML files.
 *
 * @since 1.0.10
 * @author Cedric Beurtheret
 */
@SuppressWarnings("serial")
@MetaInfServices(Loader.class)
public class YAMLLoader implements Loader {

	private static final ObjectMapper mapper = new ObjectMapper(
			new YAMLFactory());

	static {
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);

		mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
		mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
		mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.aeonbits.owner.loaders.Loader#accept(java.net.URI)
	 */
	public boolean accept(URI uri) {
		try {
			URL url = uri.toURL();
			return url.getFile().toLowerCase().endsWith(".yaml");
		} catch (MalformedURLException ex) {
			return false;
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.aeonbits.owner.loaders.Loader#defaultSpecFor(java.lang.String)
	 */
	public String defaultSpecFor(String urlPrefix) {
		return urlPrefix + ".yaml";
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.aeonbits.owner.loaders.Loader#load(java.util.Properties, java.net.URI)
	 */
	public void load(Properties result, URI uri) throws IOException {
		result.putAll(mapper.readValue(new File(uri.toURL().getFile()),
				Properties.class));
	}

}
