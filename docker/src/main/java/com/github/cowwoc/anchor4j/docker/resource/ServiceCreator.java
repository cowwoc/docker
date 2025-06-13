package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.anchor4j.docker.exception.NotSwarmManagerException;
import com.github.cowwoc.anchor4j.docker.exception.ResourceInUseException;
import com.github.cowwoc.anchor4j.docker.exception.ResourceNotFoundException;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.resource.ContainerCreator.BindMount;
import com.github.cowwoc.anchor4j.docker.resource.ContainerCreator.BindMountOptions;
import com.github.cowwoc.anchor4j.docker.resource.ContainerCreator.PortAndProtocol;
import com.github.cowwoc.anchor4j.docker.resource.ContainerCreator.PortBinding;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;

import static com.github.cowwoc.anchor4j.docker.resource.Protocol.TCP;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Creates a service, representing a group of containers.
 */
public final class ServiceCreator
{
	private static final RestartPolicy DEFAULT_RESTART_POLICY = new RestartPolicy(RestartPolicyCondition.ANY,
		Duration.ofSeconds(5), Integer.MAX_VALUE, Duration.ZERO);
	private final InternalDocker client;
	private final String imageId;
	private String name = "";
	private List<String> entrypoint = List.of();
	private List<String> arguments = List.of();
	private String workingDirectory = "";
	private final Map<String, String> environmentVariables = new HashMap<>();
	private final Map<Path, BindMount> hostPathToBindMount = new HashMap<>();
	private final Map<PortAndProtocol, InetSocketAddress> containerToHostPort = new HashMap<>();
	private boolean runOncePerNode;
	private int numberOfReplicas;
	private RestartPolicy restartPolicy = DEFAULT_RESTART_POLICY;
	private Duration updateMonitor = Duration.ofSeconds(5);

