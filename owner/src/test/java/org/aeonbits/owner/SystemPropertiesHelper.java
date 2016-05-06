package org.aeonbits.owner;

import java.util.HashSet;
import java.util.Set;

public class SystemPropertiesHelper {
    
    private Set<String> sysProps = new HashSet<String>();

    public void setProperty(String key, String val) {
        System.setProperty(key, val);
        sysProps.add(key);
    }

    public void cleanProperty(String key) {
        System.clearProperty(key);
        sysProps.remove(key);
    }

    public void cleanNonDefaultSysProps() {
        for (String prop : sysProps) {
            System.clearProperty(prop);
        }
        sysProps.clear();
    }
}
