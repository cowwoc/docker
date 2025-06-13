package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.anchor4j.docker.exception.ResourceInUseException;
import com.github.cowwoc.anchor4j.docker.exception.ResourceNotFoundException;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import static com.github.cowwoc.anchor4j.docker.resource.Protocol.TCP;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Creates a container.
 */
public final class ContainerCreator
{
	/**
	 * The format of container names.
	 */
	static final Pattern NAME_PATTERN = Pattern.compile("\\w[\\w.-]{0,127}");
	private final InternalDocker client;
	private final String imageId;
	private String name = "";
	private String platform = "";
	private boolean privileged;
	private List<String> entrypoint = List.of();
	private List<String> arguments = List.of();
	private String workingDirectory = "";
	private final Map<String, String> environmentVariables = new HashMap<>();
	private final Map<Path, BindMount> hostPathToBindMount = new HashMap<>();
	private final Map<PortAndProtocol, InetSocketAddress> containerToHostPort = new HashMap<>();
	private boolean removeOnExit;
	private RestartPolicy restartPolicy = new RestartPolicy(RestartPolicyCondition.NO, 0);

	/**
	 * Creates a container creator.
	 *
	 * @param client  the client configuration
	 * @param imageId the image ID or {@link Image reference} to create the container from
	 * @throws NullPointerException     if {@code imageId} is null
	 * @throws IllegalArgumentException if {@code imageId}:
	 *                                  <ul>
	 *                                    <li>is empty.</li>
	 *                                    <li>contains any character other than lowercase letters (a–z),
	 *                                    digits (0–9), and the following characters: {@code '.'}, {@code '/'},
	 *                                    {@code ':'}, {@code '_'}, {@code '-'}, {@code '@'}.</li>
	 *                                  </ul>
	 */
	ContainerCreator(InternalDocker client, String imageId)
	{
		assert that(client, "client").isNotNull().elseThrow();
		client.validateImageReference(imageId, "imageId");
		this.client = client;
		this.imageId = imageId;
	}

	/**
	 * Sets the name of the container.
	 *
	 * @param name the container name. The value must start with a letter, or digit, or underscore, and may be
	 *             followed by additional characters consisting of letters, digits, underscores, periods or
	 *             hyphens.
	 * @return this
	 * @throws NullPointerException     if {@code name} is null
	 * @throws IllegalArgumentException if {@code name}'s format is invalid
	 */
	public ContainerCreator name(String name)
	{
		client.validateName(name, "name");
		if (!name.isEmpty() && !NAME_PATTERN.matcher(name).matches())
		{
			throw new IllegalArgumentException("name must start with a letter, or digit, or underscore, and may " +
				"be followed by additional characters consisting of letters, digits, underscores, periods or " +
				"hyphens.\n" +
				"Actual: " + name);
		}
		this.name = name;
		return this;
	}

	/**
	 * Sets the platform of the container.
	 *
	 * @param platform the platform
	 * @return this
	 * @throws NullPointerException     if {@code platform} is null
	 * @throws IllegalArgumentException if {@code platform} contains whitespace or is empty
	 */
	public ContainerCreator platform(String platform)
	{
		requireThat(platform, "platform").doesNotContainWhitespace().isNotEmpty();
		this.platform = platform;
		return this;
	}

	/**
	 * Sets the container's {@code ENTRYPOINT}, overriding any value set in the Dockerfile. If a {@code CMD}
	 * instruction is present in the Dockerfile, it will be ignored when {@code ENTRYPOINT} is overridden.
	 *
	 * @param entrypoint the command
	 * @return this
	 * @throws NullPointerException     if {@code command} is null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>{@code command} is empty.</li>
	 *                                    <li>any of {@code command}'s elements contain whitespace or are
	 *                                    empty.</li>
	 *                                  </ul>
	 */
	public ContainerCreator entrypoint(String... entrypoint)
	{
		return entrypoint(Arrays.asList(entrypoint));
	}

	/**
	 * Sets the container's {@code ENTRYPOINT}, overriding any value set in the Dockerfile. If a {@code CMD}
	 * instruction is present in the Dockerfile, it will be ignored when {@code ENTRYPOINT} is overridden.
	 *
	 * @param entrypoint the command
	 * @return this
	 * @throws NullPointerException     if {@code command} is null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>{@code command} is empty.</li>
	 *                                    <li>any of {@code command}'s elements contain whitespace or are
	 *                                    empty.</li>
	 *                                  </ul>
	 */
	public ContainerCreator entrypoint(List<String> entrypoint)
	{
		requireThat(entrypoint, "entrypoint").size().isGreaterThanOrEqualTo(1);
		for (String element : entrypoint)
		{
			requireThat(element, "element").withContext(entrypoint, "entrypoint").
				doesNotContainWhitespace().isNotEmpty();
		}
		this.entrypoint = List.copyOf(entrypoint);
		return this;
	}

