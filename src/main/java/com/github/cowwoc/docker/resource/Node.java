package com.github.cowwoc.docker.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.cowwoc.docker.client.DockerClient;
import com.github.cowwoc.docker.internal.util.ClientRequests;
import com.github.cowwoc.docker.internal.util.Dockers;
import com.github.cowwoc.docker.internal.util.ToStringBuilder;
import com.github.cowwoc.requirements10.annotation.CheckReturnValue;
import com.github.cowwoc.requirements10.java.validator.StringValidator;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpMethod.DELETE;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.http.HttpStatus.SERVICE_UNAVAILABLE_503;

/**
 * A docker node.
 */
public final class Node
{
	/**
	 * Looks up a node by its ID.
	 *
	 * @param client the client configuration
	 * @param id     the node's ID
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
	public static Node getById(DockerClient client, String id)
		throws IOException, TimeoutException, InterruptedException
	{
		return getByIdOrName(client, id);
	}

	/**
	 * Looks up a node by its name.
	 *
	 * @param client the client configuration
	 * @param name   the node's name
	 * @return null if no match is found
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
	public static Node getByName(DockerClient client, String name)
		throws IOException, TimeoutException, InterruptedException
	{
		return getByIdOrName(client, name);
	}

	/**
	 * Looks up a node by its ID or name.
	 *
	 * @param client   the client configuration
	 * @param idOrName the node's ID or name
	 * @return null if no match is found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code idOrName} contains leading or trailing whitespace or is empty
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
	private static Node getByIdOrName(DockerClient client, String idOrName)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Node/operation/NodeInspect
		String uri = client.getUri() + "/nodes/" + idOrName;
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
	 * @param client the client configuration
	 * @param json   the JSON representation of the node
	 * @return the node
	 * @throws NullPointerException if any of the arguments are null
	 */
	static Node getByJson(DockerClient client, JsonNode json)
	{
		String id = json.get("ID").textValue();
		int version = Dockers.getVersion(json);
		JsonNode spec = json.get("Spec");
		Availability availability = Availability.valueOf(spec.get("Availability").textValue().
			toUpperCase(Locale.ROOT));
		JsonNode nameNode = spec.get("Name");
		String name;
		if (nameNode == null)
			name = "";
		else
			name = nameNode.textValue();
		SwarmRole role = SwarmRole.valueOf(spec.get("Role").textValue().toUpperCase(Locale.ROOT));
		JsonNode labelsNode = spec.get("Labels");
		List<String> labels = new ArrayList<>();
		for (JsonNode label : labelsNode)
		{
			String keyValue = label.textValue();
			int separator = keyValue.indexOf('=');
			if (separator == -1)
				throw new IllegalArgumentException("Labels must follow the format: key=value.\n" +
					"Actual: " + keyValue);
			String key = keyValue.substring(0, separator);
			requireThat(key, "key").matches("^[a-zA-Z0-9.-_]+$");
			labels.add(keyValue);
		}
		// Reminder: spec.labels are used to constrain task scheduling (e.g., zone=us-east, role=worker) while
		// description.engine.labels are informational (e.g., operation-system, version)

		JsonNode description = json.get("Description");
		String hostname = description.get("Hostname").textValue();

		JsonNode statusNode = json.get("Status");
		State state = State.valueOf(statusNode.get("State").textValue().toUpperCase(Locale.ROOT));
		JsonNode messageNode = statusNode.get("Message");
		String message;
		if (messageNode == null)
			message = "";
		else
			message = messageNode.textValue();
		String address = statusNode.get("Addr").textValue();
		Status status = new Status(state, message);

		JsonNode managerStatusNode = json.get("ManagerStatus");
		boolean swarmLeader;
		Reachability reachability;
		String managerAddress;
		if (managerStatusNode == null)
		{
			// Worker
			swarmLeader = false;
			reachability = Reachability.UNKNOWN;
			managerAddress = "";
		}
		else
		{
			swarmLeader = managerStatusNode.get("Leader").booleanValue();
			reachability = Reachability.valueOf(managerStatusNode.get("Reachability").textValue().
				toUpperCase(Locale.ROOT));
			managerAddress = managerStatusNode.get("Addr").textValue();
		}
		return new Node(client, id, version, name, hostname, role, swarmLeader, status, reachability,
			availability,
			managerAddress, address, labels);
	}

	private final DockerClient client;
	private final String id;
	private final int version;
	private final String name;
	private final SwarmRole role;
	private final boolean leader;
	private final Availability availability;
	private final Reachability reachability;
	private final Status status;
	private final String managerAddress;
	private final String address;
	private final String hostname;
	private final List<String> labels;

