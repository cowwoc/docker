package com.github.cowwoc.docker.exception;

import java.io.IOException;
import java.io.Serial;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * Thrown if a referenced resource does not exist.
 */
public class ResourceNotFoundException extends IOException
{
	@Serial
	private static final long serialVersionUID = 0L;

	/**
	 * Creates a new instance.
	 *
	 * @param message an explanation of what went wrong
	 */
	public ResourceNotFoundException(String message)
	{
		super(message);
	}

	/**
	 * Creates a new instance.
	 *
	 * @param cause the underlying exception
	 * @throws NullPointerException if {@code cause} is null
	 */
	public ResourceNotFoundException(Throwable cause)
	{
		super(cause);
		requireThat(cause, "cause").isNotNull();
	}
}