	/**
	 * Creates a container creator.
	 *
	 * @param client  the client configuration
	 * @param imageId the image ID or {@link Image reference} to create containers from
	 * @throws NullPointerException     if {@code imageId} is null
	 * @throws IllegalArgumentException if {@code imageId}:
	 *                                  <ul>
	 *                                    <li>is empty.</li>
	 *                                    <li>contains any character other than lowercase letters (a–z),
	 *                                    digits (0–9), and the following characters: {@code '.'}, {@code '/'},
	 *                                    {@code ':'}, {@code '_'}, {@code '-'}, {@code '@'}.</li>
	 *                                  </ul>
	 */
	ServiceCreator(InternalDocker client, String imageId)
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
	 *             followed by up by additional characters consisting of letters, digits, underscores, periods
	 *             or hyphens.
	 * @return this
	 * @throws NullPointerException     if {@code name} is null
	 * @throws IllegalArgumentException if {@code name}'s format is invalid
	 */
	public ServiceCreator name(String name)
	{
		client.validateName(name, "name");
		this.name = name;
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
	public ServiceCreator entrypoint(String... entrypoint)
	{
		requireThat(entrypoint, "entrypoint").length().isGreaterThanOrEqualTo(1);
		for (String element : entrypoint)
		{
			requireThat(element, "element").withContext(entrypoint, "entrypoint").
				doesNotContainWhitespace().isNotEmpty();
		}
		this.entrypoint = List.copyOf(Arrays.asList(entrypoint));
		return this;
	}

	/**
	 * Sets the container's {@code CMD}, overriding any value set in the Dockerfile or becoming the full command
	 * if the Dockerfile does not contain an {@code ENTRYPOINT}, and no new {@link #entrypoint(String...)} is
	 * specified.
	 *
	 * @param arguments the arguments
	 * @return this
	 * @throws NullPointerException     if {@code arguments} is null
	 * @throws IllegalArgumentException if {@code arguments} contains whitespace or is empty
	 */
	public ServiceCreator arguments(String... arguments)
	{
		requireThat(arguments, "arguments").isNotNull();
		this.arguments = List.copyOf(Arrays.asList(arguments));
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
	public ServiceCreator workingDirectory(String workingDirectory)
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
	public ServiceCreator environmentVariable(String name, String value)
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
	public ServiceCreator bindPath(Path hostPath, String containerPath, BindMountOptions... options)
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
	public ServiceCreator bindPort(PortBinding configuration)
	{
		requireThat(configuration, "configuration").isNotNull();
		containerToHostPort.put(new PortAndProtocol(configuration.containerPort, configuration.protocol),
			new InetSocketAddress(configuration.hostAddress, configuration.hostPort));
		return this;
	}

	/**
	 * Indicates that the service should run a fixed number of replicas (copies) of the task. By default, only a
	 * single replica is run.
	 *
	 * @param replicas the number of copies
	 * @return this
	 * @throws IllegalArgumentException if {@code replicas} is negative or zero
	 * @see #runOncePerNode()
	 */
	public ServiceCreator runMultipleCopies(int replicas)
	{
		this.runOncePerNode = false;
		this.numberOfReplicas = replicas;
		return this;
	}

	/**
	 * Indicates that the service should run the task on each active node in the swarm. By default, the service
	 * only runs a specified number of replicas of the task.
	 *
	 * @return this
	 * @see #runMultipleCopies(int)
	 */
	public ServiceCreator runOncePerNode()
	{
		this.runOncePerNode = true;
		return this;
	}

	/**
	 * Indicates that the tasks are expected to run to completion exactly once and are not restarted afterward.
	 * <p>
	 * Once a task exits, it will not be restarted; even if the number of replicas is scaled or the node
	 * restarts. This is useful for batch jobs or one-off processes that should not restart on failure or
	 * shutdown.
	 * <p>
	 * By default, tasks are restarted within five seconds of exiting, unless the service is removed.
	 *
	 * @return this
	 */
	public ServiceCreator runOnce()
	{
		this.restartPolicy = new RestartPolicy(RestartPolicyCondition.NONE, Duration.ZERO, 0, Duration.ZERO);
		return this;
	}

	/**
	 * Indicates that the container should automatically restart when it stops, regardless of the reason. By
	 * default, tasks are restarted automatically five seconds after shutting down.
	 *
	 * @param maximumAttempts the number of times to retry before giving up
	 * @param delay           the amount of time to wait after a task has exited before attempting to restart
	 *                        it
	 * @param slidingWindow   the time period during which failures are counted towards {@code maximumAttempts}.
	 *                        If the task fails more than {@code maximumAttempts} times within this period, it
	 *                        will not be restarted again. If set to zero, all failures are counted without any
	 *                        time constraint.
	 * @return this
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code maximumAttempts}, {@code delay} or {@code slidingWindow} are
	 *                                  negative
	 */
	public ServiceCreator alwaysRestart(int maximumAttempts, Duration delay, Duration slidingWindow)
	{
		this.restartPolicy = new RestartPolicy(RestartPolicyCondition.ANY, delay, maximumAttempts,
			slidingWindow);
		return this;
	}

	/**
	 * Configures the container to restart if its exit code is non-zero.
	 *
	 * @param maximumAttempts the number of times to retry before giving up
	 * @param delay           the amount of time to wait after a task has exited before attempting to restart
	 *                        it
	 * @param slidingWindow   the time period during which failures are counted towards {@code maximumAttempts}.
	 *                        If the task fails more than {@code maximumAttempts} times within this period, it
	 *                        will not be restarted again. If set to zero, all failures are counted without any
	 *                        time constraint.
	 * @return this
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code maximumAttempts}, {@code delay} or {@code slidingWindow} are
	 *                                  negative
	 * @throws IllegalArgumentException if {@code maximumAttempts} is negative
	 */
	public ServiceCreator restartOnFailure(int maximumAttempts, Duration delay, Duration slidingWindow)
	{
		this.restartPolicy = new RestartPolicy(RestartPolicyCondition.ON_FAILURE, delay, maximumAttempts,
			slidingWindow);
		return this;
	}

	/**
	 * Specifies the duration to monitor each task for failures after starting. By default, tasks are monitored
	 * for 5 seconds after startup.
	 * <p>
	 * This parameter is useful for detecting unstable or crashing containers during rolling updates.
	 *
	 * @param updateMonitor The duration to monitor updated tasks for failure
	 * @return this
	 * @throws NullPointerException     if {@code updateMonitor} is null
	 * @throws IllegalArgumentException if {@code updateMonitor} is negative or zero
	 */
	public ServiceCreator updateMonitor(Duration updateMonitor)
	{
		requireThat(updateMonitor, "updateMonitor").isGreaterThan(Duration.ZERO);
		this.updateMonitor = updateMonitor;
		return this;
	}

	/**
	 * Creates the service.
	 *
	 * @return the ID of the new service
	 * @throws ResourceNotFoundException if the referenced image is not available locally and cannot be pulled
	 *                                   from Docker Hub, either because the repository does not exist or
	 *                                   requires different authentication credentials
	 * @throws ResourceInUseException    if the requested name is in use by another service
	 * @throws NotSwarmManagerException  if the current node is not a swarm manager
	 * @throws IOException               if an I/O error occurs. These errors are typically transient, and
	 *                                   retrying the request may resolve the issue.
	 * @throws InterruptedException      if the thread is interrupted before the operation completes. This can
	 *                                   happen due to shutdown signals.
	 */
	public String create() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/service/create/
		List<String> arguments = new ArrayList<>(4 + environmentVariables.size() * 2 +
			hostPathToBindMount.size() * 2 + 5 + containerToHostPort.size() * 2 + 3 + entrypoint.size() + 2 +
			this.arguments.size());
		arguments.add("service");
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
		addBindPortArguments(arguments);
		if (!restartPolicy.equals(DEFAULT_RESTART_POLICY))
		{
			if (restartPolicy.condition != DEFAULT_RESTART_POLICY.condition)
			{
				arguments.add("--restart-condition");
				arguments.add(restartPolicy.condition.toCommandLine());
			}
			if (!restartPolicy.delay.equals(DEFAULT_RESTART_POLICY.delay))
			{
				arguments.add("--restart-delay");
				arguments.add(toString(restartPolicy.delay));
			}
			if (restartPolicy.maximumAttempts != DEFAULT_RESTART_POLICY.maximumAttempts)
			{
				arguments.add("--restart-max-attempts");
				arguments.add(String.valueOf(restartPolicy.maximumAttempts));
			}
			if (!restartPolicy.slidingWindow.equals(DEFAULT_RESTART_POLICY.slidingWindow))
			{
				arguments.add("--restart-window");
				arguments.add(toString(restartPolicy.slidingWindow));
			}
		}
		if (!updateMonitor.equals(Duration.ofSeconds(5)))
		{
			arguments.add("--update-monitor");
			arguments.add(toString(updateMonitor));
		}
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
		return client.getServiceParser().create(result);
	}

	/**
	 * @param duration a duration
	 * @return the String representation of the duration
	 */
	private String toString(Duration duration)
	{
		assert that(duration, "duration").isGreaterThanOrEqualTo(Duration.ZERO).elseThrow();
		if (duration.isZero())
			return "0s";

		Duration timeLeft = duration;
		StringBuilder result = new StringBuilder();

		int hours = timeLeft.toHoursPart();
		if (hours > 0)
		{
			result.append(hours).append('h');
			timeLeft = timeLeft.minusHours(hours);
		}

		int minutes = timeLeft.toMinutesPart();
		if (minutes > 0)
		{
			result.append(minutes).append('m');
			timeLeft = timeLeft.minusMinutes(minutes);
		}

		int seconds = timeLeft.toSecondsPart();
		if (seconds > 0)
		{
			result.append(seconds).append('s');
			timeLeft = timeLeft.minusSeconds(seconds);
		}

		int milliseconds = timeLeft.toMillisPart();
		if (milliseconds > 0)
		{
			result.append(milliseconds).append("ms");
			timeLeft = timeLeft.minusMillis(milliseconds);
		}

		int microseconds = Math.toIntExact(timeLeft.dividedBy(ChronoUnit.MICROS.getDuration()));
		if (microseconds > 0)
		{
			result.append(microseconds).append("us");
			timeLeft = timeLeft.minus(ChronoUnit.MICROS.getDuration().multipliedBy(microseconds));
		}

		long nanoseconds = timeLeft.getNano();
		if (nanoseconds > 0)
			result.append(nanoseconds).append("ns");
		return result.toString();
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

			BindMount mount = entry.getValue();
			options.add("target=" + mount.containerPath());
			for (BindMountOptions option : mount.options())
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
			value.append(host.getPort()).append(':').append(portAndProtocol.port());
			if (portAndProtocol.protocol() != TCP)
				value.append('/').append(portAndProtocol.protocol());

			arguments.add("--publish");
			arguments.add(value.toString());
		}
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(ServiceCreator.class).
			add("name", name).
			add("entrypoint", entrypoint).
			add("arguments", arguments).
			add("workingDirectory", workingDirectory).
			add("environmentVariables", environmentVariables).
			add("hostPathToBindMount", hostPathToBindMount).
			add("containerToHostPort", containerToHostPort).
			add("runOnEachActiveNode", runOncePerNode).
			add("numberOfReplicas", numberOfReplicas).
			add("restartPolicy", restartPolicy).
			toString();
	}

