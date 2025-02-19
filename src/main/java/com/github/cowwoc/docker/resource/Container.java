package com.github.cowwoc.docker.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.docker.client.DockerClient;
import com.github.cowwoc.docker.exception.ContainerAlreadyStartedException;
import com.github.cowwoc.docker.exception.ResourceNotFoundException;
import com.github.cowwoc.docker.internal.client.InternalClient;
import com.github.cowwoc.docker.internal.util.ToStringBuilder;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.NOT_MODIFIED_304;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.eclipse.jetty.http.HttpStatus.OK_200;

/**
 * A docker container, a running instance of an image.
 */
public final class Container
{
	/**
	 * Looks up a container by its name or ID.
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
	public static Container getById(DockerClient client, String id)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(id, "id").isStripped().isNotEmpty();

		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Container/operation/ContainerInspect
		InternalClient ic = (InternalClient) client;
		URI uri = ic.getServer().resolve("containers/" + id + "/json");
		Request request = ic.createRequest(uri).
			method(GET);

		ContentResponse serverResponse = ic.send(request);
		return switch (serverResponse.getStatus())
		{
			case OK_200 ->
			{
				JsonNode body = ic.getResponseBody(serverResponse);
				yield getByJson(ic, body);
			}
			case NOT_FOUND_404 -> null;
			default -> throw new AssertionError("Unexpected response: " + ic.toString(serverResponse) + "\n" +
				"Request: " + ic.toString(request));
		};
	}

	/**
	 * @param client the client configuration
	 * @param json   the JSON representation of the node
	 * @return the container
	 * @throws NullPointerException if any of the arguments are null
	 */
	private static Container getByJson(InternalClient client, JsonNode json)
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Container/operation/ContainerInspect
		String id = json.get("Id").textValue();
		String name = json.get("Name").textValue();
		return new Container(client, id, name);
	}

	private final InternalClient client;
	private final String id;
	private final String name;

	/**
	 * Creates a new container.
	 *
	 * @param client the client configuration
	 * @param id     the ID of the container
	 * @param name   the name of the container
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or is
	 *                                  empty
	 */
	private Container(InternalClient client, String id, String name)
	{
		assert that(client, "client").isNotNull().elseThrow();
		assert that(id, "id").isStripped().isNotEmpty().elseThrow();
		assert that(name, "name").isStripped().isNotEmpty().elseThrow();
		this.client = client;
		this.id = id;
		this.name = name;
	}

	/**
	 * Returns the ID of the container.
	 *
	 * @return the ID
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * Returns the name of the container.
	 *
	 * @return the name
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Starts the container.
	 *
	 * @throws IOException                      if an I/O error occurs. These errors are typically transient,
	 *                                          and retrying the request may resolve the issue.
	 * @throws TimeoutException                 if the request times out before receiving a response. This might
	 *                                          indicate network latency or server overload.
	 * @throws InterruptedException             if the thread is interrupted while waiting for a response. This
	 *                                          can happen due to shutdown signals.
	 * @throws ResourceNotFoundException        if the container no longer exists
	 * @throws ContainerAlreadyStartedException if the container is already started
	 */
	public void start()
		throws IOException, TimeoutException, InterruptedException, ContainerAlreadyStartedException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Container/operation/ContainerStart
		URI uri = client.getServer().resolve("containers/" + id + "/start");
		Request request = client.createRequest(uri).
			method(POST);

		ContentResponse serverResponse = client.send(request);
		switch (serverResponse.getStatus())
		{
			case NO_CONTENT_204 ->
			{
				// success
			}
			case NOT_MODIFIED_304 ->
			{
				throw new ContainerAlreadyStartedException(this);
			}
			case NOT_FOUND_404 ->
			{
				JsonNode json = client.getJsonMapper().readTree(serverResponse.getContentAsString());
				throw new ResourceNotFoundException(json.get("message").textValue());
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
	}

	/**
	 * Stops a container.
	 *
	 * @param signal  the signal to send ot the container (usually {@code SIGTERM}, {@code SIGKILL},
	 *                {@code SIGHUP} or {@code SIGINT}). {@code docker stop} sends {@code SIGTERM}.
	 * @param timeout the maximum amount of time to wait before killing the container
	 * @throws NullPointerException      if any of the arguments are null
	 * @throws IllegalArgumentException  if {@code signal} contains leading or trailing whitespace or is empty
	 * @throws ResourceNotFoundException if the container no longer exists
	 */
	public void stop(String signal, Duration timeout)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(signal, "signal").isStripped().isNotEmpty();
		requireThat(timeout, "timeout").isNotNull();

		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Container/operation/ContainerStop
		URI uri = client.getServer().resolve("containers/" + id + "/stop");
		Request request = client.createRequest(uri).
			param("signal", signal).
			param("t", String.valueOf(timeout.toSeconds())).
			method(POST);

		ContentResponse serverResponse = client.send(request);
		switch (serverResponse.getStatus())
		{
			case NO_CONTENT_204 ->
			{
				// success
			}
			case NOT_FOUND_404 ->
			{
				JsonNode json = client.getJsonMapper().readTree(serverResponse.getContentAsString());
				throw new ResourceNotFoundException(json.get("message").textValue());
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
	}

	/**
	 * Blocks until the container stops.
	 *
	 * @param condition the type of stop condition to wait for
	 * @return the exit code returned by the container
	 * @throws ResourceNotFoundException if the container no longer exists
	 */
	public WaitForStopResult waitForStop(StopCondition condition)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Container/operation/ContainerWait
		URI uri = client.getServer().resolve("containers/" + id + "/wait");
		Request request = client.createRequest(uri).
			param("condition", condition.toJson()).
			method(POST);

		ContentResponse serverResponse = client.send(request);
		return switch (serverResponse.getStatus())
		{
			case OK_200 ->
			{
				// success
				JsonNode json = client.getJsonMapper().readTree(serverResponse.getContentAsString());
				long code = client.toLong(json.get("StatusCode"), "StatusCode");
				JsonNode errorNode = json.get("Error");
				String error;
				if (errorNode == null)
					error = "";
				else
					error = errorNode.get("Message").textValue();
				yield new WaitForStopResult(code, error);
			}
			case NOT_FOUND_404 ->
			{
				JsonNode json = client.getJsonMapper().readTree(serverResponse.getContentAsString());
				throw new ResourceNotFoundException(json.get("message").textValue());
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		};
	}

	/**
	 * Streams the container logs.
	 *
	 * @return stream configuration
	 */
	public ContainerLogs logs()
	{
		return new ContainerLogs(client, id);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder().
			add("id", id).
			add("name", name).
			toString();
	}

	/**
	 * The type of stop condition to wait for.
	 */
	public enum StopCondition
	{
		/**
		 * Wait until the container shuts down.
		 */
		NOT_RUNNING,
		/**
		 * Wait until the next time the container exits. If the container is running, this waits until it shuts
		 * down. If the container is already shut down, this waits for the container to start and exit again.
		 */
		NEXT_EXIT,
		/**
		 * Wait until the container stops and is removed.
		 */
		REMOVED;

		/**
		 * Returns the object's JSON representation.
		 *
		 * @return the JSON representation
		 */
		public String toJson()
		{
			return name().toLowerCase(Locale.ROOT).replace('_', '-');
		}
	}

	/**
	 * The result of the {@link #waitForStop(StopCondition)} operation.
	 *
	 * @param code  the container's exit code
	 * @param error explains why the wait operation failed, or an empty string on success
	 */
	public record WaitForStopResult(long code, String error)
	{
		/**
		 * @param code  the container's exit code
		 * @param error explains why the wait operation failed, or an empty string on success
		 * @throws NullPointerException if {@code error} is null
		 */
		public WaitForStopResult
		{
			requireThat(error, "error").isNotNull();
		}
	}
}