package com.github.cowwoc.docker.internal.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.cowwoc.pouch.core.WrappedCheckedException;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * The internals of a {@code DockerClient}.
 */
public interface InternalClient
{
	/**
	 * Defines the frequency at which it is acceptable to log the same message to indicate that the thread is
	 * still active. This helps in monitoring the progress and ensuring the thread has not become unresponsive.
	 */
	Duration PROGRESS_FREQUENCY = Duration.ofSeconds(2);

	/**
	 * Returns the JSON configuration.
	 *
	 * @return the JSON configuration
	 * @throws IllegalStateException if the client is closed
	 */
	ObjectMapper getObjectMapper();

	/**
	 * Returns the URI of the REST API server.
	 *
	 * @return the URI of the REST API server
	 * @throws IllegalStateException if the client is closed
	 */
	URI getServer();

	/**
	 * Creates a request without a body.
	 *
	 * @param uri the URI the send a request to
	 * @return the request
	 * @throws NullPointerException  if {@code uri} is null
	 * @throws IllegalStateException if the client is closed
	 */
	Request createRequest(URI uri);

	/**
	 * Creates a request with a body.
	 *
	 * @param uri         the URI the send a request to
	 * @param requestBody the request body
	 * @return the request
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 */
	Request createRequest(URI uri, JsonNode requestBody);

	/**
	 * Sends a request.
	 *
	 * @param request the client request
	 * @return the server response
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws TimeoutException     if the request times out before receiving a response. This might indicate
	 *                              network latency or server overload.
	 * @throws InterruptedException if the thread is interrupted while waiting for a response. This can happen
	 *                              due to shutdown signals.
	 */
	ContentResponse send(Request request) throws IOException, TimeoutException, InterruptedException;

	/**
	 * Sends a request with an asynchronous response listener.
	 *
	 * @param request  the client request
	 * @param listener the server response listener
	 * @throws IOException if an I/O error occurs. These errors are typically transient, and retrying the
	 *                     request may resolve the issue.
	 */
	void send(Request request, Response.Listener listener) throws IOException;

	/**
	 * Returns the JSON representation of the server response.
	 *
	 * @param serverResponse the server response
	 * @return the JSON representation of the response
	 * @throws IllegalStateException   if the client is closed
	 * @throws WrappedCheckedException if the server response could not be parsed
	 */
	JsonNode getResponseBody(ContentResponse serverResponse);

	/**
	 * Returns the String representation of the request.
	 *
	 * @param request a client request
	 * @return the String representation
	 */
	String toString(Request request);

	/**
	 * Returns the String representation of the response.
	 *
	 * @param response the server response
	 * @return the String representation
	 */
	String toString(Response response);

	/**
	 * Returns a resource's version number.
	 *
	 * @param json the resource's state
	 * @return the version number
	 * @throws IllegalArgumentException if the node value is not an integer or does not fit into an {@code int}
	 * @throws IllegalStateException    if the client is closed
	 */
	int getVersion(JsonNode json);

	/**
	 * Returns the {@code int} value of a JSON node.
	 *
	 * @param node a JSON node
	 * @param name the name of the node
	 * @return the {@code int} value
	 * @throws IllegalArgumentException if the node value is not an integer or does not fit into an {@code int}
	 * @throws IllegalStateException    if the client is closed
	 */
	int toInt(JsonNode node, String name);

	/**
	 * Returns the {@code long} value of a JSON node.
	 *
	 * @param node a JSON node
	 * @param name the name of the node
	 * @return the {@code long} value
	 * @throws IllegalArgumentException if the node value is not an integer
	 * @throws IllegalStateException    if the client is closed
	 */
	long toLong(JsonNode node, String name);

	/**
	 * Ensures that the server returned HTTP 200 ("OK"); otherwise, throws an exception.
	 *
	 * @param request        the client request
	 * @param serverResponse the server response
	 * @throws AssertionError        if the server response is not HTTP 200
	 * @throws IllegalStateException if the client is closed
	 */
	void expectOk200(Request request, ContentResponse serverResponse);

	/**
	 * Returns the {@code List<String>} representation of a JSON array of strings.
	 *
	 * @param array a JSON array
	 * @param name  the name of the node
	 * @return an empty list if the array is empty
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if the array's value is not a String
	 * @throws IllegalStateException    if the client is closed
	 */
	List<String> arrayToListOfString(JsonNode array, String name);

	/**
	 * Removes a registry from an image name.
	 *
	 * @param id an identifier of the image. Local images may be identified by their name, digest or ID. Remote
	 *           images may be identified by their name or ID. If a name is specified, it may include a tag or a
	 *           digest.
	 * @return the id without a registry
	 */
	String removeRegistry(String id);
}