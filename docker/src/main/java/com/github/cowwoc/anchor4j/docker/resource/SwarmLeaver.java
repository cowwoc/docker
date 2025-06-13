package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.anchor4j.docker.exception.ResourceInUseException;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Leaves a Swarm.
 */
public final class SwarmLeaver
{
	private final InternalDocker client;
	private boolean force;

	/**
	 * Creates a swarm leaver.
	 *
	 * @param client the client configuration
	 */
	SwarmLeaver(InternalDocker client)
	{
		assert that(client, "client").isNotNull().elseThrow();
		this.client = client;
	}

	/**
	 * Indicates that the context should be removed even if it is in use by a client.
	 *
	 * @return this
	 */
	public SwarmLeaver force()
	{
		this.force = true;
		return this;
	}

	/**
	 * Leaves the swarm. If the node is not a member of a swarm, this method has no effect.
	 *
	 * @throws ResourceInUseException if the node is a manager and {@link #force()} was not used. The safe way
	 *                                to remove a manager from a swarm is to demote it to a worker and then
	 *                                direct it to leave the quorum without using {@code force}. Only use
	 *                                {@code force} in situations where the swarm will no longer be used after
	 *                                the manager leaves, such as in a single-node swarm.
	 * @throws IOException            if an I/O error occurs. These errors are typically transient, and retrying
	 *                                the request may resolve the issue.
	 * @throws InterruptedException   if the thread is interrupted before the operation completes. This can
	 *                                happen due to shutdown signals.
	 */
	public void leave() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/swarm/leave/
		List<String> arguments = new ArrayList<>(3);
		arguments.add("swarm");
		arguments.add("leave");
		if (force)
			arguments.add("--force");
		CommandResult result = client.run(arguments);
		client.getSwarmParser().leave(result);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(SwarmLeaver.class).
			add("force", force).
			toString();
	}
}