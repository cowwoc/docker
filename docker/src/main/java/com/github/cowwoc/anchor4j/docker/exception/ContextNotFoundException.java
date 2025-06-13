package com.github.cowwoc.anchor4j.docker.exception;

import com.github.cowwoc.anchor4j.docker.client.Docker;

import java.io.IOException;
import java.io.Serial;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;

/**
 * Thrown if the referenced {@link Docker#getClientContext() context} does not exist.
 */
public class ContextNotFoundException extends IOException
{
	@Serial
	private static final long serialVersionUID = 0L;

	/**
	 * Creates an exception.
	 *
	 * @param message an explanation of what went wrong
	 */
	public ContextNotFoundException(String message)
	{
		super(message);
	}

	/**
	 * Creates an exception.
	 *
	 * @param cause the underlying exception
	 * @throws NullPointerException if {@code cause} is null
	 */
	public ContextNotFoundException(Throwable cause)
	{
		super(cause);
		requireThat(cause, "cause").isNotNull();
	}
}