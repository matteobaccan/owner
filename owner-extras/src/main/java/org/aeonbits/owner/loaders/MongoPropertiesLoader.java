package org.aeonbits.owner.loaders;

import java.io.IOException;
import java.net.URI;
import java.util.Properties;

import org.aeonbits.owner.loaders.Loader;
import org.bson.Document;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

/**

If you have a collection of key value pairs in mongo, you can use this class to pull values from it.

	Loader loader = new MongoPropertiesLoader();
	ConfigFactory.registerLoader(loader);
	
	CoreConfig cfg = ConfigCache.getOrCreate(CoreConfig.class);


This class assumes that the collection of config values live in the "configuration" collection.  If different, set the property configurationCollectionName.

This class needs a full mongo url in sources:

	@Sources({"mongodb://user:password@master.hostname:11111/dbname","classpath:server.properties"})


Code courtesy of Katasi LLC.

*/


public class MongoPropertiesLoader implements Loader {

	private String configurationCollectionName = "configuration";
	
	@Override
	public boolean accept(URI uri) {
		return ("mongodb".equals(uri.getScheme()));
	}

	@Override
	public String defaultSpecFor(String urlPrefix) {
		return urlPrefix;
	}

	@Override
	public void load(Properties props, URI uri) throws IOException {
		
		MongoClientURI mongoURI = new MongoClientURI(uri.toString());
		MongoClient mongoClient = new MongoClient(mongoURI);
		MongoDatabase database = mongoClient.getDatabase(mongoURI.getDatabase());
		MongoCollection<Document> collection = database.getCollection(getConfigurationCollectionName());
		MongoCursor<Document> find = collection.find().iterator();
		while (find.hasNext()) {
			Document next = find.next();
			String key = next.getString("key");
			Object value = next.get("value");
			props.setProperty(key, String.valueOf(value));
		}
	}

	public String getConfigurationCollectionName() {
		return configurationCollectionName;
	}

	public void setConfigurationCollectionName(String configurationCollectionName) {
		this.configurationCollectionName = configurationCollectionName;
	}

}
