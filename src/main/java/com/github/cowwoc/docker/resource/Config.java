package com.github.cowwoc.docker.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.cowwoc.docker.client.DockerClient;
import com.github.cowwoc.docker.internal.util.ClientRequests;
import com.github.cowwoc.docker.internal.util.Dockers;
import com.github.cowwoc.docker.internal.util.ToStringBuilder;
import com.github.cowwoc.requirements10.annotation.CheckReturnValue;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.http.HttpStatus.SERVICE_UNAVAILABLE_503;

/**
 * Non-sensitive configuration that is stored in the swarm.
 */
public final class Config
{
	/**
	 * Looks up a config by its ID.
	 *
	 * @param client the client configuration
	 * @param id     the config's ID
	 * @return null if no match is found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code id} contains leading or trailing whitespace or is empty
	 * @throws IllegalStateException    if:
	 *                                  <ul>
	 *                                    <li>the client is closed.</li>
	 *                                    <li>the server is not part of a swarm.</li>
	 *                                  </ul>
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public static Config getById(DockerClient client, String id)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Config/operation/ConfigInspect
		String uri = client.getUri() + "/configs/" + id;
		ClientRequests clientRequests = client.getClientRequests();
		Request request = client.getHttpClient().newRequest(uri).
			transport(client.getTransport()).
			method(GET);
		ContentResponse serverResponse = clientRequests.send(request);
		switch (serverResponse.getStatus())
		{
			case OK_200 ->
			{
				// success
			}
			case NOT_FOUND_404 ->
			{
				return null;
			}
			case INTERNAL_SERVER_ERROR_500 ->
			{
				JsonNode json = client.getObjectMapper().readTree(serverResponse.getContentAsString());
				throw new IOException(json.get("message").textValue());
			}
			case SERVICE_UNAVAILABLE_503 ->
			{
				// The node is not part of a swarm
				JsonNode json = client.getObjectMapper().readTree(serverResponse.getContentAsString());
				throw new IllegalStateException(json.get("message").textValue());
			}
			default -> throw new AssertionError("Unexpected response: " +
				clientRequests.toString(serverResponse) + "\n" +
				"Request: " + clientRequests.toString(request));
		}
		JsonNode body = Dockers.getResponseBody(client, serverResponse);
		return getByJson(client, body);
	}

	/**
	 * Creates a new config.
	 *
	 * @param client the client configuration
	 * @param name   the name of the config
	 * @param value  the value of the config
	 * @return the new Config
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code name} contains leading or trailing whitespace or is empty
	 * @throws IllegalStateException    if:
	 *                                  <ul>
	 *                                    <li>the client is closed.</li>
	 *                                    <li>the server is not part of a swarm.</li>
	 *                                  </ul>
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public static Config create(DockerClient client, String name, byte[] value)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(value, "value").isNotNull();

		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Config/operation/ConfigCreate
		String uri = client.getUri() + "/configs/create";
		ClientRequests clientRequests = client.getClientRequests();
		ObjectMapper om = client.getObjectMapper();
		ObjectNode requestBody = om.createObjectNode();
		requestBody.put("Name", name);
		requestBody.put("Data", Base64.getUrlEncoder().encodeToString(value));
		Request request = client.getHttpClient().newRequest(uri).
			transport(client.getTransport()).
			method(GET);
		ContentResponse serverResponse = clientRequests.send(request);
		switch (serverResponse.getStatus())
		{
			case CREATED_201 ->
			{
				// success
			}
			case INTERNAL_SERVER_ERROR_500 ->
			{
				JsonNode json = om.readTree(serverResponse.getContentAsString());
				throw new IOException(json.get("message").textValue());
			}
			case SERVICE_UNAVAILABLE_503 ->
			{
				// The node is not part of a swarm
				JsonNode json = om.readTree(serverResponse.getContentAsString());
				throw new IllegalStateException(json.get("message").textValue());
			}
			default -> throw new AssertionError("Unexpected response: " +
				clientRequests.toString(serverResponse) + "\n" +
				"Request: " + clientRequests.toString(request));
		}
		JsonNode body = Dockers.getResponseBody(client, serverResponse);
		String id = body.get("Id").textValue();
		return getById(client, id);
	}

	/**
	 * Returns all the configs.
	 *
	 * @param client the client configuration
	 * @return an empty list if no match is found
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if:
	 *                               <ul>
	 *                                 <li>the client is closed.</li>
	 *                                 <li>the server is not part of a swarm.</li>
	 *                               </ul>
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public List<Config> getAll(DockerClient client) throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Config/operation/ConfigList
		String uri = client.getUri() + "/configs";
		ClientRequests clientRequests = client.getClientRequests();
		Request request = client.getHttpClient().newRequest(uri).
			transport(client.getTransport()).
			method(GET);
		ContentResponse serverResponse = clientRequests.send(request);
		switch (serverResponse.getStatus())
		{
			case OK_200 ->
			{
				// success
			}
			case NOT_FOUND_404 ->
			{
				return List.of();
			}
			case INTERNAL_SERVER_ERROR_500 ->
			{
				JsonNode json = client.getObjectMapper().readTree(serverResponse.getContentAsString());
				throw new IOException(json.get("message").textValue());
			}
			case SERVICE_UNAVAILABLE_503 ->
			{
				// The node is not part of a swarm
				JsonNode json = client.getObjectMapper().readTree(serverResponse.getContentAsString());
				throw new IllegalStateException(json.get("message").textValue());
			}
			default -> throw new AssertionError("Unexpected response: " +
				clientRequests.toString(serverResponse) + "\n" +
				"Request: " + clientRequests.toString(request));
		}
		JsonNode body = Dockers.getResponseBody(client, serverResponse);
		List<Config> configs = new ArrayList<>();
		for (JsonNode config : body)
			configs.add(getByJson(client, config));
		return configs;
	}

	/**
	 * Looks up a config by its name.
	 *
	 * @param client the client configuration
	 * @param name   the name of the config
	 * @return null if no match is found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code name} contains leading or trailing whitespace or is empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public static Config getByName(DockerClient client, String name)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Config/operation/ConfigList
		String uri = client.getUri() + "/configs";
		ClientRequests clientRequests = client.getClientRequests();
		Request request = client.getHttpClient().newRequest(uri).
			transport(client.getTransport()).
			method(GET);
		ContentResponse serverResponse = clientRequests.send(request);
		switch (serverResponse.getStatus())
		{
			case OK_200 ->
			{
				// success
			}
			case INTERNAL_SERVER_ERROR_500 ->
			{
				JsonNode json = client.getObjectMapper().readTree(serverResponse.getContentAsString());
				throw new IOException(json.get("message").textValue());
			}
			case SERVICE_UNAVAILABLE_503 ->
			{
				// The node is not part of a swarm
				JsonNode json = client.getObjectMapper().readTree(serverResponse.getContentAsString());
				throw new IllegalStateException(json.get("message").textValue());
			}
			default -> throw new AssertionError("Unexpected response: " +
				clientRequests.toString(serverResponse) + "\n" +
				"Request: " + clientRequests.toString(request));
		}
		JsonNode body = Dockers.getResponseBody(client, serverResponse);
		for (JsonNode entry : body)
		{
			Config config = getByJson(client, entry);
			if (config.getName().equals(name))
				return config;
		}
		return null;
	}

	/**
	 * @param client the client configuration
	 * @param json   the JSON representation of the config
	 * @return the node
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 */
	static Config getByJson(DockerClient client, JsonNode json)
	{
		String id = json.get("ID").textValue();
		int version = Dockers.getVersion(json);
		JsonNode spec = json.get("Spec");
		String name = spec.get("Name").textValue();
		String value = spec.get("Value").textValue();
		byte[] valueAsBytes = Base64.getUrlDecoder().decode(value);
		return new Config(client, id, version, name, valueAsBytes);
	}

