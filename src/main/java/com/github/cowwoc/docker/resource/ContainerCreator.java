package com.github.cowwoc.docker.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.cowwoc.docker.internal.client.InternalClient;
import com.github.cowwoc.docker.internal.util.ToStringBuilder;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;

/**
 * Creates a container.
 */
public final class ContainerCreator
{
	private final InternalClient client;
	private final String id;
	private String name = "";
	private String platform = "";
	private List<String> command = List.of();
	private List<String> arguments = List.of();
	private String workingDirectory = "";
	private final Map<String, String> environmentVariables = new HashMap<>();
	private final Map<Path, BindMount> hostPathToBindMount = new HashMap<>();
	private boolean autoRemove;
	private RestartPolicy restartPolicy;

	/**
	 * Creates a new instance.
	 *
	 * @param client the client configuration
	 * @param id     the image's name, digest or ID. If a name is specified, it may include a tag or a digest.
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 */
	ContainerCreator(InternalClient client, String id)
	{
		requireThat(client, "client").isNotNull();
		requireThat(id, "id").isStripped().isNotEmpty();
		this.client = client;
		this.id = id;
	}

	/**
	 * Sets the name of the container.
	 *
	 * @param name the name
	 * @return this
	 * @throws NullPointerException     if {@code name} is null
	 * @throws IllegalArgumentException if {@code name} contains leading or trailing whitespace or is empty
	 */
	public ContainerCreator name(String name)
	{
		requireThat(name, "name").isStripped().isNotEmpty();
		this.name = name;
		return this;
	}

	/**
	 * Sets the platform of the container.
	 *
	 * @param platform the platform
	 * @return this
	 * @throws NullPointerException     if {@code platform} is null
	 * @throws IllegalArgumentException if {@code platform} contains leading or trailing whitespace or is empty
	 */
	public ContainerCreator platform(String platform)
	{
		requireThat(platform, "platform").isStripped().isNotEmpty();
		this.platform = platform;
		return this;
	}

	/**
	 * Sets the container's default executable and arguments (also known as the {@code entrypoint}).
	 *
	 * @param command the default executable and arguments
	 * @return this
	 * @throws NullPointerException     if {@code command} is null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>{@code command} is empty.</li>
	 *                                    <li>any of {@code command}'s elements contain leading or trailing
	 *                                    whitespace or are empty.</li>
	 *                                  </ul>
	 */
	public ContainerCreator command(String... command)
	{
		requireThat(command, "command").length().isGreaterThanOrEqualTo(1);
		for (String element : command)
			requireThat(element, "element").withContext(command, "command").isStripped().isNotEmpty();
		this.command = List.copyOf(Arrays.asList(command));
		return this;
	}

