package com.github.cowwoc.docker.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.cowwoc.docker.client.DockerClient;
import com.github.cowwoc.docker.internal.client.InternalClient;
import com.github.cowwoc.docker.internal.util.ToStringBuilder;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.OK_200;

/**
 * A docker network.
 */
public final class Network
{
	/**
	 * Looks up a network by its name or ID.
	 *
	 * @param client the client configuration
	 * @param id     the name or ID
	 * @return null if no match is found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code id} contains leading or trailing whitespace, or is empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public static Network getById(DockerClient client, String id)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(id, "id").isStripped().isNotEmpty();

		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Network/operation/NetworkInspect
		InternalClient ic = (InternalClient) client;
		URI uri = ic.getServer().resolve("networks/" + id);
		Request request = ic.createRequest(uri).method(GET);

		ContentResponse serverResponse = ic.send(request);
		return switch (serverResponse.getStatus())
		{
			case OK_200 ->
			{
				JsonNode body = ic.getResponseBody(serverResponse);
				yield getByJson(body);
			}
			case NOT_FOUND_404 -> null;
			default -> throw new AssertionError("Unexpected response: " + ic.toString(serverResponse) + "\n" +
				"Request: " + ic.toString(request));
		};
	}

	/**
	 * @param json the JSON representation of the node
	 * @return the network
	 * @throws NullPointerException if any of the arguments are null
	 */
	private static Network getByJson(JsonNode json)
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Network/operation/NetworkInspect
		String id = json.get("Id").textValue();
		String name = json.get("Name").textValue();

		JsonNode ipAddressManagement = json.get("IPAM");
		ArrayNode configsNode = (ArrayNode) ipAddressManagement.get("Config");
		List<Configuration> configurations = new ArrayList<>();
		for (JsonNode configNode : configsNode)
		{
			String subnet = configNode.get("Subnet").textValue();
			String gateway = configNode.get("Gateway").textValue();
			configurations.add(new Configuration(subnet, gateway));
		}
		return new Network(id, name, configurations);
	}

	private final String id;
	private final String name;
	private final List<Configuration> configurations;

	/**
	 * Creates a new network.
	 *
	 * @param id             the ID of the network
	 * @param name           the name of the network
	 * @param configurations the network configurations
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or is
	 *                                  empty
	 */
	private Network(String id, String name, List<Configuration> configurations)
	{
		assert that(id, "id").isStripped().isNotEmpty().elseThrow();
		assert that(name, "name").isStripped().isNotEmpty().elseThrow();
		this.id = id;
		this.name = name;
		this.configurations = List.copyOf(configurations);
	}

	/**
	 * Returns the ID of the network.
	 *
	 * @return the ID
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * Returns the name of the network.
	 *
	 * @return the name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns the network's configurations.
	 *
	 * @return the configurations
	 */
	public List<Configuration> getConfigurations()
	{
		return configurations;
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder().
			add("id", id).
			add("name", name).
			add("configurations", configurations).
			toString();
	}

	/**
	 * A network configuration.
	 *
	 * @param subnet  the network's subnet CIDR
	 * @param gateway the network's gateway
	 */
	public record Configuration(String subnet, String gateway)
	{
		/**
		 * Creates a new instance.
		 *
		 * @param subnet  the network's subnet CIDR
		 * @param gateway the network's gateway
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or is
		 *                                  empty
		 */
		public Configuration
		{
			requireThat(subnet, "subnet").isStripped().isNotEmpty();
			requireThat(gateway, "gateway").isStripped().isNotEmpty();
		}

		@Override
		public String toString()
		{
			return new ToStringBuilder().
				add("subnet", subnet).
				add("gateway", gateway).
				toString();
		}
	}
}