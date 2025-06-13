package com.github.cowwoc.anchor4j.docker.exception;

import java.io.Serial;

/**
 * Thrown if a Swarm-related command is invoked on a node that is not a member of a Swarm.
 */
public class NotSwarmMemberException extends IllegalStateException
{
	@Serial
	private static final long serialVersionUID = 0L;

	/**
	 * Creates an exception.
	 */
	public NotSwarmMemberException()
	{
		super("This node is not a member of a swarm");
	}
}