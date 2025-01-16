package com.github.cowwoc.docker.exception;

import java.io.IOException;
import java.io.Serial;

/**
 * Thrown if a referenced container does not exist.
 */
public class ContainerNotFoundException extends IOException
{
	@Serial
	private static final long serialVersionUID = 0L;

	/**
	 * Creates a new instance.
	 *
	 * @param message an explanation of what went wrong
	 */
	public ContainerNotFoundException(String message)
	{
		super(message);
	}

	/**
	 * Creates a new instance.
	 *
	 * @param cause the underlying exception
	 * @throws NullPointerException if {@code cause} is null
	 */
	public ContainerNotFoundException(Throwable cause)
	{
		super(cause);
	}
}