package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.internal.resource.ContextAccess;
import com.github.cowwoc.anchor4j.docker.internal.resource.SharedSecrets;
import com.github.cowwoc.requirements11.annotation.CheckReturnValue;

import java.io.IOException;
import java.util.Objects;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Represents a Docker context (i.e., the Docker Engine that the client communicates with).
 *
 * @see <a href="https://docs.docker.com/engine/manage-resources/contexts/">Docker documentation</a>
 */
@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass")
public final class Context
{
	static
	{
		SharedSecrets.setContextAccess(new ContextAccess()
		{
			@Override
			public Context get(InternalDocker client, String name, String description, String endpoint)
			{
				return new Context(client, name, description, endpoint);
			}

			@Override
			public ContextCreator create(InternalDocker client, String name, ContextEndpoint endpoint)
			{
				return new ContextCreator(client, name, endpoint);
			}

			@Override
			public ContextRemover remove(InternalDocker client, String name)
			{
				return new ContextRemover(client, name);
			}
		});
	}

	private final InternalDocker client;
	private final String name;
	private final String description;
	private final String endpoint;

	/**
	 * Creates a reference to a context.
	 *
	 * @param client      the client configuration
	 * @param name        the context's name
	 * @param description the context's description
	 * @param endpoint    the configuration of the target Docker Engine
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain whitespace.</li>
	 *                                    <li>{@code name} or {@code endpoint} are empty.</li>
	 *                                  </ul>
	 */
	private Context(InternalDocker client, String name, String description, String endpoint)
	{
		assert that(client, "client").elseThrow();
		requireThat(name, "name").doesNotContainWhitespace().isNotEmpty();
		requireThat(description, "description").doesNotContainWhitespace();
		requireThat(endpoint, "endpoint").doesNotContainWhitespace().isNotEmpty();

		this.client = client;
		this.name = name;
		this.description = description;
		this.endpoint = endpoint;
	}

	/**
	 * Returns the context's name.
	 *
	 * @return the name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns the context's description.
	 *
	 * @return an empty string if omitted
	 */
	public String getDescription()
	{
		return description;
	}

	/**
	 * Reloads the context's state.
	 *
	 * @return the updated state
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted before the operation completes. This can happen
	 *                              due to shutdown signals.
	 */
	@CheckReturnValue
	public Context reload() throws IOException, InterruptedException
	{
		return client.getContext(name);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, description, endpoint);
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Context other && other.name.equals(name) && other.description.equals(description) &&
			other.endpoint.equals(endpoint);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(Image.class).
			add("name", name).
			add("description", description).
			add("endpoint", endpoint).
			toString();
	}
}