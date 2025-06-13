package com.github.cowwoc.anchor4j.docker.exception;

import java.io.Serial;

/**
 * Thrown if a node is instructed to join a Swarm but is already a member of another Swarm.
 */
public class AlreadySwarmMemberException extends IllegalStateException
{
	@Serial
	private static final long serialVersionUID = 0L;

	/**
	 * Creates an exception.
	 */
	public AlreadySwarmMemberException()
	{
		super("This node is already a member of a swarm");
	}
}