package com.github.cowwoc.docker.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.docker.internal.client.InternalClient;
import com.github.cowwoc.docker.internal.util.ToStringBuilder;
import com.github.cowwoc.docker.resource.Node.State;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;
import static org.eclipse.jetty.http.HttpMethod.DELETE;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.http.HttpStatus.SERVICE_UNAVAILABLE_503;

/**
 * A docker swarm (cluster).
 */
public class Swarm
{
	/**
	 * @param client the client configuration
	 * @param json   the JSON representation of the node
	 * @return the node
	 * @throws NullPointerException if any of the arguments are null
	 */
	static Swarm getByJson(InternalClient client, JsonNode json)
	{
		String id = json.get("ID").textValue();
		int version = client.getVersion(json);
		JsonNode joinTokens = json.get("JoinTokens");
		String managerToken = joinTokens.get("Manager").textValue();
		String workerToken = joinTokens.get("Worker").textValue();
		return new Swarm(client, id, version, managerToken, workerToken);
	}

	private final InternalClient client;
	private final String id;
	private final int version;
	private final String managerToken;
	private final String workerToken;

	/**
	 * Creates a new swarm.
	 *
	 * @param client       the client configuration
	 * @param id           the node ID of the server
	 * @param version      the version number of the node. This is used to avoid conflicting writes.
	 * @param managerToken a secret value needed to join the swarm as a manager
	 * @param workerToken  a secret value needed to join the swarm as a worker
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 */
	Swarm(InternalClient client, String id, int version, String managerToken, String workerToken)
	{
		assert that(client, "client").isNotNull().elseThrow();
		assert that(id, "id").isStripped().isNotEmpty().elseThrow();
		assert that(managerToken, "managerToken").isStripped().isNotEmpty().elseThrow();
		assert that(workerToken, "workerToken").isStripped().isNotEmpty().elseThrow();
		this.client = client;
		this.id = id;
		this.version = version;
		this.managerToken = managerToken;
		this.workerToken = workerToken;
	}

	/**
	 * Returns the ID of the swarm.
	 *
	 * @return the ID of the swarm
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * Returns the manager nodes in the swarm.
	 *
	 * @return the manager nodes in the swarm
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	public List<Node> getManagers() throws IOException, TimeoutException, InterruptedException
	{
		return getNodes("{\"role\":[\"manager\"]}");
	}

	/**
	 * Returns the worker nodes in the swarm.
	 *
	 * @return the worker nodes in the swarm
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	public List<Node> getWorkers() throws IOException, TimeoutException, InterruptedException
	{
		return getNodes("{\"role\":[\"worker\"]}");
	}

	/**
	 * Returns the nodes in the swarm.
	 *
	 * @return the nodes in the swarm
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	public List<Node> getNodes() throws IOException, TimeoutException, InterruptedException
	{
		return getNodes("");
	}

	/**
	 * Returns the nodes in the swarm.
	 *
	 * @param filters the filters to apply to the list, or an empty string if absent
	 * @return the nodes in the swarm
	 * @throws NullPointerException  if {@code filters} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	private List<Node> getNodes(String filters) throws IOException, TimeoutException, InterruptedException
	{
		assert that(filters, "filters").isStripped().elseThrow();

		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Node/operation/NodeList
		URI uri = client.getServer().resolve("nodes");
		Request request = client.createRequest(uri);
		if (!filters.isEmpty())
			request.param("filters", filters);
		request.method(GET);

		ContentResponse serverResponse = client.send(request);
		client.expectOk200(request, serverResponse);
		JsonNode body = client.getResponseBody(serverResponse);
		List<Node> nodes = new ArrayList<>();
		for (JsonNode node : body)
			nodes.add(Node.getByJson(client, node));
		return nodes;
	}

	/**
	 * Returns the secret value needed to join the swarm as a manager.
	 *
	 * @return the secret value needed to join the swarm as a manager
	 */
	public String getManagerToken()
	{
		return managerToken;
	}

	/**
	 * Returns the secret value needed to join the swarm as a worker.
	 *
	 * @return the secret value needed to join the swarm as a worker
	 */
	public String getWorkerToken()
	{
		return workerToken;
	}

	/**
	 * Looks up a node by its ID.
	 *
	 * @param id the ID of a node
	 * @return {@code null} if no match is found
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code id} contains leading or trailing whitespace or is empty
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public Node getNodeById(String id) throws IOException, TimeoutException, InterruptedException
	{
		return Node.getById(client, id);
	}

	/**
	 * Looks up a node by its name.
	 *
	 * @param name the name of a node
	 * @return {@code null} if no match is found
	 * @throws NullPointerException     if {@code name} is null
	 * @throws IllegalArgumentException if {@code name} contains leading or trailing whitespace or is empty
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public Node getNodeByName(String name) throws IOException, TimeoutException, InterruptedException
	{
		return Node.getByName(client, name);
	}

	/**
	 * Returns the manager nodes with a READY status.
	 *
	 * @return the manager nodes with a READY status
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	public List<Node> getReadyManagers() throws IOException, TimeoutException, InterruptedException
	{
		List<Node> managers = new ArrayList<>();
		for (Node manager : getManagers())
		{
			if (manager.getStatus().state() == State.READY)
				managers.add(manager);
		}
		return managers;
	}

	/**
	 * Returns the worker nodes with a READY status.
	 *
	 * @return the worker nodes with a READY status
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	public List<Node> getReadyWorkers() throws IOException, TimeoutException, InterruptedException
	{
		List<Node> workers = new ArrayList<>();
		for (Node worker : getWorkers())
		{
			if (worker.getStatus().state() == State.READY)
				workers.add(worker);
		}
		return workers;
	}

	/**
	 * Removes a node from the swarm.
	 *
	 * @param node the node
	 * @throws NullPointerException  if {@code node} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public void removeNode(Node node) throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Node/operation/NodeDelete
		URI uri = client.getServer().resolve("nodes/" + node.getId());
		Request request = client.createRequest(uri).
			method(DELETE);

		ContentResponse serverResponse = client.send(request);
		switch (serverResponse.getStatus())
		{
			case OK_200, NOT_FOUND_404, SERVICE_UNAVAILABLE_503 ->
			{
				// success, node not found, or the node is not part of the swarm
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(Swarm.class).
			add("id", id).
			add("version", version).
			add("managerToken", managerToken).
			add("workerToken", workerToken).
			toString();
	}
}