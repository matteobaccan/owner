/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.xml;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.DefaultHandler2;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;
import java.util.Stack;

/**
 * @author Luigi R. Viggiano
 */
public class XmlSpike {

    static class XmlToPropsHandler extends DefaultHandler2 {
        // The required DTD URI for exported properties
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
        private final PrintWriter writer;
        private final Stack<String> paths = new Stack<String>();


        @Override
        public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId) throws SAXException, IOException {
            if (systemId.equals(PROPS_DTD_URI)) {
                isJavaPropertiesFormat = true;
                InputSource is;
                is = new InputSource(new StringReader(PROPS_DTD));
                is.setSystemId(PROPS_DTD_URI);
                return is;
            } else {
                return super.resolveEntity(name, publicId, baseURI, systemId);
            }
        }

        @Override
        public void warning(SAXParseException e) throws SAXException {
            System.err.println("!!!!!!!!!!!!!!!!!!!!!" + e);
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            System.err.println("!!!!!!!!!!!!!!!!!!!!!" + e);
        }

        public XmlToPropsHandler(PrintWriter writer) {
            this.writer = writer;
        }

        @Override
        public void skippedEntity(String name) throws SAXException {
            System.err.println("skipped entity: " + name);
        }

        @Override
        public void unparsedEntityDecl(String name, String publicId, String systemId, String notationName) throws SAXException {
            System.err.println("skipped entity: " + name);
        }

        @Override
        public void notationDecl(String name, String publicId, String systemId) throws SAXException {
            System.err.println("skipped entity: " + name);
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes attributes) throws SAXException {
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
                    writer.println(path + "." + attrName + "=" + attrValue);
                }
            }
        }

        private String fixNewLines(String value) {
            return value.replace("\n", "\\\n");
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            String value = fixNewLines(new String(ch, start, length).trim());
            if (!value.isEmpty())
                writer.println(paths.peek() + "=" + value);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            paths.pop();
        }
    }

    public static Properties load(InputStream inputStream) throws ParserConfigurationException, SAXException, 
            IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/validation", true);
        SAXParser parser = factory.newSAXParser();

        StringWriter output = new StringWriter();
        PrintWriter pw = new PrintWriter(output);
        XmlToPropsHandler h = new XmlToPropsHandler(pw);
        parser.parse(inputStream, h);
        pw.flush();

        System.out.println("output:\n" + output);

        Properties props = new Properties();
        props.load(new StringReader(output.toString()));
        return props;
    }


    public static void main(String[] args) throws Exception {

        InputStream in = XmlSpike.class.getResourceAsStream("Config.xml");

        Properties props = load(in);

        File file = new File("target/test-resources/props.xml");
        file.getParentFile().mkdirs();

        ByteArrayOutputStream propertiesxmlformat = new ByteArrayOutputStream();
        props.storeToXML(propertiesxmlformat, "test");
        String propsXmlString = propertiesxmlformat.toString();


        System.out.println("java xml properties format:" + propsXmlString);

        Properties props2 = new Properties();
        props2.loadFromXML(new FileInputStream(file));


        Properties props3 = load(new FileInputStream(file));

        props.store(System.out, "props");
        props2.store(System.out, "props2");
        props3.store(System.out, "props3");
    }
}
