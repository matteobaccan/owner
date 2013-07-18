/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;
import java.util.Scanner;
import java.util.Stack;

/**
 * @author Luigi R. Viggiano
 */
public class XmlSpike {
    public static class StringInputStream extends InputStream {
        private String string;
        private int i;

        public StringInputStream(String string) {
            this.string = string;
        }

        @Override
        public int read() throws IOException {
            if (i < string.length())
                return string.charAt(i++);
            else
                return -1;
        }
    }
    static class XmlToPropsHandler extends DefaultHandler {
        private final PrintWriter writer;
        private final Stack<String> paths = new Stack<String>();

        public XmlToPropsHandler(PrintWriter writer) {
            this.writer = writer;
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes attributes) throws SAXException {
            String path = (paths.size() == 0) ? qName : paths.peek() + "." + qName;
            paths.push(path);
            for (int i = 0; i < attributes.getLength(); i++) {
                String attrName = attributes.getQName(i);
                String attrValue = fixNewLines(attributes.getValue(i));
                writer.println(path + "." + attrName + "=" + attrValue);
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

    public static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }


    public static void main(String[] args) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();

        InputStream in = XmlSpike.class.getResourceAsStream("Config.xml");

        String input = convertStreamToString(in);

        System.out.println("input:\n" + input);

        StringWriter output = new StringWriter();
        PrintWriter pw = new PrintWriter(output);
        XmlToPropsHandler h = new XmlToPropsHandler(pw);
        parser.parse(new StringInputStream(input), h);
        pw.flush();

        System.out.println("output:\n" + output);

        File file = new File("target/test-resources/props.xml");
        file.getParentFile().mkdirs();


        Properties props = new Properties();
        props.load(new StringInputStream(output.toString()));

        ByteArrayOutputStream propertiesxmlformat = new ByteArrayOutputStream();
        props.storeToXML(propertiesxmlformat, "test");
        String propsXmlString = propertiesxmlformat.toString();

        
        System.out.println("java xml properties format:" + propsXmlString);

        boolean isJavaProp = propsXmlString.contains("http://java.sun.com/dtd/properties.dtd");
        
        System.out.println("isJavaProp: " + isJavaProp);
    }
}
