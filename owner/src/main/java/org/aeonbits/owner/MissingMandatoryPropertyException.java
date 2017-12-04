package org.aeonbits.owner;

public class MissingMandatoryPropertyException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	MissingMandatoryPropertyException(String message) {
		super(message);
	}

}