	/**
	 * A mode of operation.
	 */
	public enum Mode
	{
		/**
		 * The service runs a specific number copies (replicas) of the task.
		 */
		REPLICATED,
		/**
		 * The service runs the task on every active node.
		 */
		GLOBAL
	}

	/**
	 * Determines when the container should automatically restart.
	 *
	 * @param condition       the conditions under which the container is restarted automatically
	 * @param maximumAttempts the number of times to retry before giving up
	 * @param delay           the amount of time to wait after a task has exited before attempting to restart
	 *                        it
	 * @param slidingWindow   the time period during which failures are counted towards {@code maximumAttempts}.
	 *                        If the task fails more than {@code maximumAttempts} times within this period, it
	 *                        will not be restarted again. If set to zero, all failures are counted without any
	 *                        time constraint.
	 */
	public record RestartPolicy(RestartPolicyCondition condition, Duration delay, int maximumAttempts,
	                            Duration slidingWindow)
	{
		/**
		 * Creates a restart policy.
		 *
		 * @param condition       the conditions under which the container is restarted automatically
		 * @param maximumAttempts the number of times to retry before giving up
		 * @param delay           the amount of time to wait after a task has exited before attempting to restart
		 *                        it
		 * @param slidingWindow   the time period during which failures are counted towards
		 *                        {@code maximumAttempts}. If the task fails more than {@code maximumAttempts}
		 *                        times within this period, it will not be restarted again. If set to zero, all
		 *                        failures are counted without any time constraint.
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if {@code condition} is {@code ALWAYS} or {@code ON_FAILURE}, and
		 *                                  {@code maximumAttempts}, {@code delay} or {@code window} are negative
		 */
		public RestartPolicy
		{
			requireThat(condition, "condition").isNotNull();
			switch (condition)
			{
				case NONE ->
				{
				}
				case ANY, ON_FAILURE ->
				{
					requireThat(delay, "delay").isGreaterThanOrEqualTo(Duration.ZERO);
					requireThat(maximumAttempts, "maximumAttempts").isNotNegative();
					requireThat(slidingWindow, "slidingWindow").isGreaterThanOrEqualTo(Duration.ZERO);
				}
			}
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
		NONE,
		/**
		 * Always restart on exit.
		 */
		ANY,
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
