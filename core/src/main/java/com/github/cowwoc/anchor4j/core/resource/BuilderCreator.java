package com.github.cowwoc.anchor4j.core.resource;

import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.client.InternalClient;
import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.requirements11.annotation.CheckReturnValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Creates a builder.
 */
public final class BuilderCreator
{
	private final InternalClient client;
	private String name = "";
	private boolean startEagerly;
	private Driver driver;
	private String context = "";

	/**
	 * Creates a builder creator.
	 *
	 * @param client the client configuration
	 */
	BuilderCreator(InternalClient client)
	{
		assert that(client, "client").isNotNull().elseThrow();
		this.client = client;
	}

	/**
	 * Sets the builder's name.
	 *
	 * @param name the name
	 * @return this
	 * @throws NullPointerException     if {@code name} is null
	 * @throws IllegalArgumentException if {@code name}'s format is invalid
	 */
	public BuilderCreator name(String name)
	{
		client.validateName(name, "name");
		this.name = name;
		return this;
	}

	/**
	 * Starts the builder immediately after creation. By default, the builder is started lazily when it handles
	 * its first request.
	 * <p>
	 * Eager startup helps surface configuration issues early.
	 *
	 * @return this
	 */
	public BuilderCreator startEagerly()
	{
		this.startEagerly = true;
		return this;
	}

	/**
	 * Sets the builder's driver.
	 *
	 * @param driver the driver
	 * @return this
	 * @throws NullPointerException if {@code driver} is null
	 */
	public BuilderCreator driver(Driver driver)
	{
		requireThat(driver, "driver").isNotNull();
		this.driver = driver;
		return this;
	}

	/**
	 * Sets the context to create the builder on. By default, the current Docker context is used.
	 *
	 * @param context the name of the context
	 * @return this
	 * @throws NullPointerException     if {@code context} is null
	 * @throws IllegalArgumentException if {@code context}'s format is invalid
	 */
	public BuilderCreator context(String context)
	{
		client.validateName(context, "context");
		this.context = context;
		return this;
	}

	/**
	 * Creates the builder.
	 *
	 * @return the name of the builder
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted before the operation completes. This can happen
	 *                              due to shutdown signals.
	 */
	public String create() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/buildx/create/
		List<String> arguments = new ArrayList<>(7);
		arguments.add("buildx");
		arguments.add("create");
		if (!name.isEmpty())
		{
			arguments.add("--name");
			arguments.add(name);
		}
		if (driver != null)
			arguments.addAll(driver.toCommandLine());
		if (!context.isEmpty())
			arguments.add(context);
		CommandResult result = client.run(arguments);
		return client.getBuildXParser().create(result);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(BuilderCreator.class).
			add("name", name).
			add("startEagerly", startEagerly).
			add("driver", driver).
			add("context", context).
			toString();
	}

	/**
	 * The backend used to execute builds.
	 */
	public sealed interface Driver
	{
		/**
		 * Returns the command-line representation of this option.
		 *
		 * @return the command-line options
		 */
		List<String> toCommandLine();

		/**
		 * Runs builds in an isolated BuildKit container. Enables advanced features like multi-node builds, better
		 * caching, and exporting to OCI.
		 *
		 * @return the driver
		 */
		@CheckReturnValue
		static DockerContainerDriverBuilder dockerContainer()
		{
			return new DockerContainerDriverBuilder();
		}

		/**
		 * Runs BuildKit inside a Kubernetes cluster.
		 *
		 * @return the driver
		 */
		@CheckReturnValue
		static KubernetesDriverBuilder kubernetes()
		{
			return new KubernetesDriverBuilder();
		}

		/**
		 * Connects to an existing BuildKit instance.
		 *
		 * @return the driver
		 */
		@CheckReturnValue
		static RemoteDriverBuilder remote()
		{
			return new RemoteDriverBuilder();
		}
	}

	/**
	 * Builds a BuildKit environment in a dedicated Docker container.
	 */
	public static final class DockerContainerDriverBuilder
	{
		/**
		 * Creates a new instance.
		 */
		private DockerContainerDriverBuilder()
		{
		}

		/**
		 * Builds the driver.
		 *
		 * @return the driver
		 */
		public Driver build()
		{
			return new DriverAdapter();
		}

		private static final class DriverAdapter implements Driver
		{
			@Override
			public List<String> toCommandLine()
			{
				return List.of("--driver=docker-container");
			}
		}
	}

	/**
	 * Builds a BuildKit environment inside a Kubernetes cluster.
	 */
	public static final class KubernetesDriverBuilder
	{
		/**
		 * Creates a new instance.
		 */
		private KubernetesDriverBuilder()
		{
		}

		/**
		 * Builds the driver.
		 *
		 * @return the driver
		 */
		public Driver build()
		{
			return new DriverAdapter();
		}

		private static final class DriverAdapter implements Driver
		{
			@Override
			public List<String> toCommandLine()
			{
				return List.of("--driver=kubernetes");
			}
		}
	}

	/**
	 * Connects to an already running BuildKit instance.
	 */
	public static final class RemoteDriverBuilder
	{
		/**
		 * Creates a new instance.
		 */
		private RemoteDriverBuilder()
		{
		}

		/**
		 * Builds the driver.
		 *
		 * @return the driver
		 */
		public Driver build()
		{
			return new DriverAdapter();
		}

		private static final class DriverAdapter implements Driver
		{
			@Override
			public List<String> toCommandLine()
			{
				return List.of("--driver=remote");
			}
		}
	}
}