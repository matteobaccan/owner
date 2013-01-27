package org.aeonbits.owner;

import static org.aeonbits.owner.PropertiesLoader.getInputStream;
import static org.aeonbits.owner.Util.prohibitInstantiation;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

public class PropertiesFiller implements Closeable {

    private InputStream sourceStream;

    public PropertiesFiller() {
        prohibitInstantiation();
    }

    public static PropertiesFiller create(URL propertyFile) throws IOException {
        InputStream useStream = null;
        if (propertyFile != null)
            useStream = getInputStream(propertyFile);
        return useStream != null ? createFillerByResourceType(propertyFile, useStream) : null;
    }

    private static PropertiesFiller createFillerByResourceType(URL propertyFile,
            InputStream useStream) {
        PropertiesFiller selectedFiller = null;
        if(propertyFile.getPath().endsWith(".xml")) {
            selectedFiller = new XmlPropertiesFiller(useStream);
        } else {
            selectedFiller = new PropertiesFiller(useStream);
        }
        return selectedFiller;
    }

    @Override
    public final void close() throws IOException {
        sourceStream.close();
    }

    public void load(Properties properties) throws IOException {
        if (properties == null)
            throw new IllegalArgumentException();
        properties.load(sourceStream);
    }

    protected final InputStream getSourceStream() {
        return sourceStream;
    }

    protected PropertiesFiller(InputStream stream) {
        this.sourceStream = stream;
    }
}
