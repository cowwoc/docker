package com.github.cowwoc.anchor4j.docker.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.internal.resource.ContainerAccess;
import com.github.cowwoc.anchor4j.docker.internal.resource.SharedSecrets;
import com.github.cowwoc.requirements11.annotation.CheckReturnValue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * A docker container, which is a running instance of an image.
 * <p>
 * <b>Thread Safety</b>: This class is immutable and thread-safe.
 */
@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass")
public final class Container
{
	static
	{
		SharedSecrets.setContainerAccess(new ContainerAccess()
		{
			@Override
			public Container get(InternalDocker client, String id, String name,
				HostConfiguration hostConfiguration, NetworkConfiguration networkConfiguration, Status status)
			{
				return new Container(client, id, name, hostConfiguration, networkConfiguration, status);
			}

			@Override
			public ContainerLogGetter getLogs(InternalDocker client, String id)
			{
				return new ContainerLogGetter(client, id);
			}

			@Override
			public ContainerCreator create(InternalDocker client, String imageId)
			{
				return new ContainerCreator(client, imageId);
			}

			@Override
			public ContainerStarter start(InternalDocker client, String id)
			{
				return new ContainerStarter(client, id);
			}

			@Override
			public ContainerRemover remove(InternalDocker client, String id)
			{
				return new ContainerRemover(client, id);
			}

			@Override
			public ContainerStopper stop(InternalDocker client, String id)
			{
				return new ContainerStopper(client, id);
			}

			@Override
			public Status getStatus(JsonNode json)
			{
				return Status.fromJson(json);
			}
		});
	}

	private final InternalDocker client;
	private final String id;
	private final String name;
	private final HostConfiguration hostConfiguration;
	private final NetworkConfiguration networkConfiguration;
	private final Status status;

	/**
	 * Creates a reference to a container.
	 *
	 * @param client               the client configuration
	 * @param id                   the ID of the container
	 * @param name                 the name of the container, or an empty string if the container does not have
	 *                             a name
	 * @param hostConfiguration    the container's host configuration
	 * @param networkConfiguration the container's network configuration
	 * @param status               the container's status
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain whitespace.</li>
	 *                                    <li>{@code id} is empty.</li>
	 *                                  </ul>
	 */
	private Container(InternalDocker client, String id, String name, HostConfiguration hostConfiguration,
		NetworkConfiguration networkConfiguration, Status status)
	{
		assert that(client, "client").isNotNull().elseThrow();
		assert that(id, "id").doesNotContainWhitespace().isNotEmpty().elseThrow();
		assert that(name, "name").doesNotContainWhitespace().isNotEmpty().elseThrow();
		assert that(hostConfiguration, "hostConfiguration").isNotNull().elseThrow();
		assert that(networkConfiguration, "networkConfiguration").isNotNull().elseThrow();
		assert that(status, "status").isNotNull().elseThrow();
		this.client = client;
		this.id = id;
		this.name = name;
		this.hostConfiguration = hostConfiguration;
		this.networkConfiguration = networkConfiguration;
		this.status = status;
	}

	/**
	 * Returns the ID of the container.
	 *
	 * @return the ID
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * Returns the name of the container.
	 *
	 * @return an empty string if the container does not have a name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns the container's host configuration.
	 *
	 * @return the host configuration
	 */
	public HostConfiguration getHostConfiguration()
	{
		return hostConfiguration;
	}

	/**
	 * Returns the container's network configuration.
	 *
	 * @return the network configuration
	 */
	public NetworkConfiguration getNetworkConfiguration()
	{
		return networkConfiguration;
	}

	/**
	 * Returns the container's status.
	 *
	 * @return the status
	 */
	public Status getStatus()
	{
		return status;
	}

	/**
	 * Reloads the container's status.
	 *
	 * @return the updated status
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted before the operation completes. This can happen
	 *                              due to shutdown signals.
	 */
	@CheckReturnValue
	public Container reload() throws IOException, InterruptedException
	{
		return client.getContainer(id);
	}

	/**
	 * Starts this container.
	 *
	 * @return a container starter
	 */
	@CheckReturnValue
	public ContainerStarter starter()
	{
		return client.startContainer(id);
	}

	/**
	 * Stops this container.
	 *
	 * @return a container starter
	 */
	@CheckReturnValue
	public ContainerStopper stopper()
	{
		return client.stopContainer(id);
	}

	/**
	 * Removes this container.
	 *
	 * @return a container remover
	 */
	@CheckReturnValue
	public ContainerRemover remover()
	{
		return client.removeContainer(id);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id, name, hostConfiguration, networkConfiguration, status);
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Container other && other.id.equals(id) && other.name.equals(name) &&
			other.hostConfiguration.equals(hostConfiguration) &&
			other.networkConfiguration.equals(networkConfiguration);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder().
			add("id", id).
			add("name", name).
			add("hostConfiguration", hostConfiguration).
			add("networkConfiguration", networkConfiguration).
			add("status", status).
			toString();
	}

	/**
	 * Represents a port mapping entry for a Docker container.
	 *
	 * @param containerPort the container port number being exposed
	 * @param protocol      the transport protocol being exposed
	 * @param hostAddresses the host addresses to which the container port is bound
	 */
	public record PortBinding(int containerPort, Protocol protocol, List<InetSocketAddress> hostAddresses)
	{
	}

	/**
	 * A container's host configuration.
	 * <p>
	 * <b>Thread Safety</b>: This class is immutable and thread-safe.
	 *
	 * @param portBindings the bound ports
	 */
	public record HostConfiguration(List<PortBinding> portBindings)
	{
		/**
		 * Creates a configuration.
		 *
		 * @param portBindings the bound ports
		 */
		public HostConfiguration(List<PortBinding> portBindings)
		{
			this.portBindings = List.copyOf(portBindings);
		}
	}

	/**
	 * A container's network settings.
	 * <p>
	 * <b>Thread Safety</b>: This class is immutable and thread-safe.
	 *
	 * @param ports the bound ports
	 */
	public record NetworkConfiguration(List<PortBinding> ports)
	{
		/**
		 * Creates a configuration.
		 *
		 * @param ports the bound ports
		 */
		public NetworkConfiguration
		{
			assert that(ports, "ports").isNotNull().elseThrow();
		}
	}

	/**
	 * Represents the status of a container.
	 * <p>
	 * <b>Thread Safety</b>: This class is immutable and thread-safe.
	 */
	public enum Status
	{
		/**
		 * The container was created but has never been started.
		 */
		CREATED,
		/**
		 * The container is running.
		 */
		RUNNING,
		/**
		 * The container is paused.
		 */
		PAUSED,
		/**
		 * The container is in the process of restarting.
		 */
		RESTARTING,
		/**
		 * A container which is no longer running.
		 */
		EXITED,
		/**
		 * A container which is in the process of being removed.
		 */
		REMOVING,
		/**
		 * The container was partially removed (e.g., because resources were kept busy by an external process). It
		 * cannot be (re)started, only removed.
		 */
		DEAD;

		/**
		 * Looks up a value from its JSON representation.
		 *
		 * @param json the JSON representation
		 * @return the matching value
		 * @throws NullPointerException     if {@code json} is null
		 * @throws IllegalArgumentException if no match is found
		 */
		private static Status fromJson(JsonNode json)
		{
			return valueOf(json.textValue().toUpperCase(Locale.ROOT));
		}
	}
}