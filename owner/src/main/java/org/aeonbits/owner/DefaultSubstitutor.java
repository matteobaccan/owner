package org.aeonbits.owner;

import java.io.Serializable;
import java.util.Properties;


final class DefaultSubstitutor implements Substitutor, Serializable {

    private final Properties variables;

    DefaultSubstitutor(Properties props) {
        variables = props;
    }

    public String replace(String strToReplace) {
        return variables.getProperty(strToReplace);
    }

    @Override
    public String toString() {
        return variables.toString();
    }

}
