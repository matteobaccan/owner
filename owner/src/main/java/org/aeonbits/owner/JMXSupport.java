/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.ReflectionException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * @author Robin Meißner
 * @author Luigi R. Viggiano
 */
class JMXSupport implements Serializable {

    private final Class<?> clazz;
    private final PropertiesManager manager;

    public JMXSupport(Class<?> clazz, PropertiesManager manager) {
        this.clazz = clazz;
        this.manager = manager;
    }

    @Delegate
    public Object getAttribute(String attribute)
            throws AttributeNotFoundException, MBeanException,
            ReflectionException {
        return manager.getProperty(attribute);
    }

    @Delegate
    public void setAttribute(Attribute attribute)
            throws AttributeNotFoundException, InvalidAttributeValueException,
            MBeanException, ReflectionException {
        manager.setProperty(attribute.getName(), (String) attribute.getValue());
    }

    @Delegate
    public AttributeList getAttributes(String[] attributes) {
        List<Attribute> attrList = new LinkedList<Attribute>();
        for (String propertyName : attributes)
            attrList.add(new Attribute(propertyName, manager.getProperty(propertyName)));
        return new AttributeList(attrList);
    }

    @Delegate
    public AttributeList setAttributes(AttributeList attributes) {
        for (Attribute attr : attributes.asList())
            manager.setProperty(attr.getName(), (String) attr.getValue());
        return attributes;
    }

    @Delegate
    public Object invoke(String actionName, Object[] params, String[] signature)
            throws MBeanException, ReflectionException {
        if (actionName.equals("getProperty") && params != null && params.length == 1) {
            return manager.getProperty((String) params[0]);
        } else if (actionName.equals("setProperty") && params != null && params.length == 2) {
            manager.setProperty((String) params[0], (String) params[1]);
            return null;
        } else if (actionName.equals("reload") && (params == null || params.length == 0)) {
            manager.reload();
            return null;
        }
        throw new ReflectionException(new NoSuchMethodException(actionName));
    }

    @Delegate
    public MBeanInfo getMBeanInfo() {
        List<MBeanAttributeInfo> attributesInfo = new ArrayList<MBeanAttributeInfo>();
        Set<String> propertyNames = manager.propertyNames();
        for (String name : propertyNames)
            attributesInfo.add(new MBeanAttributeInfo(name, "java.lang.String", name, true, true, false));

        MBeanAttributeInfo[] attributes = attributesInfo.toArray(new MBeanAttributeInfo[propertyNames.size()]);

        MBeanParameterInfo key = new MBeanParameterInfo("key", "java.lang.String", "Key of the property");
        MBeanParameterInfo value = new MBeanParameterInfo("value", "java.lang.String", "Value of the property");

        MBeanOperationInfo[] operations = new MBeanOperationInfo[] {
                new MBeanOperationInfo("getProperty", "Gets value for a property",
                        new MBeanParameterInfo[] { key }, "java.lang.String", MBeanOperationInfo.INFO),
                new MBeanOperationInfo("setProperty", "Sets the value for a property",
                        new MBeanParameterInfo[] { key, value }, "void", MBeanOperationInfo.ACTION),
                new MBeanOperationInfo("reload", "Reload properties", null, "void", MBeanOperationInfo.ACTION)
        };

        return new MBeanInfo(clazz.getName(), clazz.getSimpleName() + " OWNER MBean",
                attributes, null, operations, null);
    }

}