	/**
	 * Creates a new reference to a node.
	 *
	 * @param client         the client configuration
	 * @param id             the node's ID
	 * @param version        the version number of the node. This is used to avoid conflicting writes.
	 * @param name           (optional) the node's name, or an empty string if absent
	 * @param role           the role of the node within the swarm
	 * @param leader         {@code true} if the node is a swarm leader
	 * @param status         the overall health of the node
	 * @param reachability   indicates if the node is reachable ({@link Reachability#UNKNOWN UNKNOWN} for worker
	 *                       nodes)
	 * @param availability   indicates if the node is available to run tasks
	 * @param managerAddress (optional) the node's address for manager communication (empty string for worker
	 *                       nodes)
	 * @param address        the node's address
	 * @param hostname       the node's hostname
	 * @param labels         values that are used to constrain task scheduling to specific nodes
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain leading or trailing whitespace.</li>
	 *                                    <li>any of the mandatory arguments are empty.</li>
	 *                                  </ul>
	 */
	private Node(DockerClient client, String id, int version, String name, String hostname, SwarmRole role,
		boolean leader, Status status, Reachability reachability, Availability availability,
		String managerAddress, String address, List<String> labels)
	{
		requireThat(client, "client").isNotNull();
		requireThat(id, "id").isStripped().isNotEmpty();
		requireThat(name, "name").isStripped();
		requireThat(hostname, "hostname").isStripped().isNotEmpty();
		requireThat(role, "role").isNotNull();
		requireThat(status, "status").isNotNull();
		requireThat(reachability, "reachability").isNotNull();
		requireThat(availability, "availability").isNotNull();
		requireThat(address, "address").isStripped().isNotEmpty();
		StringValidator managerAddressValidator = requireThat(managerAddress, "managerAddress").isStripped();
		switch (role)
		{
			case MANAGER -> managerAddressValidator.isNotEmpty();
			case WORKER ->
			{
				// do nothing
			}
		}
		requireThat(labels, "labels").isNotNull();
		this.client = client;
		this.id = id;
		this.version = version;
		this.name = name;
		this.hostname = hostname;
		this.role = role;
		this.leader = leader;
		this.status = status;
		this.reachability = reachability;
		this.availability = availability;
		this.managerAddress = managerAddress;
		this.address = address;
		this.labels = List.copyOf(labels);
	}

	/**
	 * Returns the node's id.
	 *
	 * @return the node's id
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * Returns the node's name.
	 *
	 * @return the node's name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Returns the node's hostname.
	 *
	 * @return the hostname
	 */
	public String getHostname()
	{
		return hostname;
	}

	/**
	 * Returns the role of the node within the swarm.
	 *
	 * @return null if the node is not part of a swarm
	 */
	public SwarmRole getRole()
	{
		return role;
	}

	/**
	 * Indicates if the node is a swarm leader.
	 *
	 * @return {@code true} if the node is a swarm leader
	 */
	public boolean isLeader()
	{
		return leader;
	}

	/**
	 * Returns the overall health of the node.
	 *
	 * @return the overall health of the node
	 */
	public Status getStatus()
	{
		return status;
	}

	/**
	 * Indicates whether it is possible to communicate with the node.
	 *
	 * @return {@link Reachability#UNKNOWN UNKNOWN} for worker nodes
	 */
	public Reachability getReachability()
	{
		return reachability;
	}

	/**
	 * Indicates if the node is available to run tasks.
	 *
	 * @return {@code true} if the node is available to run tasks
	 */
	public Availability getAvailability()
	{
		return availability;
	}

	/**
	 * Returns the node's address for manager communication.
	 *
	 * @return an empty string for worker nodes
	 */
	public String getManagerAddress()
	{
		assert role != SwarmRole.MANAGER || !managerAddress.isBlank() :
			"Node is a manager but its address is blank";
		return managerAddress;
	}

	/**
	 * Returns the node's address.
	 *
	 * @return the node's address
	 */
	public String getAddress()
	{
		return address;
	}

	/**
	 * Returns values that are used to constrain task scheduling to specific nodes.
	 *
	 * @return values that are used to constrain task scheduling to specific nodes
	 */
	public List<String> getLabels()
	{
		return labels;
	}

