package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.anchor4j.docker.exception.ResourceInUseException;
import com.github.cowwoc.anchor4j.docker.exception.ResourceNotFoundException;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Creates a context.
 */
public final class ContextCreator
{
	private final InternalDocker client;
	private final String name;
	private final ContextEndpoint endpoint;
	private String description = "";

	/**
	 * Creates a context creator.
	 *
	 * @param client   the client configuration
	 * @param name     the name of the context
	 * @param endpoint the connection configuration for the target Docker Engine
	 * @throws NullPointerException     if {@code name} or {@code endpoint} are null
	 * @throws IllegalArgumentException if {@code name} contains whitespace or is empty
	 * @see ContextEndpoint#builder(URI)
	 */
	ContextCreator(InternalDocker client, String name, ContextEndpoint endpoint)
	{
		assert that(client, "client").isNotNull().elseThrow();
		requireThat(name, "name").doesNotContainWhitespace().isNotEmpty();
		requireThat(endpoint, "endpoint").isNotNull();
		this.client = client;
		this.name = name;
		this.endpoint = endpoint;
	}

	/**
	 * Sets the context's description.
	 *
	 * @param description the description, or an empty string to omit
	 * @return this
	 * @throws NullPointerException     if {@code description} is null
	 * @throws IllegalArgumentException if {@code description} contains whitespace
	 */
	public ContextCreator description(String description)
	{
		requireThat(description, "description").doesNotContainWhitespace();
		this.description = description;
		return this;
	}

	/**
	 * Creates the context.
	 *
	 * @throws ResourceNotFoundException if any of the {@link ContextEndpoint referenced TLS files} is not
	 *                                   found
	 * @throws ResourceInUseException    if another context with the same name already exists
	 * @throws IOException               if an I/O error occurs. These errors are typically transient, and
	 *                                   retrying the request may resolve the issue.
	 * @throws InterruptedException      if the thread is interrupted before the operation completes. This can
	 *                                   happen due to shutdown signals.
	 */
	public void create() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/context/create/
		List<String> arguments = new ArrayList<>(11);
		arguments.add("context");
		arguments.add("create");
		if (!description.isEmpty())
		{
			arguments.add("--description");
			arguments.add(description);
		}

		StringJoiner endpointJoiner = new StringJoiner(",");
		endpointJoiner.add("host=" + endpoint.uri());
		if (endpoint.caPublicKey() != null)
		{
			endpointJoiner.add("ca=" + endpoint.caPublicKey());
			endpointJoiner.add("cert=" + endpoint.clientCertificate());
			endpointJoiner.add("key=" + endpoint.clientPrivateKey());
		}
		arguments.add("--docker");
		arguments.add(endpointJoiner.toString());
		arguments.add(name);

		CommandResult result = client.run(arguments);
		client.getContextParser().create(result);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(ContextCreator.class).
			add("description", description).
			toString();
	}
}