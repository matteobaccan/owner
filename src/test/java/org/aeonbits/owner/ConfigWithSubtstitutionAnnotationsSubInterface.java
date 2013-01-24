/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

/**
 * @author luigi
 */
public interface ConfigWithSubtstitutionAnnotationsSubInterface extends ConfigWithSubstitutionAnnotations {

    @DefaultValue("grandma")
    public String teller();

    @DefaultValue("Please ${teller}, tell me the story of '${story}'")
    public String tellmeTheStory();
}
