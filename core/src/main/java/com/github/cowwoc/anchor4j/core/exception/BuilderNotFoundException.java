package com.github.cowwoc.anchor4j.core.exception;

import java.io.IOException;
import java.io.Serial;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;

/**
 * Thrown if the referenced {@code BuildX} builder does not exist.
 */
public class BuilderNotFoundException extends IOException
{
	@Serial
	private static final long serialVersionUID = 0L;

	/**
	 * Creates an exception.
	 *
	 * @param message an explanation of what went wrong
	 */
	public BuilderNotFoundException(String message)
	{
		super(message);
	}

	/**
	 * Creates an exception.
	 *
	 * @param cause the underlying exception
	 * @throws NullPointerException if {@code cause} is null
	 */
	public BuilderNotFoundException(Throwable cause)
	{
		super(cause);
		requireThat(cause, "cause").isNotNull();
	}
}