package com.github.cowwoc.docker.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.cowwoc.docker.client.DockerClient;
import com.github.cowwoc.docker.internal.client.InternalClient;
import com.github.cowwoc.pouch.core.WrappedCheckedException;
import com.github.cowwoc.requirements10.annotation.CheckReturnValue;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.http.HttpFields;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.http.HttpStatus.SERVICE_UNAVAILABLE_503;

/**
 * A docker server.
 */
public class Server
{
	/**
	 * The API version required by this library.
	 */
	private static final BigDecimal REQUIRED_VERSION = new BigDecimal("1.47");
	/**
	 * The port used for communication with and between docker containers across hosts.
	 *
	 * @see <a
	 * 	href="https://docs.docker.com/engine/swarm/swarm-tutorial/#open-protocols-and-ports-between-the-hosts">documentation</a>
	 */
	private static final int DEFAULT_DATA_PATH_PORT = 4789;
	private final InternalClient client;
	private final String id;
	private final String nodeId;

	/**
	 * Determines if the server is accessible and supports the API version required by this library.
	 *
	 * @param client the client configuration
	 * @return {@code true} if the server is accessible, or {@code false} if it is not
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static boolean isAvailable(DockerClient client)
		throws IOException, TimeoutException, InterruptedException
	{
		InternalClient ic = (InternalClient) client;

		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/System/operation/SystemPing
		URI uri = ic.getServer().resolve("_ping");
		Request request = ic.createRequest(uri).
			method(GET);
		try
		{
			ContentResponse serverResponse = ic.send(request);
			switch (serverResponse.getStatus())
			{
				case OK_200 ->
				{
					// success
				}
				default -> throw new AssertionError(
					"Unexpected response: " + ic.toString(serverResponse) + "\n" +
						"Request: " + ic.toString(request));
			}
			HttpFields headers = serverResponse.getHeaders();
			BigDecimal supportedVersion = new BigDecimal(headers.get("API-Version"));
			return supportedVersion.compareTo(REQUIRED_VERSION) >= 0;
		}
		catch (ConnectException e)
		{
			return false;
		}
	}

	/**
	 * Returns the configured docker server that the client is connected to.
	 *
	 * @param client the client configuration
	 * @return the docker server
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static Server get(DockerClient client)
		throws IOException, TimeoutException, InterruptedException
	{
		InternalClient ic = (InternalClient) client;

		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/System/operation/SystemInfo
		URI uri = ic.getServer().resolve("info");
		Request request = ic.createRequest(uri).
			method(GET);
		ContentResponse serverResponse = ic.send(request);
		ic.expectOk200(request, serverResponse);
		JsonNode body = ic.getJsonMapper().readTree(serverResponse.getContentAsString());
		String id = body.get("ID").textValue();
		JsonNode swarm = body.get("Swarm");
		String nodeId;
		if (swarm == null)
			nodeId = "";
		else
			nodeId = swarm.get("NodeID").textValue();
		return new Server(ic, id, nodeId);
	}

	/**
	 * Creates a new server.
	 *
	 * @param client the client configuration
	 * @param id     the ID of the server
	 * @param nodeId (optional) the node ID if the server is a member of a swarm; otherwise, an empty string
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain leading or trailing whitespace.</li>
	 *                                    <li>any of the mandatory arguments is empty.</li>
	 *                                  </ul>
	 */
	Server(InternalClient client, String id, String nodeId)
	{
		assert that(client, "client").isNotNull().elseThrow();
		assert that(id, "id").isStripped().isNotEmpty().elseThrow();
		assert that(nodeId, "nodeId").isStripped().elseThrow();
		this.client = client;
		this.id = id;
		this.nodeId = nodeId;
	}

	/**
	 * Returns the ID of the docker server. This value is not related to {@link Node#getId()}.
	 *
	 * @return the server's ID
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * Returns the URI of the docker server.
	 *
	 * @return the URI
	 */
	public URI getUri()
	{
		return client.getServer();
	}

	/**
	 * Returns the server's {@link Node#getId() node ID}.
	 *
	 * @return the server's node ID
	 */
	public String getNodeId()
	{
		return nodeId;
	}

