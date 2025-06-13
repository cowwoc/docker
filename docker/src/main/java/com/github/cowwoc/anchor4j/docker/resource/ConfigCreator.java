package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.anchor4j.docker.exception.NotSwarmManagerException;
import com.github.cowwoc.anchor4j.docker.exception.ResourceInUseException;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Creates a {@code Config}.
 */
public final class ConfigCreator
{
	/**
	 * The maximum size of a config value in bytes.
	 */
	public static final int MAX_SIZE = 1000 * 1024;
	/**
	 * The format of config names.
	 */
	static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z0-9-_.]{1,64}");
	private final InternalDocker client;
	private final Map<String, String> labels = new HashMap<>();

	/**
	 * Creates a config creator.
	 *
	 * @param client the client configuration
	 */
	ConfigCreator(InternalDocker client)
	{
		assert that(client, "client").isNotNull().elseThrow();
		this.client = client;
	}

	/**
	 * Adds a key-value metadata pair that provide additional information about the Config, such as environment
	 * or usage context (e.g., {@code environment=production}).
	 *
	 * @param name  the name of the label
	 * @param value the value of the label
	 * @return this
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain whitespace or are empty
	 */
	public ConfigCreator label(String name, String value)
	{
		requireThat(name, "name").doesNotContainWhitespace().isNotEmpty();
		requireThat(value, "value").doesNotContainWhitespace().isNotEmpty();
		labels.put(name, value);
		return this;
	}

	/**
	 * Creates a config containing a {@link StandardCharsets#UTF_8 UTF_8}-encoded String.
	 *
	 * @param name  the config's name
	 * @param value the config's value
	 * @return the config
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>{@code name} is empty.</li>
	 *                                    <li>{@code name} contains more than 64 characters.</li>
	 *                                    <li>{@code name} contains characters other than
	 *                                    {@code [a-zA-Z0-9-_.]}.</li>
	 *                                    <li>another configuration with the same {@code name} already
	 *                                    exists.</li>
	 *                                    <li>{@code value.getBytes(UTF_8).length} is greater than
	 *                                    {@link #MAX_SIZE}.</li>
	 *                                  </ul>
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 * @throws ResourceInUseException   if the requested name is in use by another config
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	public Config create(String name, String value) throws IOException, InterruptedException
	{
		return create(name, ByteBuffer.wrap(value.getBytes(UTF_8)));
	}

	/**
	 * Creates a config.
	 *
	 * @param name  the config's name
	 * @param value the config's value
	 * @return the config
	 * @throws NullPointerException     if {@code name} or {@code value} are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>{@code name} is empty.</li>
	 *                                    <li>{@code name} contains more than 64 characters.</li>
	 *                                    <li>{@code name} contains characters other than
	 *                                    {@code [a-zA-Z0-9-_.]}.</li>
	 *                                    <li>another configuration with the same {@code name} already
	 *                                    exists.</li>
	 *                                    <li>{@code value} contains more than {@link #MAX_SIZE}
	 *                                    bytes.</li>
	 *                                  </ul>
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 * @throws ResourceInUseException   if the requested name is in use by another config
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	public Config create(String name, ByteBuffer value) throws IOException, InterruptedException
	{
		requireThat(name, "name").doesNotContainWhitespace().matches(NAME_PATTERN);
		requireThat(value.remaining(), "value.remaining()").isLessThanOrEqualTo(MAX_SIZE);

		// https://docs.docker.com/reference/cli/docker/config/create/
		List<String> arguments = new ArrayList<>(4 + labels.size() * 2);
		arguments.add("config");
		arguments.add("create");
		for (Entry<String, String> entry : labels.entrySet())
		{
			arguments.add("--label");
			arguments.add(entry.getKey() + "=" + entry.getValue());
		}
		arguments.add(name);
		arguments.add("-");
		CommandResult result = client.run(arguments, value);
		String id = client.getConfigParser().create(result);
		return client.getConfig(id);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(ConfigCreator.class).
			add("labels", labels).
			toString();
	}
}