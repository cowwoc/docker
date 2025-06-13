package com.github.cowwoc.anchor4j.docker.exception;

import java.io.Serial;

/**
 * Thrown if a Swarm-related command demotes or removes the last manager of a Swarm.
 */
public class LastManagerException extends IllegalStateException
{
	@Serial
	private static final long serialVersionUID = 0L;

	/**
	 * Creates an exception.
	 *
	 * @param message an explanation of what went wrong
	 */
	public LastManagerException(String message)
	{
		super(message);
	}
}