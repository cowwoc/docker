package com.github.cowwoc.anchor4j.docker.exception;

import java.io.Serial;

/**
 * Thrown if a Swarm administration command is invoked on a node that is not a Swarm manager.
 */
public class NotSwarmManagerException extends IllegalStateException
{
	@Serial
	private static final long serialVersionUID = 0L;

	/**
	 * Creates an exception.
	 */
	public NotSwarmManagerException()
	{
		super("This node is not a swarm manager. Worker nodes cannot be used to view or modify the cluster " +
			"state.");
	}
}