package com.github.cowwoc.docker.exception;

import java.io.IOException;
import java.io.Serial;

/**
 * Thrown if a referenced image does not exist.
 */
public class ImageNotFoundException extends IOException
{
	@Serial
	private static final long serialVersionUID = 0L;

	/**
	 * Creates a new instance.
	 *
	 * @param message an explanation of what went wrong
	 */
	public ImageNotFoundException(String message)
	{
		super(message);
	}

	/**
	 * Creates a new instance.
	 *
	 * @param cause the underlying exception
	 */
	public ImageNotFoundException(Throwable cause)
	{
		super(cause);
	}
}