	/**
	 * Sets the container's default arguments (also known as the {@code cmd}).
	 *
	 * @param arguments the arguments
	 * @return this
	 * @throws NullPointerException     if {@code arguments} is null
	 * @throws IllegalArgumentException if {@code arguments} contains leading or trailing whitespace or is
	 *                                  empty
	 */
	public ContainerCreator arguments(String... arguments)
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
	 * @throws IllegalArgumentException if {@code workingDirectory} contains leading or trailing whitespace or
	 *                                  is empty
	 */
	public ContainerCreator workingDirectory(String workingDirectory)
	{
		requireThat(workingDirectory, "workingDirectory").isStripped().isNotEmpty();
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
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 */
	public ContainerCreator environmentVariable(String name, String value)
	{
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(value, "value").isStripped().isNotEmpty();
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
	public ContainerCreator bind(Path hostPath, String containerPath, BindMountOptions... options)
	{
		requireThat(hostPath, "hostPath").isNotNull();
		this.hostPathToBindMount.put(hostPath.toAbsolutePath(),
			new BindMount(containerPath, options));
		return this;
	}

	/**
	 * Determines whether the container should be automatically removed on exit.
	 *
	 * @param autoRemove {@code true} to remove on exit
	 * @return this
	 * @throws IllegalArgumentException if {@code restartPolicy} is enabled
	 */
	public ContainerCreator autoRemove(boolean autoRemove)
	{
		if (autoRemove && restartPolicy != null)
			throw new IllegalStateException("restartPolicy may not be set when autoRemove is enabled.");
		this.autoRemove = autoRemove;
		return this;
	}

	/**
	 * Configures the container to never restart.
	 *
	 * @return this
	 * @throws IllegalArgumentException {@code autoRemove} is {@code true}
	 */
	public ContainerCreator doNotRestart()
	{
		this.restartPolicy = null;
		return this;
	}

	/**
	 * Configures the container to always restart.
	 *
	 * @return this
	 * @throws IllegalArgumentException {@code autoRemove} is {@code true}
	 */
	public ContainerCreator alwaysRestart()
	{
		if (autoRemove)
			throw new IllegalStateException("restartPolicy may not be set when autoRemove is enabled.");
		this.restartPolicy = new RestartPolicy(RestartPolicyCondition.ALWAYS, 0);
		return this;
	}

	/**
	 * Configures the container to restart unless it is manually stopped.
	 *
	 * @return this
	 * @throws IllegalArgumentException {@code autoRemove} is {@code true}
	 */
	public ContainerCreator restartUnlessStopped()
	{
		if (autoRemove)
			throw new IllegalStateException("restartPolicy may not be set when autoRemove is enabled.");
		this.restartPolicy = new RestartPolicy(RestartPolicyCondition.UNLESS_STOPPED, 0);
		return this;
	}

	/**
	 * Configures the container to restart if its exit code is non-zero.
	 *
	 * @param maximumRetryCount the number of times to retry before giving up
	 * @return this
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>{@code autoRemove} is {@code true}.</li>
	 *                                    <li>{@code maximumRetryCount} is negative.</li>
	 *                                  </ul>
	 */
	public ContainerCreator restartOnFailure(int maximumRetryCount)
	{
		if (autoRemove)
			throw new IllegalStateException("restartPolicy may not be set when autoRemove is enabled.");
		this.restartPolicy = new RestartPolicy(RestartPolicyCondition.ON_FAILURE, maximumRetryCount);
		return this;
	}

	/**
	 * Creates the container.
	 *
	 * @return the result of the operation
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public CreateResult create() throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Container/operation/ContainerCreate
		URI uri = client.getServer().resolve("containers/create");

		JsonMapper jm = client.getJsonMapper();
		ObjectNode requestBody = jm.createObjectNode();
		if (!environmentVariables.isEmpty())
		{
			ArrayNode envNode = requestBody.putArray("Env");
			for (Entry<String, String> entry : environmentVariables.entrySet())
				envNode.add(entry.getKey() + "=" + entry.getValue());
		}
		requestBody.put("Image", id);
		if (!command.isEmpty())
		{
			ArrayNode commandNode = requestBody.putArray("Entrypoint");
			for (String element : command)
				commandNode.add(element);
		}
		if (!arguments.isEmpty())
		{
			ArrayNode argumentNode = requestBody.putArray("Cmd");
			for (String argument : arguments)
				argumentNode.add(argument);
		}
		if (!workingDirectory.isEmpty())
			requestBody.put("WorkingDir", workingDirectory);
		if (restartPolicy != null)
		{
			ObjectNode restartPolicyNode = requestBody.putObject("RestartPolicy").
				put("Name", restartPolicy.condition.toJson());
			if (restartPolicy.condition == RestartPolicyCondition.ON_FAILURE)
				restartPolicyNode.put("MaximumRetryCount", restartPolicy.maximumRetryCount);
		}

		ObjectNode hostConfig = jm.createObjectNode();
		if (autoRemove)
			hostConfig.put("AutoRemove", true);
		if (!hostPathToBindMount.isEmpty())
		{
			ArrayNode bindsArray = jm.createArrayNode();
			for (Entry<Path, BindMount> entry : hostPathToBindMount.entrySet())
			{
				BindMount container = entry.getValue();
				StringJoiner options = new StringJoiner(",", ":", "");
				for (BindMountOptions option : container.options)
					options.add(option.toJoin());
				bindsArray.add(entry.getKey() + ":" + container.containerPath + options);
			}
			if (!bindsArray.isEmpty())
				hostConfig.set("Binds", bindsArray);
		}
		if (!hostConfig.isEmpty())
			requestBody.set("HostConfig", hostConfig);

		Request request = client.createRequest(uri, requestBody);
		if (!name.isEmpty())
			request.param("name", name);
		if (!platform.isEmpty())
			request.param("platform", platform);
		request.method(POST);

		ContentResponse serverResponse = client.send(request);
		switch (serverResponse.getStatus())
		{
			case CREATED_201 ->
			{
				// success
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
		JsonNode responseBody = client.getResponseBody(serverResponse);
		String id = responseBody.get("Id").textValue();
		List<String> warnings = client.arrayToListOfString(responseBody.get("Warnings"), "Warnings");
		return new CreateResult(Container.getById(client, id), warnings);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(ContainerCreator.class).
			add("id", id).
			add("name", name).
			add("platform", platform).
			add("entrypoint", command).
			add("workingDirectory", workingDirectory).
			add("environmentVariables", environmentVariables).
			add("hostPathToBindMount", hostPathToBindMount).
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
		public String toJoin()
		{
			return switch (this)
			{
				case READ_ONLY -> "ro";
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
	// WORKAROUND: https://github.com/checkstyle/checkstyle/issues/15683
	@SuppressWarnings("checkstyle:javadocmethod")
	private record BindMount(String containerPath, BindMountOptions... options)
	{
		/**
		 * Creates a new instance.
		 *
		 * @param containerPath a path on the container
		 * @param options       mounting options
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if {@code containerPath} is not an absolute path
		 */
		private BindMount
		{
			requireThat(containerPath, "containerPath").startsWith("/");
			for (BindMountOptions option : options)
				requireThat(option, "option").isNotNull();
		}
	}

	/**
	 * The result of creating a container.
	 *
	 * @param container the created container
	 * @param warnings  warnings encountered when creating the container
	 */
	// WORKAROUND: https://github.com/checkstyle/checkstyle/issues/15683
	@SuppressWarnings("checkstyle:javadocmethod")
	public record CreateResult(Container container, List<String> warnings)
	{
		/**
		 * Creates a new instance.
		 *
		 * @param container the created container
		 * @param warnings  warnings encountered when creating the container, or an empty list if there are no
		 *                  warnings
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if {@code warnings} contains an element with leading or trailing
		 *                                  whitespace or is empty
		 */
		public CreateResult
		{
			requireThat(container, "container").isNotNull();
			requireThat(warnings, "warnings").isNotNull();
			for (String warning : warnings)
				requireThat(warning, "warning").isStripped().isNotEmpty();
		}
	}

	/**
	 * Determines when the container should automatically restart.
	 *
	 * @param condition         the conditions under which the container is restarted automatically
	 * @param maximumRetryCount if {@link RestartPolicyCondition#ON_FAILURE} is used, the number of times to
	 *                          retry before giving up
	 */
	// WORKAROUND: https://github.com/checkstyle/checkstyle/issues/15683
	@SuppressWarnings("checkstyle:javadocmethod")
	public record RestartPolicy(RestartPolicyCondition condition, int maximumRetryCount)
	{
		/**
		 * @param condition         the conditions under which the container is restarted automatically
		 * @param maximumRetryCount the maximum number of times to restart
		 * @throws NullPointerException     if {@code type} is null
		 * @throws IllegalArgumentException if {@code maximumRetryCount} is negative
		 */
		public RestartPolicy
		{
			requireThat(condition, "condition").isNotNull();
			requireThat(maximumRetryCount, "maximumRetryCount").isNotNegative();
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
		 * Returns the object's JSON representation.
		 *
		 * @return the JSON representation
		 */
		public String toJson()
		{
			return name().toLowerCase(Locale.ROOT);
		}
	}
}