	/**
	 * Sets the container's {@code CMD}, overriding any value set in the Dockerfile or becoming the full command
	 * if the Dockerfile does not contain an {@code ENTRYPOINT}, and no new {@link #entrypoint(List)} is
	 * specified.
	 *
	 * @param arguments the arguments
	 * @return this
	 * @throws NullPointerException     if {@code arguments} is null
	 * @throws IllegalArgumentException if {@code arguments} contains whitespace or is empty
	 */
	public ContainerCreator arguments(String... arguments)
	{
		return arguments(Arrays.asList(arguments));
	}

	/**
	 * Sets the container's {@code CMD}, overriding any value set in the Dockerfile or becoming the full command
	 * if the Dockerfile does not contain an {@code ENTRYPOINT}, and no new {@link #entrypoint(List)} is
	 * specified.
	 *
	 * @param arguments the arguments
	 * @return this
	 * @throws NullPointerException     if {@code arguments} is null
	 * @throws IllegalArgumentException if {@code arguments} contains whitespace or is empty
	 */
	public ContainerCreator arguments(List<String> arguments)
	{
		requireThat(arguments, "arguments").isNotNull();
		this.arguments = List.copyOf(arguments);
		return this;
	}

	/**
	 * Sets the working directory to run commands in.
	 *
	 * @param workingDirectory the working directory
	 * @return this
	 * @throws NullPointerException     if {@code workingDirectory} is null
	 * @throws IllegalArgumentException if {@code workingDirectory} contains whitespace or is empty
	 */
	public ContainerCreator workingDirectory(String workingDirectory)
	{
		requireThat(workingDirectory, "workingDirectory").doesNotContainWhitespace().isNotEmpty();
		this.workingDirectory = workingDirectory;
		return this;
	}

	/**
	 * Adds or replaces an environment variable.
	 *
	 * @param name  the name of the variable
	 * @param value the value of the variable
	 * @return this
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain whitespace
	 */
	public ContainerCreator environmentVariable(String name, String value)
	{
		requireThat(name, "name").doesNotContainWhitespace().isNotEmpty();
		requireThat(value, "value").doesNotContainWhitespace();
		this.environmentVariables.put(name, value);
		return this;
	}

	/**
	 * Binds a path from the host to the container. Any modification applied on either end is mirrored to the
	 * other.
	 *
	 * @param hostPath      a path on the host
	 * @param containerPath a path on the container
	 * @param options       mounting options
	 * @return this
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>{@code hostPath} or {@code containerPath} contain leading or
	 *                                    trailing whitespace or are empty.</li>
	 *                                    <li>{@code containerPath} is not an absolute path.</li>
	 *                                  </ul>
	 */
	public ContainerCreator bindPath(Path hostPath, String containerPath, BindMountOptions... options)
	{
		// REMINDER: containerPath is not a Path because Paths are resolved relative to the host
		requireThat(hostPath, "hostPath").isNotNull();
		this.hostPathToBindMount.put(hostPath.toAbsolutePath(),
			new BindMount(containerPath, options));
		return this;
	}

	/**
	 * Binds a container port to a host port.
	 *
	 * @param configuration the port binding configuration
	 * @return this
	 * @throws NullPointerException if {@code configuration} is null
	 */
	public ContainerCreator bindPort(PortBinding configuration)
	{
		requireThat(configuration, "configuration").isNotNull();
		containerToHostPort.put(new PortAndProtocol(configuration.containerPort, configuration.protocol),
			new InetSocketAddress(configuration.hostAddress, configuration.hostPort));
		return this;
	}

	/**
	 * Indicates that the container and its associated anonymous volumes should be automatically removed upon
	 * exit.
	 *
	 * @return this
	 * @throws IllegalArgumentException if {@code restartPolicy} is enabled
	 */
	public ContainerCreator removeOnExit()
	{
		if (restartPolicy.condition != RestartPolicyCondition.NO)
			throw new IllegalArgumentException("removeOnExit cannot be enabled if restartPolicy is set");
		this.removeOnExit = true;
		return this;
	}

