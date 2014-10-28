package org.aeonbits.owner;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.ReflectionException;

public interface JMXBean extends Config, DynamicMBean {
	
	public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException,
	ReflectionException;

	public void setAttribute(Attribute attribute)
			throws AttributeNotFoundException, InvalidAttributeValueException,
			MBeanException, ReflectionException;

	public AttributeList getAttributes(String[] attributes);

	public AttributeList setAttributes(AttributeList attributes);

	public Object invoke(String actionName, Object[] params, String[] signature)
			throws MBeanException, ReflectionException;

	public MBeanInfo getMBeanInfo();

}
