package org.aeonbits.owner.java8;

import org.aeonbits.owner.Config;

public interface ConfigWithDefaultMethods extends Config {
    default public Integer sum(Integer a, Integer b) {
        return Integer.sum(a, b);
    }
}