	/**
	 * Indicates that the container should automatically restart when it stops, regardless of the reason. By
	 * default, the container is not restarted automatically.
	 *
	 * @return this
	 * @throws IllegalArgumentException {@code removeOnExit} is {@code true}
	 */
	public ContainerCreator alwaysRestart()
	{
		if (removeOnExit)
			throw new IllegalArgumentException("restartPolicy may not be set if removeOnExit is enabled");
		this.restartPolicy = new RestartPolicy(RestartPolicyCondition.ALWAYS, Integer.MAX_VALUE);
		return this;
	}

	/**
	 * Indicates that the container should automatically restart unless it is manually stopped. By default, the
	 * container is not restarted automatically.
	 *
	 * @return this
	 * @throws IllegalArgumentException {@code removeOnExit} is {@code true}
	 */
	public ContainerCreator restartUnlessStopped()
	{
		if (removeOnExit)
			throw new IllegalArgumentException("restartPolicy may not be set if removeOnExit is enabled");
		this.restartPolicy = new RestartPolicy(RestartPolicyCondition.UNLESS_STOPPED, Integer.MAX_VALUE);
		return this;
	}

	/**
	 * Configures the container to restart if its exit code is non-zero. By default, the container is not
	 * restarted automatically.
	 *
	 * @param maximumAttempts the number of times to retry before giving up
	 * @return this
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>{@code removeOnExit} is {@code true}.</li>
	 *                                    <li>{@code maximumAttempts} is negative or zero.</li>
	 *                                  </ul>
	 */
	public ContainerCreator restartOnFailure(int maximumAttempts)
	{
		if (removeOnExit)
			throw new IllegalArgumentException("restartPolicy may not be set if removeOnExit is enabled");
		this.restartPolicy = new RestartPolicy(RestartPolicyCondition.ON_FAILURE, maximumAttempts);
		return this;
	}

	/**
	 * Grants the container permission to do almost everything that the host can do. This is typically used to
	 * run Docker inside Docker.
	 * <ul>
	 * <li>Enables all Linux kernel capabilities.</li>
	 * <li>Disables the default seccomp profile.</li>
	 * <li>Disables the default AppArmor profile.</li>
	 * <li>Disables the SELinux process label.</li>
	 * <li>Makes {@code /sys} read-write Makes.</li>
	 * <li>Makes {@code cgroups} mounts read-write.</li>
	 * </ul>
	 * Use this flag with caution. Containers in this mode can get a root shell on the host and take control
	 * over the system.
	 *
	 * @return this
	 */
	public ContainerCreator privileged()
	{
		// Documentation taken from https://docs.docker.com/reference/cli/docker/container/run/#privileged
		this.privileged = true;
		return this;
	}