	private final DockerClient client;
	private final String id;
	private final int version;
	private final String name;
	private final byte[] value;

	/**
	 * Creates a new reference to a config.
	 *
	 * @param client  the client configuration
	 * @param id      the config's ID
	 * @param version the version number of the config. This is used to avoid conflicting writes.
	 * @param name    the config's name
	 * @param value   the config's value
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain leading or trailing whitespace.</li>
	 *                                    <li>the {@code name} is empty.</li>
	 *                                  </ul>
	 */
	private Config(DockerClient client, String id, int version, String name, byte[] value)
	{
		requireThat(client, "client").isNotNull();
		requireThat(id, "id").isStripped().isNotEmpty();
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(value, "value").isNotNull();
		this.client = client;
		this.id = id;
		this.version = version;
		this.name = name;
		this.value = Arrays.copyOf(value, value.length);
	}

	/**
	 * Returns the config's id.
	 *
	 * @return the config's id
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * Returns the config's name.
	 *
	 * @return the config's name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns config value.
	 *
	 * @return config value
	 */
	public byte[] getValue()
	{
		return value.clone();
	}

	/**
	 * Reloads the config's state.
	 *
	 * @return the updated state
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	@CheckReturnValue
	public Config reload() throws IOException, TimeoutException, InterruptedException
	{
		return getById(client, id);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(Config.class).
			add("id", id).
			add("version", version).
			add("name", name).
			add("value", value).
			toString();
	}
}