package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.internal.resource.SharedSecrets;
import com.github.cowwoc.requirements11.annotation.CheckReturnValue;

import java.io.IOException;
import java.util.List;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * A docker network.
 */
@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass")
public final class Network
{
	static
	{
		SharedSecrets.setNetworkAccess(Network::new);
	}

	private final InternalDocker client;
	private final String id;
	private final String name;
	private final List<Configuration> configurations;

	/**
	 * Creates a network.
	 *
	 * @param client         the client configuration
	 * @param id             the ID of the network
	 * @param name           the name of the network
	 * @param configurations the network configurations
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain whitespace or is empty
	 */
	private Network(InternalDocker client, String id, String name, List<Configuration> configurations)
	{
		assert that(client, "client").isNotNull().elseThrow();
		assert that(id, "id").doesNotContainWhitespace().isNotEmpty().elseThrow();
		assert that(name, "name").doesNotContainWhitespace().isNotEmpty().elseThrow();
		this.client = client;
		this.id = id;
		this.name = name;
		this.configurations = List.copyOf(configurations);
	}

	/**
	 * Returns the ID of the network.
	 *
	 * @return the ID
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * Returns the name of the network.
	 *
	 * @return the name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns the network's configurations.
	 *
	 * @return the configurations
	 */
	public List<Configuration> getConfigurations()
	{
		return configurations;
	}

	/**
	 * Reloads the network's state.
	 *
	 * @return the updated state
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted before the operation completes. This can happen
	 *                              due to shutdown signals.
	 */
	@CheckReturnValue
	public Network reload() throws IOException, InterruptedException
	{
		return client.getNetwork(id);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder().
			add("id", id).
			add("name", name).
			add("configurations", configurations).
			toString();
	}

	/**
	 * A network configuration.
	 *
	 * @param subnet  the network's subnet CIDR
	 * @param gateway the network's gateway
	 */
	public record Configuration(String subnet, String gateway)
	{
		/**
		 * Creates a configuration.
		 *
		 * @param subnet  the network's subnet CIDR
		 * @param gateway the network's gateway
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if any of the arguments contain whitespace or is empty
		 */
		public Configuration
		{
			requireThat(subnet, "subnet").doesNotContainWhitespace().isNotEmpty();
			requireThat(gateway, "gateway").doesNotContainWhitespace().isNotEmpty();
		}

		@Override
		public String toString()
		{
			return new ToStringBuilder().
				add("subnet", subnet).
				add("gateway", gateway).
				toString();
		}
	}
}