/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.plugin.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Unlike @see org.aeonbits.owner.Config.DefaultValue that is written ad default 
 * commented value in properties file, ValorizedAs is a custom value to change
 * property current value, without replace the default one.
 * @author Luca Taddeo
 */
@Target(value = {ElementType.METHOD})
@Retention(value = RetentionPolicy.RUNTIME)
public @interface ValorizedAs {

    /**
     *
     * @return
     */
    public String value() default "${ValorizedAs}";
}