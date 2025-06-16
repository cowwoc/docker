package com.github.cowwoc.anchor4j.docker.internal.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.resource.AbstractParser;
import com.github.cowwoc.anchor4j.docker.exception.NotSwarmManagerException;
import com.github.cowwoc.anchor4j.docker.exception.ResourceInUseException;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.resource.Config;
import com.github.cowwoc.anchor4j.docker.resource.ConfigElement;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.cowwoc.anchor4j.docker.internal.resource.NodeParser.NOT_SWARM_MANAGER;

/**
 * Parses server responses to {@code Config} commands.
 */
public final class ConfigParser extends AbstractParser
{
	private static final Pattern NOT_FOUND = Pattern.compile(
		"Error response from daemon: config [^ ]+ not found");
	private static final Pattern NAME_IN_USE = Pattern.compile(
		"Error response from daemon: rpc error: code = AlreadyExists desc = config ([^ ]+) already exists");

	/**
	 * Creates a parser.
	 *
	 * @param client the client configuration
	 */
	public ConfigParser(InternalDocker client)
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
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 */
	public List<ConfigElement> list(CommandResult result)
	{
		if (result.exitCode() != 0)
		{
			if (result.stderr().startsWith(NOT_SWARM_MANAGER))
				throw new NotSwarmManagerException();
			throw result.unexpectedResponse();
		}
		JsonMapper jm = client.getJsonMapper();
		try
		{
			String[] lines = SPLIT_LINES.split(result.stdout());
			List<ConfigElement> elements = new ArrayList<>(lines.length);
			for (String line : lines)
			{
				if (line.isBlank())
					continue;
				JsonNode json = jm.readTree(line);
				String id = json.get("ID").textValue();
				String name = json.get("Name").textValue();
				elements.add(new ConfigElement(id, name));
			}
			return elements;
		}
		catch (JsonProcessingException e)
		{
			throw new AssertionError(e);
		}
	}

	/**
	 * Looks up a config by its ID or name.
	 *
	 * @param result the result of executing a command
	 * @return null if no match is found
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 */
	public Config get(CommandResult result)
	{
		if (result.exitCode() != 0)
		{
			String stderr = result.stderr();
			if (stderr.startsWith(NOT_SWARM_MANAGER))
				throw new NotSwarmManagerException();
			if (NOT_FOUND.matcher(stderr).matches())
				return null;
			throw result.unexpectedResponse();
		}
		JsonMapper jm = client.getJsonMapper();
		try
		{
			JsonNode json = jm.readTree(result.stdout());
			assert json.size() == 1 : json;

			JsonNode config = json.get(0);
			String actualId = config.get("ID").textValue();
			JsonNode spec = config.get("Spec");
			String name = spec.get("Name").textValue();
			String data = spec.get("Data").textValue();
			ByteBuffer decodedData = ByteBuffer.wrap(Base64.getUrlDecoder().decode(data));
			return SharedSecrets.getConfig(getClient(), actualId, name, decodedData);
		}
		catch (JsonProcessingException e)
		{
			throw new AssertionError(e);
		}
	}

	/**
	 * Creates a config.
	 *
	 * @param result the result of executing a command
	 * @return the ID of the new config
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws NotSwarmManagerException if the current node is not a swarm manager
	 * @throws ResourceInUseException   if the name is already in use by another config
	 */
	public String create(CommandResult result) throws ResourceInUseException
	{
		if (result.exitCode() != 0)
		{
			String stderr = result.stderr();
			if (stderr.startsWith(NOT_SWARM_MANAGER))
				throw new NotSwarmManagerException();
			Matcher matcher = NAME_IN_USE.matcher(stderr);
			if (matcher.matches())
				throw new ResourceInUseException("Config name \"" + matcher.group(1) + "\" is already in use.");
			throw result.unexpectedResponse();
		}
		return result.stdout();
	}
}