	/**
	 * Returns the swarm that the server is part of.
	 *
	 * @return {@code null} if the server is not part of a swarm
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public Swarm getSwarm() throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Swarm/operation/SwarmInspect
		@SuppressWarnings("PMD.CloseResource")
		URI uri = client.getServer().resolve("swarm");
		Request request = client.createRequest(uri).
			method(GET);
		ContentResponse serverResponse = client.send(request);
		JsonNode body = switch (serverResponse.getStatus())
		{
			case OK_200 ->
			{
				try
				{
					yield client.getJsonMapper().readTree(serverResponse.getContentAsString());
				}
				catch (JsonProcessingException e)
				{
					throw WrappedCheckedException.wrap(e);
				}
			}
			// The server is not a member of a swarm
			case SERVICE_UNAVAILABLE_503 -> null;
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		};
		if (body == null)
			return null;
		return Swarm.getByJson(client, body);
	}

	/**
	 * Creates a new Swarm cluster.
	 * <p>
	 * This method sets up the current node as a Swarm manager, creating the foundation for a distributed
	 * cluster. It configures critical networking and operational parameters required for the Swarm's
	 * functionality. The cluster can subsequently be scaled by adding worker or manager nodes.
	 * </p>
	 * <p>
	 * Networking details:
	 * <ul>
	 *   <li><b>Listen Address</b>: The address where the current node listens for inter-manager communication,
	 *   such as leader election and cluster state updates.</li>
	 *   <li><b>Data Path Address</b>: The address where the current node handles inter-container communication
	 *   over Swarm overlay networks.</li>
	 *   <li><b>Advertise Address</b>: The externally reachable address that other nodes use for connecting to the
	 *   manager API and joining the Swarm.</li>
	 *   <li><b>Node Discovery Port</b>: Used by the gossip protocol for node discovery, state synchronization,
	 *   and health monitoring between nodes. This port uses the same address as the Listen Address with a fixed
	 *   port number of {@code 7946}.</li>
	 * </ul>
	 *
	 * @param listenAddress      the address the node uses to listen for inter-manager communication (e.g.,
	 *                           {@code 0.0.0.0:2377})
	 * @param dataPathAddress    the address the node uses to listen for inter-container communication (e.g.,
	 *                           {@code 0.0.0.0:4789}). If no port is specified, {@code 4789} will be used. If a
	 *                           port is specified, it must be within the range {@code 1024} to {@code 49,151},
	 *                           inclusive.
	 * @param advertiseAddress   the externally reachable address advertised to other nodes for connecting to
	 *                           the manager (e.g., {@code 192.168.1.1:2377})
	 * @param labels             key-value metadata pairs that provide additional information about the Swarm,
	 *                           such as environment or usage context (e.g., {@code environment=production}).
	 * @param defaultAddressPool one or more subnets in CIDR notation used to allocate network addresses for
	 *                           overlay networks. If empty, internal defaults are used.
	 * @param subnetSize         the CIDR prefix length for each overlay network. Smaller values result in
	 *                           larger subnets, while larger values produce smaller subnets. The prefix length
	 *                           must be between {@code 16} and {@code 28} inclusive.
	 * @return the updated server
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain leading or trailing whitespace or
	 *                                    are empty.</li>
	 *                                    <li>{@code dataPathAddress} contains {@code :}.</li>
	 *                                    <li>{@code dataPathAddress} is equal to {@code 0.0.0.0}.</li>
	 *                                    <li>{@code dataPathPort} is less than 1024 or greater than
	 *                                    49,151.</li>
	 *                                    <li>{@code subnetSize} is less than 16 or greater than 28.</li>
	 *                                  </ul>
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	@CheckReturnValue
	public Server createSwarm(String listenAddress, String dataPathAddress, String advertiseAddress,
		Map<String, String> labels, Collection<String> defaultAddressPool, int subnetSize)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(listenAddress, "listenAddress").isStripped().isNotEmpty();
		requireThat(dataPathAddress, "dataPathAddress").isStripped().isNotEmpty();
		int colon = dataPathAddress.indexOf(':');
		int dataPathPort;
		if (colon == -1)
			dataPathPort = DEFAULT_DATA_PATH_PORT;
		else
		{
			dataPathPort = Integer.parseInt(dataPathAddress.substring(colon + 1));
			requireThat(dataPathPort, "dataPathPort").isBetween(1024, true, 49_151, true);
			dataPathAddress = dataPathAddress.substring(0, colon);
		}
		requireThat(advertiseAddress, "advertiseAddress").isStripped().isNotEmpty();
		requireThat(labels, "labels").isNotNull();
		requireThat(defaultAddressPool, "defaultAddressPool").isNotNull();
		requireThat(subnetSize, "subnetSize").isBetween(16, true, 28, true);

		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Swarm/operation/SwarmInit
		ObjectNode requestBody = client.getJsonMapper().createObjectNode();
		requestBody.put("ListenAddr", listenAddress);
		requestBody.put("AdvertiseAddr", advertiseAddress);
		requestBody.put("DataPathAddr", dataPathAddress);
		if (dataPathPort != DEFAULT_DATA_PATH_PORT)
			requestBody.put("DataPathPort", dataPathPort);
		if (!defaultAddressPool.isEmpty())
		{
			ArrayNode defaultAddrPool = requestBody.putArray("DefaultAddrPool");
			for (String subnet : defaultAddressPool)
				defaultAddrPool.add(subnet);
		}
		requestBody.put("SubnetSize", subnetSize);

		ObjectNode spec = requestBody.putObject("Spec");
		// https://git.causa-arcana.com/kotovalexarian-likes-github/moby--moby/commit/e374126ed111bac8fe66692a17ad7547e7240c73
		spec.put("Name", "default");
		ObjectNode labelsNode = spec.putObject("Labels");
		for (Entry<String, String> label : labels.entrySet())
			labelsNode.put(label.getKey(), label.getValue());

		URI uri = client.getServer().resolve("swarm/init");
		Request request = client.createRequest(uri, requestBody).
			method(POST);
		ContentResponse serverResponse = client.send(request);
		return switch (serverResponse.getStatus())
		{
			case OK_200 -> reload();
			case SERVICE_UNAVAILABLE_503 ->
			{
				// The server is already part of a swarm
				JsonNode responseBody = client.getJsonMapper().readTree(serverResponse.getContentAsString());
				String message = responseBody.get("message").textValue();
				throw new IllegalStateException(message);
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		};
	}

	/**
	 * Joins an existing swarm as a manager.
	 *
	 * @param listenAddress    the address that the current node will use to listen for inter-manager
	 *                         communication (e.g., {@code 0.0.0.0:2377})
	 * @param dataPathAddress  the address that the current node will use to listen for inter-container
	 *                         communication (e.g., {@code 0.0.0.0:4789}
	 * @param advertiseAddress the externally reachable address that is advertised to other nodes for API access
	 *                         (e.g., {@code 192.168.1.1:2377})
	 * @param managerAddresses the addresses of one or more existing swarm managers
	 * @param joinToken        a secret value needed to join the swarm
	 * @return the updated server
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	@CheckReturnValue
	public Server joinSwarmAsManager(String listenAddress, String dataPathAddress, String advertiseAddress,
		List<String> managerAddresses, String joinToken)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(dataPathAddress, "dataPathAddress").isStripped().isNotEmpty();
		requireThat(advertiseAddress, "advertiseAddress").isStripped().isNotEmpty();
		requireThat(listenAddress, "listenAddress").isStripped().isNotEmpty();

		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Swarm/operation/SwarmJoin
		ObjectNode requestBody = client.getJsonMapper().createObjectNode();
		requestBody.put("AdvertiseAddr", advertiseAddress);
		requestBody.put("DataPathAddr", dataPathAddress);
		return joinServer(listenAddress, managerAddresses, joinToken, requestBody);
	}

	/**
	 * Joins an existing swarm as a worker.
	 *
	 * @param listenAddress    the address that the current node will use to listen for inter-manager
	 *                         communication (e.g., {@code 0.0.0.0:2377})
	 * @param managerAddresses the addresses of one or more existing swarm managers
	 * @param joinToken        a secret value needed to join the swarm
	 * @return the updated server
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public Server joinSwarmAsWorker(String listenAddress, List<String> managerAddresses, String joinToken)
		throws IOException, TimeoutException, InterruptedException
	{
		JsonMapper jm = client.getJsonMapper();
		ObjectNode requestBody = jm.createObjectNode();
		return joinServer(listenAddress, managerAddresses, joinToken, requestBody);
	}

	/**
	 * Joins an existing swarm.
	 *
	 * @param listenAddress    the address that the current node will use to listen for inter-manager
	 *                         communication (e.g., {@code 0.0.0.0:2377})
	 * @param managerAddresses the addresses of one or more existing swarm managers
	 * @param joinToken        a secret value needed to join the swarm
	 * @param requestBody      the body of the client request
	 * @return the updated server
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	@CheckReturnValue
	private Server joinServer(String listenAddress, List<String> managerAddresses, String joinToken,
		ObjectNode requestBody) throws IOException, TimeoutException, InterruptedException
	{
		requireThat(listenAddress, "listenAddress").isStripped().isNotEmpty();
		requireThat(managerAddresses, "managerAddresses").isNotNull();
		requireThat(joinToken, "joinToken").isStripped().isNotEmpty();

		requestBody.put("ListenAddr", listenAddress);
		ArrayNode remoteAddrs = requestBody.putArray("RemoteAddrs");
		for (String address : managerAddresses)
			remoteAddrs.add(address);
		requestBody.put("JoinToken", joinToken);

		URI uri = client.getServer().resolve("swarm/join");
		Request request = client.createRequest(uri, requestBody).
			method(POST);
		ContentResponse serverResponse = client.send(request);
		return switch (serverResponse.getStatus())
		{
			case OK_200 -> reload();
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		};
	}

	/**
	 * Leaves the swarm.
	 *
	 * @return the updated server
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public Server leaveSwarm() throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Swarm/operation/SwarmLeave
		@SuppressWarnings("PMD.CloseResource")
		URI uri = client.getServer().resolve("swarm/leave");
		Request request = client.createRequest(uri).
			method(POST);
		ContentResponse serverResponse = client.send(request);
		return switch (serverResponse.getStatus())
		{
			case OK_200 -> reload();
			// The server is not a member of a swarm
			case SERVICE_UNAVAILABLE_503 -> this;
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		};
	}

	/**
	 * Reloads the server's state.
	 *
	 * @return the updated server
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	@CheckReturnValue
	private Server reload() throws IOException, TimeoutException, InterruptedException
	{
		return get(client);
	}
}