	/**
	 * Reloads the node's state.
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
	public Node reload() throws IOException, TimeoutException, InterruptedException
	{
		return getById(client, id);
	}

	/**
	 * Begins gracefully removing tasks from this node and redistribute them to other active nodes.
	 *
	 * @return the updated node
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public Node drain() throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Node/operation/NodeUpdate
		ObjectMapper om = client.getObjectMapper();
		ObjectNode requestBody = om.createObjectNode().
			put("Availability", "drain");
		return update(requestBody);
	}

	/**
	 * Updates the node.
	 *
	 * @param requestBody the client request body
	 * @return the updated node
	 * @throws NullPointerException  if {@code requestBody} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	private Node update(JsonNode requestBody) throws IOException, TimeoutException, InterruptedException
	{
		ClientRequests clientRequests = client.getClientRequests();
		String uri = client.getUri() + "/nodes/" + id + "/update";
		Request request = Dockers.createRequest(client, uri, requestBody).
			method(POST).
			param("version", String.valueOf(version));
		ContentResponse serverResponse = clientRequests.send(request);
		return switch (serverResponse.getStatus())
		{
			case OK_200 -> reload();
			default -> throw new AssertionError("Unexpected response: " +
				clientRequests.toString(serverResponse) + "\n" +
				"Request: " + clientRequests.toString(request));
		};
	}

	/**
	 * Returns the tasks that are running on the node.
	 *
	 * @return the tasks that are running on the node.
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public List<Task> getTasks() throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Task/operation/TaskList
		@SuppressWarnings("PMD.CloseResource")
		HttpClient httpClient = client.getHttpClient();
		ClientRequests clientRequests = client.getClientRequests();
		String uri = client.getUri() + "/tasks";
		Request request = httpClient.newRequest(uri).
			transport(client.getTransport()).
			param("filters", "{node=" + id + "}").
			method(GET);
		ContentResponse serverResponse = clientRequests.send(request);
		Dockers.expectOk200(client, request, serverResponse);
		JsonNode body = Dockers.getResponseBody(client, serverResponse);
		List<Task> tasks = new ArrayList<>();
		for (JsonNode task : body)
			tasks.add(Task.getByJson(task));
		return tasks;
	}

	/**
	 * Changes the role of the node.
	 *
	 * @param role the new role
	 * @return the updated node
	 * @throws NullPointerException  if {@code role} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public Node setRole(SwarmRole role) throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Node/operation/NodeUpdate
		ObjectMapper om = client.getObjectMapper();
		ObjectNode requestBody = om.createObjectNode().
			put("Version", version).
			put("Role", role.name().toLowerCase(Locale.ROOT));
		return update(requestBody);
	}

	/**
	 * Destroys the node.
	 *
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public void destroy() throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Node/operation/NodeDelete
		@SuppressWarnings("PMD.CloseResource")
		HttpClient httpClient = client.getHttpClient();
		ClientRequests clientRequests = client.getClientRequests();
		String uri = client.getUri() + "/nodes/" + id;
		Request request = httpClient.newRequest(uri).
			transport(client.getTransport()).
			method(DELETE);
		ContentResponse serverResponse = clientRequests.send(request);
		switch (serverResponse.getStatus())
		{
			case OK_200, NOT_FOUND_404 ->
			{
				// success
			}
			default -> throw new AssertionError("Unexpected response: " +
				clientRequests.toString(serverResponse) + "\n" +
				"Request: " + clientRequests.toString(request));
		}
	}

	@Override
	public int hashCode()
	{
		return id.hashCode();
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Node other && other.id.equals(id);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(Node.class).
			add("id", id).
			add("name", name).
			add("role", role).
			add("leader", leader).
			add("availability", availability).
			add("reachability", reachability).
			add("status", status).
			add("managerAddress", managerAddress).
			add("workerAddress", address).
			add("hostname", hostname).
			add("labels", labels).
			toString();
	}

	/**
	 * Indicates if the node is available to run tasks.
	 */
	public enum Availability
	{
		// https://github.com/docker/engine-api/blob/4290f40c056686fcaa5c9caf02eac1dde9315adf/types/swarm/node.go#L34
		/**
		 * The node can accept new tasks.
		 */
		ACTIVE,
		/**
		 * The node is temporarily unavailable for new tasks, but existing tasks continue running.
		 */
		PAUSE,
		/**
		 * The node is unavailable for new tasks, and any existing tasks are being moved to other nodes in the
		 * swarm. This is typically used when preparing a node for maintenance.
		 */
		DRAIN
	}

	/**
	 * Indicates if it is possible to communicate with the node.
	 */
	public enum Reachability
	{
		// https://github.com/docker/engine-api/blob/4290f40c056686fcaa5c9caf02eac1dde9315adf/types/swarm/node.go#L79
		/**
		 * There is insufficient information to determine if the node is reachable.
		 */
		UNKNOWN,
		/**
		 * The node is unreachable.
		 */
		UNREACHABLE,
		/**
		 * The node is reachable.
		 */
		REACHABLE
	}

	/**
	 * The overall health of the node.
	 *
	 * @param state   the overall health of the node
	 * @param message a human-readable description of the state
	 */
	// WORKAROUND: https://github.com/checkstyle/checkstyle/issues/15683
	@SuppressWarnings("checkstyle:javadocmethod")
	public record Status(State state, String message)
	{
		/**
		 * @param state   the overall health of the node
		 * @param message a human-readable description of the state
		 * @throws NullPointerException     if any of the arguments are null
		 * @throws IllegalArgumentException if {@code message} contains leading or trailing whitespace
		 */
		public Status
		{
			requireThat(state, "state").isNotNull();
			requireThat(message, "message").isStripped();
		}
	}

	/**
	 * Indicates the overall health of the node.
	 */
	public enum State
	{
		// https://github.com/docker/engine-api/blob/4290f40c056686fcaa5c9caf02eac1dde9315adf/types/swarm/node.go#L98
		/**
		 * There is insufficient information to determine the status of the node.
		 */
		UNKNOWN,
		/**
		 * The node is permanently unable to run tasks.
		 */
		DOWN,
		/**
		 * The node is reachable and ready to run tasks.
		 */
		READY,
		/**
		 * The node is temporarily unreachable but may still be running tasks.
		 */
		DISCONNECTED
	}

	/**
	 * The role of the node within the swarm.
	 */
	public enum SwarmRole
	{
		/**
		 * A node that participates in administrating the swarm.
		 */
		MANAGER,
		/**
		 * A node that runs tasks.
		 */
		WORKER
	}
}