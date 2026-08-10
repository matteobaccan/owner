/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
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

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

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
            // Hardening against XXE: the internal Java properties DTD still works
            // (its DOCTYPE is intercepted by resolveEntity), but external DTDs and
            // external entities are neutralized, and secure processing limits
            // entity expansion (billion laughs).
            setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
            setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        }
        return factory;
    }

    /**
     * Asks the parser for one of the hardening features, and says so when it will not have it.
     * <p>
     * A parser that does not know a feature cannot be made to honour it, and there is nothing to do but carry
     * on: refusing to read XML at all because one switch is missing would be worse than reading it. But the
     * protection this class is documented to give is then not there, and silence would leave a deployment
     * believing in a defence it does not have. Which feature was refused is named, because the three are not
     * equally serious.
     * </p>
     */
    static void setFeature(SAXParserFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException e) {
            reportUnavailable(feature, e);
        } catch (SAXException e) {
            reportUnavailable(feature, e);
        }
    }

    private static void reportUnavailable(String feature, Exception cause) {
        Logger.getLogger(XMLLoader.class.getName()).log(Level.WARNING, cause, () -> String.format(
                "The XML parser in use does not support '%s', so that hardening is not in force for XML "
                        + "configuration read by OWNER. An XML source from an untrusted place could then "
                        + "reach an external entity or expand entities without limit. Placing a parser that "
                        + "supports it on the classpath restores the protection.", feature));
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
        private final Stack<String> paths = new Stack<>();
        private final Stack<StringBuilder> value = new Stack<>();

        /**
         * How many times each key has been arrived at, so that the second element of a name already seen
         * under the same parent can be told it is one of several.
         */
        private final Map<String, Integer> occurrences = new HashMap<>();

        @Override
        public InputSource resolveEntity(String name, String publicId, String baseURI,
                                         String systemId) throws SAXException, IOException {
            if (PROPS_DTD_URI.equals(systemId)) {
                isJavaPropertiesFormat = true;
                InputSource inputSource = new InputSource(new StringReader(PROPS_DTD));
                inputSource.setSystemId(PROPS_DTD_URI);
                return inputSource;
            }
            // Any other external entity/DTD is neutralized by returning an empty
            // source instead of null (which would let the parser fetch it).
            return new InputSource(new StringReader(""));
        }

        /**
         * Reads into a map of its own, which the loader merges into the caller's once the document is
         * finished.
         * <p>
         * It matters because of the renumbering. Under a MERGE policy the same {@link Properties} is handed
         * to every source in turn, so a key this document is about to write may already hold another file's
         * value; writing straight into it would make that value visible to the renumbering, which would
         * carry it off to <code>[0]</code> along with its own. Building separately makes the contribution of
         * a source what it would have been had it been read alone, which is the only reading of it that can
         * be explained.
         * </p>
         */
        public XmlToPropsHandler() {
            this.props = new Properties();
        }

        /** What the document said, once it has all been read. */
        Properties properties() {
            return props;
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
                String path = pathFor(PropertyKeys.child(paths.isEmpty() ? null : paths.peek(), qName));
                paths.push(path);
                for (int i = 0; i < attributes.getLength(); i++)
                    put(PropertyKeys.child(path, attributes.getQName(i)), attributes.getValue(i));
            }
        }

        /**
         * The key for an element, given the key it would have if it were the only one of its name under its
         * parent.
         * <p>
         * A stream cannot look ahead: when the first <code>&lt;tag&gt;</code> arrives there is no telling
         * whether a second will follow, and giving every element an index on the chance of it would rename
         * every key of every XML file ever written against this library. So the first keeps the plain name,
         * and if a second turns up the first is moved under <code>[0]</code> then — by which time its
         * subtree has been read in full, so there is a subtree to move.
         * </p>
         */
        private String pathFor(String base) {
            Integer seen = occurrences.get(base);
            if (seen == null) {
                occurrences.put(base, 1);
                return base;
            }
            if (seen == 1)
                moveUnderFirstIndex(base);
            occurrences.put(base, seen + 1);
            return PropertyKeys.element(base, seen);
        }

        /** Moves everything read under {@code base} to {@code base[0]}, itself included. */
        private void moveUnderFirstIndex(String base) {
            String indexed = PropertyKeys.element(base, 0);
            String below = base + PropertyKeys.NESTING;
            for (String name : new ArrayList<>(props.stringPropertyNames())) {
                if (name.equals(base))
                    props.setProperty(indexed, (String) props.remove(name));
                else if (name.startsWith(below))
                    props.setProperty(indexed + name.substring(base.length()), (String) props.remove(name));
            }
        }

        private void put(String key, String propertyValue) {
            props.setProperty(key, propertyValue);
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
                put(key, propertyValue);
            value.pop();
            paths.pop();
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            if (isJavaPropertiesFormat)
                throw e;
        }
    }

    @Override
    public boolean accept(URI uri) {
        try {
            uri.toURL();
            // matched against the path rather than against URL.getFile(), which includes the query: an XML
            // served over HTTP as config.xml?v=2 used to fail this test, fall through to PropertiesLoader -
            // which accepts everything it can resolve - and be read as a properties file, in silence
            return SourceOptions.path(uri).toLowerCase().endsWith(".xml");
        } catch (MalformedURLException ex) {
            return false;
        }
    }

    @Override
    public void load(Properties result, URI uri) throws IOException {
        SourceOptions.of(uri).refuseUnknown();
        InputStream input = uri.toURL().openStream();
        try {
            SAXParser parser = factory().newSAXParser();
            XmlToPropsHandler h = new XmlToPropsHandler();
            parser.setProperty("http://xml.org/sax/properties/lexical-handler", h);
            parser.parse(input, h);
            // merged only once the whole document has been read: see the handler's constructor for why it
            // does not write straight into what it was given
            result.putAll(h.properties());
        } catch (ParserConfigurationException e) {
            throw new IllegalArgumentException(e);
        } catch (SAXException e) {
            throw new IOException(e);
        } finally {
            input.close();
        }
    }

    @Override
    public String defaultSpecFor(String urlPrefix) {
        return urlPrefix + ".xml";
    }

}
