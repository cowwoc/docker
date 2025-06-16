package com.github.cowwoc.anchor4j.docker.internal.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.resource.AbstractParser;
import com.github.cowwoc.anchor4j.docker.exception.NotSwarmManagerException;
import com.github.cowwoc.anchor4j.docker.exception.ResourceInUseException;
import com.github.cowwoc.anchor4j.docker.exception.ResourceNotFoundException;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.resource.Task;
import com.github.cowwoc.anchor4j.docker.resource.Task.State;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.DOTALL;

/**
 * Parses server responses to {@code Task} commands.
 */
public class ServiceParser extends AbstractParser
{
	private static final Pattern IMAGE_NOT_FOUND = Pattern.compile("^Unable to find image '([^']+)' locally.*",
		DOTALL);
	private static final Pattern CONFLICTING_NAME = Pattern.compile("""
		Error response from daemon: Conflict. The container name "([^"]+)" is already in use by container \
		"([^"]+)"\\. You have to remove \\(or rename\\) that container to be able to reuse that name\\.""");
	private static final String NOT_A_MANAGER = """
		Error response from daemon: This node is not a swarm manager. Worker nodes can't be used to view or \
		modify cluster state. Please run this command on a manager node or promote the current node to a \
		manager.""";

	/**
	 * Creates a parser.
	 *
	 * @param client the client configuration
	 */
	public ServiceParser(InternalDocker client)
	{
		super(client);
	}

	/**
	 * Creates a container.
	 *
	 * @param result the result of executing a command
	 * @return the ID of the new container
	 * @throws NotSwarmManagerException  if the current node is not a swarm manager
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
			if (stderr.equals(NOT_A_MANAGER))
				throw new NotSwarmManagerException();
			throw result.unexpectedResponse();
		}
		String stdout = result.stdout();
		Matcher matcher = SPLIT_LINES.matcher(stdout);
		if (!matcher.find())
			throw result.unexpectedResponse();
		return stdout.substring(0, matcher.start());
	}

	/**
	 * @param result the result of executing a command
	 * @return the task
	 */
	public Task get(CommandResult result)
	{
		if (result.exitCode() != 0)
			throw result.unexpectedResponse();

		JsonMapper jm = client.getJsonMapper();
		try
		{
			JsonNode json = jm.readTree(result.stdout());
			assert json.size() == 1 : json;
			JsonNode task = json.get(0);

			String id = task.get("ID").textValue();
			String name = task.get("Name").textValue();
			State state = SharedSecrets.getTaskStateFromJson(json.get("CurrentState"));
			return SharedSecrets.createTask(id, name, state);
		}
		catch (JsonProcessingException e)
		{
			throw new AssertionError(e);
		}
	}
}