package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.internal.resource.ConfigAccess;
import com.github.cowwoc.anchor4j.docker.internal.resource.SharedSecrets;
import com.github.cowwoc.anchor4j.docker.internal.util.Buffers;
import com.github.cowwoc.requirements11.annotation.CheckReturnValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Non-sensitive configuration that is stored in a Swarm.
 */
@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass")
public final class Config
{
	static
	{
		SharedSecrets.setConfigAccess(new ConfigAccess()
		{
			@Override
			public Config get(InternalDocker client, String id, String name, ByteBuffer value)
			{
				return new Config(client, id, name, value);
			}

			@Override
			public ConfigCreator create(InternalDocker client)
			{
				return new ConfigCreator(client);
			}
		});
	}

	private final InternalDocker client;
	private final String id;
	private final String name;
	private final ByteBuffer value;

	/**
	 * Creates a reference to a swarm's configuration.
	 *
	 * @param client the client configuration
	 * @param id     the config's ID
	 * @param name   the config's name
	 * @param value  the config's value
	 */
	private Config(InternalDocker client, String id, String name, ByteBuffer value)
	{
		assert that(client, "commands").isNotNull().elseThrow();
		assert that(id, "id").doesNotContainWhitespace().isNotEmpty().elseThrow();
		assert that(name, "name").doesNotContainWhitespace().matches(ConfigCreator.NAME_PATTERN).elseThrow();
		assert that(value, "value").isNotNull().elseThrow();
		assert that(value.remaining(), "value.remaining()").isLessThanOrEqualTo(ConfigCreator.MAX_SIZE).
			elseThrow();
		this.client = client;
		this.id = id;
		this.name = name;
		this.value = Buffers.copyOf(value);
	}

	/**
	 * Returns the config's id.
	 *
	 * @return the config's id
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * Returns the config's name.
	 *
	 * @return the config's name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns config's value.
	 *
	 * @return the value
	 */
	public ByteBuffer getValue()
	{
		return value;
	}

	/**
	 * Returns the String representation of the config's value.
	 *
	 * @return the value
	 */
	public String getValueAsString()
	{
		return UTF_8.decode(value.duplicate()).toString();
	}

	/**
	 * Reloads the config's state.
	 *
	 * @return the updated state
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted before the operation completes. This can happen
	 *                              due to shutdown signals.
	 */
	@CheckReturnValue
	public Config reload() throws IOException, InterruptedException
	{
		return client.getConfig(id);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id, name, value);
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Config other && other.id.equals(id) && other.name.equals(name) &&
			other.value.equals(value);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(Config.class).
			add("id", id).
			add("name", name).
			add("value", getValueAsString()).
			toString();
	}
}