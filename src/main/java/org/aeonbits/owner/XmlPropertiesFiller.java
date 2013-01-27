package org.aeonbits.owner;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class XmlPropertiesFiller extends PropertiesFiller {

    public XmlPropertiesFiller() {
        super();
    }

    @Override
    public void load(Properties properties) throws IOException {
        properties.loadFromXML(getSourceStream());
    }

    protected XmlPropertiesFiller(InputStream stream) {
        super(stream);
    }
}
