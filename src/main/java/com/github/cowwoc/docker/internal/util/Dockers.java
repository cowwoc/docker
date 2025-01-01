package com.github.cowwoc.docker.internal.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.docker.client.DockerClient;
import com.github.cowwoc.pouch.core.WrappedCheckedException;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.StringRequestContent;

import static com.github.cowwoc.requirements10.jackson.DefaultJacksonValidators.requireThat;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static org.eclipse.jetty.http.HttpStatus.OK_200;

/**
 * Docker helper functions.
 */
public final class Dockers
{
	/**
	 * Returns a resource's version number.
	 *
	 * @param json the resource's state
	 * @return the version number
	 * @throws IllegalArgumentException if the node value is not an integer or does not fit into an {@code int}
	 */
	public static int getVersion(JsonNode json)
	{
		JsonNode versionNode = json.get("Version");
		return toInt(versionNode.get("Index"), "Version.Index");
	}

	/**
	 * Returns the {@code int} value of a JSON node.
	 *
	 * @param node a JSON node
	 * @param name the name of the node
	 * @return the {@code int} value
	 * @throws IllegalArgumentException if the node value is not an integer or does not fit into an {@code int}
	 */
	public static int toInt(JsonNode node, String name)
	{
		requireThat(node, name).isIntegralNumber();
		requireThat(node.canConvertToInt(), name + ".canConvertToInt()").isTrue();
		return node.intValue();
	}

	/**
	 * Ensures that the server returned HTTP 200 ("OK"); otherwise, throws an exception.
	 *
	 * @param client         the client configuration
	 * @param request        the client request
	 * @param serverResponse the server response
	 * @throws AssertionError if the server response is not HTTP 200
	 */
	public static void expectOk200(DockerClient client, Request request, ContentResponse serverResponse)
	{
		switch (serverResponse.getStatus())
		{
			case OK_200 ->
			{
				// success
			}
			default ->
			{
				ClientRequests clientRequests = client.getClientRequests();
				throw new AssertionError(
					"Unexpected response: " + clientRequests.toString(serverResponse) + "\n" +
						"Request: " + clientRequests.toString(request));
			}
		}
	}

	/**
	 * Returns the JSON representation of the server response.
	 *
	 * @param client         the client configuration
	 * @param serverResponse the server response
	 * @return the JSON representation of the response
	 * @throws WrappedCheckedException if the server response could not be parsed
	 */
	public static JsonNode getResponseBody(DockerClient client, ContentResponse serverResponse)
	{
		try
		{
			return client.getObjectMapper().readTree(serverResponse.getContentAsString());
		}
		catch (JsonProcessingException e)
		{
			throw WrappedCheckedException.wrap(e);
		}
	}

	/**
	 * Creates a request.
	 *
	 * @param client      the client configuration
	 * @param uri         the URI the send a request to
	 * @param requestBody the request body
	 * @return the request
	 */
	public static Request createRequest(DockerClient client, String uri, JsonNode requestBody)
	{
		String requestBodyAsString;
		try
		{
			requestBodyAsString = client.getObjectMapper().writeValueAsString(requestBody);
		}
		catch (JsonProcessingException e)
		{
			throw WrappedCheckedException.wrap(e);
		}
		return client.getHttpClient().newRequest(uri).
			transport(client.getTransport()).
			body(new StringRequestContent("application/json", requestBodyAsString));
	}

	private Dockers()
	{
	}
}