package com.github.cowwoc.anchor4j.docker.internal.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.resource.AbstractParser;
import com.github.cowwoc.anchor4j.docker.exception.ResourceInUseException;
import com.github.cowwoc.anchor4j.docker.exception.ResourceNotFoundException;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.resource.Context;
import com.github.cowwoc.anchor4j.docker.resource.ContextElement;
import com.github.cowwoc.anchor4j.docker.resource.ContextEndpoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses server responses to {@code Context} commands.
 */
public class ContextParser extends AbstractParser
{
	private static final Pattern CONTEXT_NOT_FOUND = Pattern.compile("context \"([^\"]+)\" does not exist");
	private static final Pattern CONFLICTING_NAME = Pattern.compile("context \"([^\"]+)\" already exists");
	private static final Pattern REMOVE_FAILED_RESOURCE_IN_USE = Pattern.compile("failed to remove context " +
		"(.+?): failed to remove metadata: remove (.+?): The process cannot access the file because it is " +
		"being used by another process\\.");
	private static final Pattern TLS_CERTIFICATE_NOT_FOUND = Pattern.compile("unable to create docker " +
		"endpoint config: open ([^:]+): The system cannot find the (?:file|path) specified\\.");
	private static final Pattern TLS_CERTIFICATE_MISSING_DATA = Pattern.compile("unable to create docker " +
		"endpoint config: invalid docker endpoint options: failed to retrieve context tls info: tls: failed " +
		"to find any PEM data in certificate input");

	/**
	 * Creates a parser.
	 *
	 * @param client the client configuration
	 */
	public ContextParser(InternalDocker client)
	{
		super(client);
	}

	private InternalDocker getClient()
	{
		return (InternalDocker) client;
	}

	/**
	 * Lists all the contexts.
	 *
	 * @param result the result of executing a command
	 * @return the contexts
	 */
	public List<ContextElement> list(CommandResult result)
	{
		if (result.exitCode() != 0)
			throw result.unexpectedResponse();
		JsonMapper jm = client.getJsonMapper();
		try
		{
			String[] lines = SPLIT_LINES.split(result.stdout());
			List<ContextElement> elements = new ArrayList<>(lines.length);
			for (String line : lines)
			{
				if (line.isBlank())
					continue;
				JsonNode json = jm.readTree(line);
				boolean current = json.get("Current").booleanValue();
				String description = json.get("Description").textValue();
				String endpoint = json.get("DockerEndpoint").textValue();
				String error = json.get("Error").textValue();
				String name = json.get("Name").textValue();
				elements.add(new ContextElement(name, current, description, endpoint, error));
			}
			return elements;
		}
		catch (JsonProcessingException e)
		{
			throw new AssertionError(e);
		}
	}

	/**
	 * Looks up an image by its name.
	 *
	 * @param result the result of executing a command
	 * @return null if no match is found
	 */
	public Context getByName(CommandResult result)
	{
		if (result.exitCode() != 0)
			throw result.unexpectedResponse();
		JsonMapper jm = client.getJsonMapper();
		try
		{
			JsonNode json = jm.readTree(result.stdout());
			assert json.size() == 1 : json;
			JsonNode context = json.get(0);

			String actualName = context.get("Name").textValue();
			JsonNode metadata = context.get("Metadata");
			JsonNode descriptionNode = metadata.get("Description");
			String description;
			if (descriptionNode == null)
				description = "";
			else
				description = descriptionNode.textValue();
			JsonNode endpoints = context.get("Endpoints");
			JsonNode dockerEndpoint = endpoints.get("docker");
			String endpoint = dockerEndpoint.get("Host").textValue();
			return SharedSecrets.getContext(getClient(), actualName, description, endpoint);
		}
		catch (JsonProcessingException e)
		{
			throw new AssertionError(e);
		}
	}

	/**
	 * Returns the name of the current context.
	 *
	 * @param result the result of executing a command
	 * @return the name
	 */
	public String show(CommandResult result)
	{
		if (result.exitCode() != 0)
			throw result.unexpectedResponse();
		return result.stdout();
	}

	/**
	 * Set a client's current context.
	 *
	 * @param result the result of executing a command
	 */
	public void use(CommandResult result)
	{
		if (result.exitCode() != 0)
			throw result.unexpectedResponse();
	}

	/**
	 * Creates a context.
	 *
	 * @param result the result of executing a command
	 * @throws ResourceNotFoundException if any of the {@link ContextEndpoint referenced TLS files} is not
	 *                                   found
	 * @throws ResourceInUseException    if another context with the same name already exists
	 */
	public void create(CommandResult result) throws ResourceNotFoundException, ResourceInUseException
	{
		if (result.exitCode() != 0)
		{
			String stderr = result.stderr();
			Matcher matcher = CONFLICTING_NAME.matcher(stderr);
			if (matcher.matches())
				throw new ResourceInUseException("Name already in use: " + matcher.group(1));
			matcher = TLS_CERTIFICATE_NOT_FOUND.matcher(stderr);
			if (matcher.matches())
				throw new ResourceNotFoundException("TLS certificate not found: " + matcher.group(1));
			matcher = TLS_CERTIFICATE_MISSING_DATA.matcher(stderr);
			if (matcher.matches())
			{
				throw new ResourceNotFoundException("One of the TLS files referenced by the Context endpoint is " +
					"empty");
			}
			throw result.unexpectedResponse();
		}
	}

	/**
	 * Removes the context. If the context does not exist, this method has no effect.
	 *
	 * @param result the result of executing a command
	 * @throws IOException if an I/O error occurs. These errors are typically transient, and retrying the
	 *                     request may resolve the issue.
	 */
	public void remove(CommandResult result) throws IOException
	{
		if (result.exitCode() != 0)
		{
			String stderr = result.stderr();
			Matcher matcher = CONTEXT_NOT_FOUND.matcher(stderr);
			if (matcher.matches())
				return;
			matcher = REMOVE_FAILED_RESOURCE_IN_USE.matcher(stderr);
			if (matcher.matches())
			{
				throw new IOException("Failed to remove metadata because the file is being used by " +
					"another process.\n" +
					"Container: " + matcher.group(1) + "\n" +
					"File     : " + matcher.group(2));
			}
			throw result.unexpectedResponse();
		}
	}
}