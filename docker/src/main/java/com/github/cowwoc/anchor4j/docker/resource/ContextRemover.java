package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Removes a context.
 */
public final class ContextRemover
{
	private final InternalDocker client;
	private final String name;
	private boolean force;

	/**
	 * Creates a context remover.
	 *
	 * @param client the client configuration
	 * @param name   the name of the context
	 * @throws NullPointerException     if {@code name} is null
	 * @throws IllegalArgumentException if {@code name}'s format is invalid
	 */
	ContextRemover(InternalDocker client, String name)
	{
		assert that(client, "client").isNotNull().elseThrow();
		requireThat(name, "name").doesNotContainWhitespace().isNotEmpty();
		this.client = client;
		this.name = name;
	}

	/**
	 * Indicates that the context should be removed even if it is in use by a client.
	 *
	 * @return this
	 */
	public ContextRemover force()
	{
		this.force = true;
		return this;
	}

	/**
	 * Removes the context. If the context does not exist, this method has no effect.
	 *
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted before the operation completes. This can happen
	 *                              due to shutdown signals.
	 */
	public void remove() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/context/rm/
		List<String> arguments = new ArrayList<>(4);
		arguments.add("context");
		arguments.add("rm");
		if (force)
			arguments.add("--force");
		arguments.add(name);
		CommandResult result = client.run(arguments);
		client.getContextParser().remove(result);
	}
}
