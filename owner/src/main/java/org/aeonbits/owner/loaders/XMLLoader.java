/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loaders;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.DefaultHandler2;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Properties;
import java.util.Stack;

/**
 * A {@link Loader loader} able to read properties from standard XML Java properties files, as well as user defined
 * XML properties files.
 *
 * @since 1.0.5
 * @author Luigi R. Viggiano
 */
public class XMLLoader implements Loader {

    private static final long serialVersionUID = -894351666332018767L;
    private transient SAXParserFactory factory = null;

    private synchronized SAXParserFactory factory() {
        if (factory == null) {
            factory = SAXParserFactory.newInstance();
            factory.setValidating(true);
            factory.setNamespaceAware(true);
        }
        return factory;
    }

    static class XmlToPropsHandler extends DefaultHandler2 {

        private static final String PROPS_DTD_URI =
                "http://java.sun.com/dtd/properties.dtd";

        private static final String PROPS_DTD =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<!-- DTD for properties -->" +
                        "<!ELEMENT properties ( comment?, entry* ) >" +
                        "<!ATTLIST properties version CDATA #FIXED \"1.0\">" +
                        "<!ELEMENT comment (#PCDATA) >" +
                        "<!ELEMENT entry (#PCDATA) >" +
                        "<!ATTLIST entry key CDATA #REQUIRED>";

        private boolean isJavaPropertiesFormat = false;
        private final Properties props;
        private final Stack<String> paths = new Stack<String>();
        private final Stack<StringBuilder> value = new Stack<StringBuilder>();

        @Override
        public InputSource resolveEntity(String name, String publicId, String baseURI,
                                         String systemId) throws SAXException, IOException {
            InputSource inputSource = null;
            if (systemId.equals(PROPS_DTD_URI)) {
                isJavaPropertiesFormat = true;
                inputSource = new InputSource(new StringReader(PROPS_DTD));
                inputSource.setSystemId(PROPS_DTD_URI);
            }
            return inputSource;
        }

        public XmlToPropsHandler(Properties props) {
            this.props = props;
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes attributes) throws SAXException {
            value.push(new StringBuilder());

            if (isJavaPropertiesFormat) {
                if ("entry".equals(qName))
                    paths.push(attributes.getValue("key"));
                else
                    paths.push(qName);
            } else {
                String path = (paths.size() == 0) ? qName : paths.peek() + "." + qName;
                paths.push(path);
                for (int i = 0; i < attributes.getLength(); i++) {
                    String attrName = attributes.getQName(i);
                    String attrValue = attributes.getValue(i);
                    props.setProperty(path + "." + attrName, attrValue);
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            value.peek().append(new String(ch, start, length));
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            String key = paths.peek();
            String propertyValue = this.value.peek().toString().trim();
            if (!propertyValue.isEmpty() &&
                    !(isJavaPropertiesFormat && "comment".equals(key)))
                props.setProperty(key, propertyValue);
            value.pop();
            paths.pop();
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            if (isJavaPropertiesFormat)
                throw e;
        }
    }

    public boolean accept(URI uri) {
        try {
            URL url = uri.toURL();
            return url.getFile().toLowerCase().endsWith(".xml");
        } catch (MalformedURLException ex) {
            return false;
        }
    }

    public void load(Properties result, URI uri) throws IOException {
        InputStream input = uri.toURL().openStream();
        try {
            SAXParser parser = factory().newSAXParser();
            XmlToPropsHandler h = new XmlToPropsHandler(result);
            parser.setProperty("http://xml.org/sax/properties/lexical-handler", h);
            parser.parse(input, h);
        } catch (ParserConfigurationException e) {
            throw new IllegalArgumentException(e);
        } catch (SAXException e) {
            throw new IOException(e);
        } finally {
            input.close();
        }
    }

    public String defaultSpecFor(String urlPrefix) {
        return urlPrefix + ".xml";
    }

}
