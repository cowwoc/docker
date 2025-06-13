package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.docker.exception.NotSwarmManagerException;
import com.github.cowwoc.anchor4j.docker.exception.ResourceInUseException;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Removes a swarm node.
 */
public final class NodeRemover
{
	private final InternalDocker client;
	private boolean force;

	/**
	 * Creates a container remover.
	 *
	 * @param client the client configuration
	 */
	NodeRemover(InternalDocker client)
	{
		assert that(client, "client").isNotNull().elseThrow();
		this.client = client;
	}

	/**
	 * Indicates that the node should be removed even if it is inaccessible, has been compromised or is not
	 * behaving as expected.
	 *
	 * @return this
	 */
	public NodeRemover force()
	{
		this.force = true;
		return this;
	}

	/**
	 * Removes a node. If the node does not exist, this method has no effect.
	 *
	 * @param id the node's ID or hostname
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id} contains whitespace or is empty
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 * @throws ResourceInUseException   if the node is inaccessible and {@link #force()} was not used, or if an
	 *                                  attempt was made to remove the current node
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	public void remove(String id) throws IOException, InterruptedException
	{
		requireThat(id, "id").doesNotContainWhitespace().isNotEmpty();

		// https://docs.docker.com/reference/cli/docker/node/rm/
		List<String> arguments = new ArrayList<>(4);
		arguments.add("node");
		arguments.add("rm");
		if (force)
			arguments.add("--force");
		arguments.add(id);
		CommandResult result = client.run(arguments);
		client.getNodeParser().remove(result);
	}
}