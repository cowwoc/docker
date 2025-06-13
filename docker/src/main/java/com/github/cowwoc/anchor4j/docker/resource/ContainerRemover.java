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
 * Removes a container.
 */
public final class ContainerRemover
{
	private final InternalDocker client;
	private final String id;
	private boolean force;
	private boolean volumes;

	/**
	 * Creates a container remover.
	 *
	 * @param id     the container's ID or name
	 * @param client the client configuration
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id}'s format is invalid
	 */
	ContainerRemover(InternalDocker client, String id)
	{
		assert that(client, "client").isNotNull().elseThrow();
		client.validateContainerIdOrName(id, "id");
		this.client = client;
		this.id = id;
	}

	/**
	 * Indicates that the container should be killed (using SIGKILL) if it is running.
	 *
	 * @return this
	 */
	public ContainerRemover kill()
	{
		this.force = true;
		return this;
	}

	/**
	 * Indicates that any anonymous volumes associated with the container should be automatically removed when
	 * it is deleted.
	 *
	 * @return this
	 */
	public ContainerRemover removeAnonymousVolumes()
	{
		this.volumes = true;
		return this;
	}

	/**
	 * Removes the container. If the container does not exist, this method has no effect.
	 *
	 * @throws ResourceInUseException if the container is running and {@link #kill()} was not used
	 * @throws IOException            if an I/O error occurs. These errors are typically transient, and retrying
	 *                                the request may resolve the issue.
	 * @throws InterruptedException   if the thread is interrupted before the operation completes. This can
	 *                                happen due to shutdown signals.
	 */
	public void remove() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/container/rm/
		List<String> arguments = new ArrayList<>(5);
		arguments.add("container");
		arguments.add("rm");
		if (force)
			arguments.add("--force");
		if (volumes)
			arguments.add("--volumes");
		arguments.add(id);
		CommandResult result = client.run(arguments);
		client.getContainerParser().remove(result);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(ContainerRemover.class).
			add("force", force).
			add("volumes", volumes).
			toString();
	}
}