	/**
	 * Creates the container.
	 *
	 * @return the ID of the new container
	 * @throws ResourceNotFoundException if the referenced image is not available locally and cannot be pulled
	 *                                   from Docker Hub, either because the repository does not exist or
	 *                                   requires different authentication credentials
	 * @throws ResourceInUseException    if the requested name is in use by another container
	 * @throws IOException               if an I/O error occurs. These errors are typically transient, and
	 *                                   retrying the request may resolve the issue.
	 * @throws InterruptedException      if the thread is interrupted before the operation completes. This can
	 *                                   happen due to shutdown signals.
	 */
	public String create() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/container/create/
		List<String> arguments = new ArrayList<>(4 + environmentVariables.size() * 2 +
			hostPathToBindMount.size() * 2 + 5 + containerToHostPort.size() * 2 + 3 + entrypoint.size() +
			this.arguments.size());
		arguments.add("container");
		arguments.add("create");
		if (!entrypoint.isEmpty())
		{
			arguments.add("--entrypoint");
			// If a user specifies:
			// ENTRYPOINT ["executable", "param1", "param2"]
			// CMD ["param3", "param4"]
			//
			// then the actual command that will be executed will be: "executable param1 param2 param3 param4"
			// To translate this behavior to command-line arguments, we need to pass the first value to --entrypoint
			// and all remaining values as arguments.
			arguments.add(entrypoint.getFirst());
		}
		if (!environmentVariables.isEmpty())
		{
			for (Entry<String, String> entry : environmentVariables.entrySet())
			{
				arguments.add("--env");
				arguments.add(entry.getKey() + "=" + entry.getValue());
			}
		}
		addBindPathArguments(arguments);
		if (!name.isEmpty())
		{
			arguments.add("--name");
			arguments.add(name);
		}
		if (!platform.isEmpty())
		{
			arguments.add("--platform");
			arguments.add(platform);
		}
		if (privileged)
			arguments.add("--privileged");
		addBindPortArguments(arguments);
		if (restartPolicy.condition != RestartPolicyCondition.NO)
		{
			arguments.add("--restart");
			StringBuilder value = new StringBuilder(restartPolicy.condition.toCommandLine());
			if (restartPolicy.condition == RestartPolicyCondition.ON_FAILURE)
				value.append(':').append(restartPolicy.maximumAttempts);
			arguments.add(value.toString());
		}
		if (removeOnExit)
			arguments.add("--rm");
		if (!workingDirectory.isEmpty())
		{
			arguments.add("--workdir");
			arguments.add(workingDirectory);
		}
		arguments.add(imageId);
		if (entrypoint.size() > 1)
			arguments.addAll(entrypoint.subList(1, entrypoint.size()));
		if (!this.arguments.isEmpty())
			arguments.addAll(this.arguments);
		CommandResult result = client.run(arguments);
		return client.getContainerParser().create(result);
	}

	private void addBindPathArguments(List<String> arguments)
	{
		if (hostPathToBindMount.isEmpty())
			return;
		for (Entry<Path, BindMount> entry : hostPathToBindMount.entrySet())
		{
			// https://docs.docker.com/engine/storage/bind-mounts/#options-for---mount
			arguments.add("--mount");
			StringJoiner options = new StringJoiner(",");
			options.add("type=bind");
			options.add("source=" + entry.getKey());

			BindMount bind = entry.getValue();
			options.add("target=" + bind.containerPath);
			for (BindMountOptions option : bind.options)
				options.add(option.toJson());
			arguments.add(options.toString());
		}
	}

	private void addBindPortArguments(List<String> arguments)
	{
		if (containerToHostPort.isEmpty())
			return;
		for (Entry<PortAndProtocol, InetSocketAddress> entry : containerToHostPort.entrySet())
		{
			PortAndProtocol portAndProtocol = entry.getKey();
			InetSocketAddress host = entry.getValue();
			StringBuilder value = new StringBuilder();
			InetAddress hostAddress = host.getAddress();
			if (!hostAddress.isAnyLocalAddress())
				value.append(hostAddress.getHostAddress()).append(':');
			value.append(host.getPort()).append(':').append(portAndProtocol.port);
			if (portAndProtocol.protocol != TCP)
				value.append('/').append(portAndProtocol.protocol());

			arguments.add("--publish");
			arguments.add(value.toString());
		}
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(ContainerCreator.class).
			add("name", name).
			add("platform", platform).
			add("privileged", privileged).
			add("entrypoint", entrypoint).
			add("arguments", arguments).
			add("workingDirectory", workingDirectory).
			add("environmentVariables", environmentVariables).
			add("hostPathToBindMount", hostPathToBindMount).
			add("containerToHostPort", containerToHostPort).
			add("removeOnExit", removeOnExit).
			add("restartPolicy", restartPolicy).
			toString();
	}

	/**
	 * Options that apply to bind mounts.
	 *
	 * @see <a href="https://docs.docker.com/engine/storage/bind-mounts/">bind mounts</a>
	 */
	public enum BindMountOptions
	{
		/**
		 * Prevents the container from modifying files inside the bind mount.
		 */
		READ_ONLY;

		/**
		 * Returns the version's JSON representation.
		 *
		 * @return the JSON representation
		 */
		public String toJson()
		{
			return switch (this)
			{
				case READ_ONLY -> "readonly";
			};
		}
	}

	/**
	 * The configuration of a bind mount.
	 *
	 * @param containerPath a path on the container
	 * @param options       mounting options
	 * @see <a href="https://docs.docker.com/engine/storage/bind-mounts/">bind mounts</a>
	 */
	record BindMount(String containerPath, BindMountOptions... options)
	{
		/**
		 * Creates a mount.
		 *
		 * @param containerPath a path on the container
		 * @param options       mounting options
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if {@code containerPath} is not an absolute path
		 */
		// WORKAROUND: https://github.com/checkstyle/checkstyle/issues/17158
		@SuppressWarnings("PMD.ArrayIsStoredDirectly")
		BindMount(String containerPath, BindMountOptions... options)
		{
			requireThat(containerPath, "containerPath").startsWith("/");
			for (BindMountOptions option : options)
				requireThat(option, "option").isNotNull();
			this.containerPath = containerPath;
			this.options = options;
		}
	}

	/**
	 * The configuration for binding a container port to a host port.
	 */
	public static final class PortBinding
	{
		final int containerPort;
		Protocol protocol = TCP;
		InetAddress hostAddress;
		int hostPort;

		/**
		 * Creates a binding.
		 *
		 * @param containerPort the container port to bind to
		 * @throws IllegalArgumentException if {@code containerPort} is negative or zero
		 */
		@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
		public PortBinding(int containerPort)
		{
			requireThat(containerPort, "containerPort").isPositive();
			this.containerPort = containerPort;
			this.hostAddress = InetAddress.ofLiteral("0.0.0.0");
		}

		/**
		 * Sets the host address to bind to. By default, {@code 0.0.0.0} is used.
		 *
		 * @param hostAddress the host address
		 * @return this
		 * @throws NullPointerException if {@code hostAddress} is null
		 */
		public PortBinding hostAddress(InetAddress hostAddress)
		{
			requireThat(hostAddress, "hostAddress").isNotNull();
			this.hostAddress = hostAddress;
			return this;
		}

		/**
		 * Sets the host port to bind to. By default, an arbitrary available port will be used.
		 *
		 * @param hostPort the host port, or {@code 0} to use an arbitrary available port
		 * @return this
		 * @throws IllegalArgumentException if {@code hostPort} is negative
		 */
		public PortBinding hostPort(int hostPort)
		{
			requireThat(hostPort, "hostPort").isNotNegative();
			this.hostPort = hostPort;
			return this;
		}

		/**
		 * Sets the communication protocol to bind to. By default, {@link Protocol#TCP} will be used.
		 *
		 * @param protocol the communication protocol to use
		 * @return this
		 * @throws NullPointerException if {@code protocol} is null
		 */
		public PortBinding protocol(Protocol protocol)
		{
			requireThat(protocol, "protocol").isNotNull();
			this.protocol = protocol;
			return this;
		}
	}

	/**
	 * A network port and protocol.
	 *
	 * @param port     a networking port
	 * @param protocol the protocol
	 */
	record PortAndProtocol(int port, Protocol protocol)
	{
		/**
		 * Creates a port and protocol.
		 *
		 * @param port     an Internet address
		 * @param protocol the protocol
		 */
		// WORKAROUND: https://github.com/checkstyle/checkstyle/issues/17158
		PortAndProtocol(int port, Protocol protocol)
		{
			assert that(port, "port").isPositive().elseThrow();
			assert that(protocol, "protocol").isNotNull().elseThrow();
			this.port = port;
			this.protocol = protocol;
		}
	}

	/**
	 * Determines when the container should automatically restart.
	 *
	 * @param condition       the conditions under which the container is restarted automatically
	 * @param maximumAttempts if {@link RestartPolicyCondition#ON_FAILURE} is used, the number of times to retry
	 *                        before giving up
	 * @see <a
	 * 	href="https://docs.docker.com/engine/containers/start-containers-automatically/#use-a-restart-policy">Docker
	 * 	documentation</a>
	 */
	public record RestartPolicy(RestartPolicyCondition condition, int maximumAttempts)
	{
		/**
		 * Creates a restart policy.
		 *
		 * @param condition       the conditions under which the container is restarted automatically
		 * @param maximumAttempts the maximum number of times to restart
		 * @throws NullPointerException     if {@code condition} is null
		 * @throws IllegalArgumentException if {@code condition} is {@code ON_FAILURE} and {@code maximumAttempts}
		 *                                  is negative or zero
		 */
		public RestartPolicy
		{
			requireThat(condition, "condition").isNotNull();
			if (condition == RestartPolicyCondition.ON_FAILURE)
				requireThat(maximumAttempts, "maximumAttempts").isPositive();
		}
	}

	/**
	 * Determines when a container is restarted automatically.
	 */
	public enum RestartPolicyCondition
	{
		/**
		 * Do not automatically restart.
		 */
		NO,
		/**
		 * Always restart on exit.
		 */
		ALWAYS,
		/**
		 * Restart the container unless it is manually stopped.
		 */
		UNLESS_STOPPED,
		/**
		 * Restart the container if its exit code is non-zero.
		 */
		ON_FAILURE;

		/**
		 * Returns the command-line representation of this option.
		 *
		 * @return the command-line value
		 */
		public String toCommandLine()
		{
			return name().toLowerCase(Locale.ROOT).replace('_', '-');
		}
	}
}