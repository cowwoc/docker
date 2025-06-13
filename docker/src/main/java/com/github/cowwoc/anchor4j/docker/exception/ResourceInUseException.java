package com.github.cowwoc.anchor4j.docker.exception;

import java.io.IOException;
import java.io.Serial;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;

/**
 * Thrown when a referenced resource cannot be removed because it is currently in use (e.g., attempting to
 * remove a running container).
 */
public class ResourceInUseException extends IOException
{
	@Serial
	private static final long serialVersionUID = 0L;

	/**
	 * Creates an exception.
	 *
	 * @param message an explanation of what went wrong
	 */
	public ResourceInUseException(String message)
	{
		super(message);
	}

	/**
	 * Creates an exception.
	 *
	 * @param cause the underlying exception
	 * @throws NullPointerException if {@code cause} is null
	 */
	public ResourceInUseException(Throwable cause)
	{
		super(cause);
		requireThat(cause, "cause").isNotNull();
	}
}