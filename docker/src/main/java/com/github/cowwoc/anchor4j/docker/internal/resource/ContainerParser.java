package com.github.cowwoc.anchor4j.docker.internal.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.resource.AbstractParser;
import com.github.cowwoc.anchor4j.docker.exception.ResourceInUseException;
import com.github.cowwoc.anchor4j.docker.exception.ResourceNotFoundException;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.resource.Container;
import com.github.cowwoc.anchor4j.docker.resource.Container.HostConfiguration;
import com.github.cowwoc.anchor4j.docker.resource.Container.NetworkConfiguration;
import com.github.cowwoc.anchor4j.docker.resource.Container.PortBinding;
import com.github.cowwoc.anchor4j.docker.resource.Container.Status;
import com.github.cowwoc.anchor4j.docker.resource.ContainerElement;
import com.github.cowwoc.anchor4j.docker.resource.ContainerRemover;
import com.github.cowwoc.anchor4j.docker.resource.Protocol;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;
import static java.util.regex.Pattern.DOTALL;

/**
 * Parses server responses to {@code Container} commands.
 */
public final class ContainerParser extends AbstractParser
{
	private static final Pattern CONTAINER_NOT_FOUND = Pattern.compile("^Error response from daemon: " +
		"No such container: ([^ ]+).*", DOTALL);
	private static final Pattern IMAGE_NOT_FOUND = Pattern.compile("^Unable to find image '([^']+)' locally.*",
		DOTALL);
	private static final Pattern CONTAINER_IN_USE = Pattern.compile("Error response from daemon: cannot " +
		"remove container \"([^\"]+)\": container is running: stop the container before removing or force " +
		"remove");
	private static final Pattern CONFLICTING_NAME = Pattern.compile("Error response from daemon: " +
		"Conflict\\. The container name \"([^\"]+)\" is already in use by container \"([^\"]+)\"\\. You have " +
		"to remove \\(or rename\\) that container to be able to reuse that name\\.");

	/**
	 * Creates a parser.
	 *
	 * @param client the client configuration
	 */
	public ContainerParser(InternalDocker client)
	{
		super(client);
	}

	private InternalDocker getClient()
	{
		return (InternalDocker) client;
	}

	/**
	 * Lists all the configs.
	 *
	 * @param result the result of executing a command
	 * @return an empty list if no match is found
	 */
	public List<ContainerElement> list(CommandResult result)
	{
		if (result.exitCode() != 0)
			throw result.unexpectedResponse();
		JsonMapper jm = client.getJsonMapper();
		try
		{
			String[] lines = SPLIT_LINES.split(result.stdout());
			List<ContainerElement> elements = new ArrayList<>(lines.length);
			for (String line : lines)
			{
				if (line.isBlank())
					continue;
				JsonNode json = jm.readTree(line);
				String id = json.get("ID").textValue();
				String name = json.get("Names").textValue();
				assert that(name, "name").doesNotContain(",").
					elseThrow();
				elements.add(new ContainerElement(id, name));
			}
			return elements;
		}
		catch (JsonProcessingException e)
		{
			throw new AssertionError(e);
		}
	}

	/**
	 * Looks up a container by its ID or name.
	 *
	 * @param result the result of executing a command
	 * @return null if no match is found
	 */
	public Container get(CommandResult result)
	{
		if (result.exitCode() != 0)
		{
			if (CONTAINER_NOT_FOUND.matcher(result.stderr()).matches())
				return null;
			throw result.unexpectedResponse();
		}
		try
		{
			JsonNode json = client.getJsonMapper().readTree(result.stdout());
			assert json.size() == 1 : json;
			JsonNode container = json.get(0);

			String actualId = container.get("Id").textValue();
			String name = container.get("Name").textValue();
			// Internal representation of container names start with a slash for historical reasons. Strip them away.
			assert that(name, "name").startsWith("/").elseThrow();
			name = name.substring(1);

			HostConfiguration hostConfiguration = getHostConfiguration(container.get("HostConfig"));
			NetworkConfiguration networkConfiguration = getNetworkConfiguration(container.get("NetworkSettings"));
			JsonNode stateNode = container.get("State");
			Status status = SharedSecrets.getContainerStatus(stateNode.get("Status"));
			return SharedSecrets.getContainer(getClient(), actualId, name, hostConfiguration, networkConfiguration,
				status);
		}
		catch (JsonProcessingException e)
		{
			throw new AssertionError(e);
		}
	}

	private static HostConfiguration getHostConfiguration(JsonNode hostConfig)
	{
		JsonNode portBindingsNode = hostConfig.get("PortBindings");
		List<PortBinding> portBindings = getPortBindings(portBindingsNode);
		return new HostConfiguration(portBindings);
	}

	private static NetworkConfiguration getNetworkConfiguration(JsonNode networkSettings)
	{
		JsonNode portBindingsNode = networkSettings.get("Ports");
		List<PortBinding> portBindings = getPortBindings(portBindingsNode);
		return new NetworkConfiguration(portBindings);
	}

