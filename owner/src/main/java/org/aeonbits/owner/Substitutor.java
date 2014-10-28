package org.aeonbits.owner;

import org.aeonbits.owner.Config.SubstitutorClasses;

/**
 * Substitutor interface specifies how to provide a custom substitutor mechanism when replacing
 * variables in strings. Substitutors are registered by name and implementation class using
 * {@link SubstitutorClasses} annotation. For example this string "${name:some-value}" the
 * "some-value" part will be sent to the Substitutor implementation registered
 * with the name "name".
 */
public interface Substitutor {

    /**
     * Replaces the given {@code strToReplace} to something custom.
     * 
     * @param strToReplace the value to replace
     * @return the replacement value
     */
    String replace(String strToReplace);
}