	@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
	private static List<PortBinding> getPortBindings(JsonNode portBindingsNode)
	{
		Set<Entry<String, JsonNode>> properties = portBindingsNode.properties();
		List<PortBinding> portBindings = new ArrayList<>(properties.size());
		for (Entry<String, JsonNode> entry : properties)
		{
			String[] portAndProtocol = SPLIT_ON_SLASH.split(entry.getKey());
			assert that(portAndProtocol, "portAndProtocol").length().isEqualTo(2).elseThrow();
			int containerPort = Integer.parseInt(portAndProtocol[0]);
			Protocol protocol = Protocol.valueOf(portAndProtocol[1].toUpperCase(Locale.ROOT));

			JsonNode hostNode = entry.getValue();
			List<InetSocketAddress> hostAddresses = new ArrayList<>(hostNode.size());
			if (!hostNode.isNull())
			{
				// A null value means that no host addresses are bound
				for (JsonNode address : hostNode)
				{
					// A container port may be mapped to multiple host ports
					String ipAddress = address.get("HostIp").textValue();
					if (ipAddress.isEmpty())
						ipAddress = "0.0.0.0";
					InetAddress hostAddress = InetAddress.ofLiteral(ipAddress);
					int hostPort = address.get("HostPort").asInt();
					hostAddresses.add(new InetSocketAddress(hostAddress, hostPort));
				}
			}
			portBindings.add(new PortBinding(containerPort, protocol, hostAddresses));
		}
		return portBindings;
	}

	/**
	 * Returns a container.
	 *
	 * @param result the result of executing a command
	 * @throws ResourceNotFoundException if the container does not exist
	 * @throws ResourceInUseException    if the requested name is in use by another container
	 */
	public void rename(CommandResult result) throws ResourceNotFoundException, ResourceInUseException
	{
		if (result.exitCode() != 0)
		{
			String stderr = result.stderr();
			Matcher matcher = IMAGE_NOT_FOUND.matcher(stderr);
			if (matcher.matches())
				throw new ResourceNotFoundException("Image not found: " + matcher.group(1));
			matcher = CONFLICTING_NAME.matcher(stderr);
			if (matcher.matches())
			{
				throw new ResourceInUseException("The container name \"" + matcher.group(1) + "\" is already in " +
					"use by container \"" + matcher.group(2) + "\". You have to remove (or rename) that container " +
					"to be able to reuse that name.");
			}
			throw result.unexpectedResponse();
		}
	}

	/**
	 * Creates a container.
	 *
	 * @param result the result of executing a command
	 * @return the ID of the new container
	 * @throws ResourceNotFoundException if the referenced image is not available locally and cannot be pulled
	 *                                   from Docker Hub, either because the repository does not exist or
	 *                                   requires different authentication credentials
	 * @throws ResourceInUseException    if the requested name is in use by another container
	 */
	public String create(CommandResult result) throws ResourceNotFoundException, ResourceInUseException
	{
		if (result.exitCode() != 0)
		{
			String stderr = result.stderr();
			Matcher matcher = IMAGE_NOT_FOUND.matcher(stderr);
			if (matcher.matches())
				throw new ResourceNotFoundException("Image not found: " + matcher.group(1));
			matcher = CONFLICTING_NAME.matcher(stderr);
			if (matcher.matches())
			{
				throw new ResourceInUseException("The container name \"" + matcher.group(1) + "\" is already in " +
					"use by container \"" + matcher.group(2) + "\". You have to remove (or rename) that container " +
					"to be able to reuse that name.");
			}
			throw result.unexpectedResponse();
		}
		return result.stdout();
	}

	/**
	 * Starts the container. If the container is already started, this method has no effect.
	 *
	 * @param result the result of executing a command
	 * @throws ResourceNotFoundException if the container no longer exists
	 */
	public void start(CommandResult result) throws ResourceNotFoundException
	{
		if (result.exitCode() != 0)
		{
			String stderr = result.stderr();
			Matcher matcher = CONTAINER_NOT_FOUND.matcher(stderr);
			if (matcher.matches())
				throw new ResourceNotFoundException("Container not found: " + matcher.group(1));
			throw result.unexpectedResponse();
		}
	}

	/**
	 * Stops the container.
	 *
	 * @param result the result of executing a command
	 * @throws ResourceNotFoundException if the container no longer exists
	 */
	public void stop(CommandResult result) throws ResourceNotFoundException
	{
		if (result.exitCode() != 0)
		{
			String stderr = result.stderr();
			Matcher matcher = CONTAINER_NOT_FOUND.matcher(stderr);
			if (matcher.matches())
				throw new ResourceNotFoundException("Container not found: " + matcher.group(1));
			throw result.unexpectedResponse();
		}
	}

	/**
	 * Waits until the container stops. If the container is already stopped, this method has no effect.
	 *
	 * @param result the result of executing a command
	 * @return the exit code returned by the container
	 * @throws ResourceNotFoundException if the container no longer exists
	 */
	public int waitUntilStopped(CommandResult result) throws ResourceNotFoundException
	{
		if (result.exitCode() != 0)
		{
			String stderr = result.stderr();
			Matcher matcher = CONTAINER_NOT_FOUND.matcher(stderr);
			if (matcher.matches())
				throw new ResourceNotFoundException("Container not found: " + matcher.group(1));
			throw result.unexpectedResponse();
		}
		String stdout = result.stdout();
		try
		{
			return Integer.parseInt(stdout);
		}
		catch (NumberFormatException e)
		{
			throw new AssertionError(e);
		}
	}

	/**
	 * Removes the container. If the container does not exist, this method has no effect.
	 *
	 * @param result the result of executing a command
	 * @throws ResourceInUseException if the container is running and {@link ContainerRemover#kill()} was not
	 *                                used
	 */
	public void remove(CommandResult result) throws ResourceInUseException
	{
		if (result.exitCode() != 0)
		{
			String stderr = result.stderr();
			if (CONTAINER_NOT_FOUND.matcher(stderr).matches())
				return;
			Matcher matcher = CONTAINER_IN_USE.matcher(stderr);
			if (matcher.matches())
				throw new ResourceInUseException("Container must be stopped first: " + matcher.group(1));
			throw result.unexpectedResponse();
		}
	}
}