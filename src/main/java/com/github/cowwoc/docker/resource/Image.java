package com.github.cowwoc.docker.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.docker.client.DockerClient;
import com.github.cowwoc.docker.exception.ImageNotFoundException;
import com.github.cowwoc.docker.internal.util.ClientRequests;
import com.github.cowwoc.docker.internal.util.Dockers;
import com.github.cowwoc.docker.internal.util.ToStringBuilder;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.OK_200;

/**
 * A docker image.
 */
public final class Image
{
	/**
	 * Returns a list of all images.
	 *
	 * @param client the client configuration
	 * @return the images
	 * @throws NullPointerException  if {@code client} is null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static List<Image> list(DockerClient client)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Image/operation/ImageList
		@SuppressWarnings("PMD.CloseResource")
		HttpClient httpClient = client.getHttpClient();
		ClientRequests clientRequests = client.getClientRequests();
		String uri = client.getUri() + "/images/json";
		Request request = httpClient.newRequest(uri).
			param("digests", "true").
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
				JsonNode json = client.getObjectMapper().readTree(serverResponse.getContentAsString());
				throw new FileNotFoundException(json.get("message").textValue());
			}
			default -> throw new AssertionError("Unexpected response: " + clientRequests.toString(serverResponse) +
				"\n" +
				"Request: " + clientRequests.toString(request));
		}
		JsonNode body = Dockers.getResponseBody(client, serverResponse);
		List<Image> images = new ArrayList<>();
		for (JsonNode node : body)
			images.add(getByJson(client, node));
		return images;
	}

	/**
	 * Looks up an image by its ID.
	 *
	 * @param client the client configuration
	 * @param id     the ID
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
	public static Image getById(DockerClient client, String id)
		throws IOException, TimeoutException, InterruptedException
	{
		return getByIdOrName(client, id);
	}

	/**
	 * Looks up an image by its name.
	 *
	 * @param client the client configuration
	 * @param name   the name
	 * @return null if no match is found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code name} contains leading or trailing whitespace, or is empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public static Image getByName(DockerClient client, String name)
		throws IOException, TimeoutException, InterruptedException
	{
		return getByIdOrName(client, name);
	}

	/**
	 * Looks up an image by its name or ID.
	 *
	 * @param client   the client configuration
	 * @param nameOrId the name or ID
	 * @return null if no match is found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code nameOrId} contains leading or trailing whitespace, or is
	 *                                  empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	private static Image getByIdOrName(DockerClient client, String nameOrId)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(nameOrId, "id").isStripped().isNotEmpty();
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Image/operation/ImageInspect
		@SuppressWarnings("PMD.CloseResource")
		HttpClient httpClient = client.getHttpClient();
		ClientRequests clientRequests = client.getClientRequests();
		String uri = client.getUri() + "/images/" + nameOrId + "/json";
		Request request = httpClient.newRequest(uri).
			param("digests", "true").
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
				JsonNode json = client.getObjectMapper().readTree(serverResponse.getContentAsString());
				throw new FileNotFoundException(json.get("message").textValue());
			}
			default -> throw new AssertionError("Unexpected response: " + clientRequests.toString(serverResponse) +
				"\n" +
				"Request: " + clientRequests.toString(request));
		}
		JsonNode body = Dockers.getResponseBody(client, serverResponse);
		for (JsonNode node : body)
		{
			Image image = getByJson(client, node);
			if (image.getNameToDigest().containsValue(nameOrId))
				return image;
		}
		return null;
	}

	/**
	 * Looks up an image by its digest.
	 *
	 * @param client the client configuration
	 * @param digest the digest
	 * @return null if no match is found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code digest} contains leading or trailing whitespace, or is empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public static Image getByDigest(DockerClient client, String digest)
		throws IOException, TimeoutException, InterruptedException
	{
		requireThat(digest, "digest").isStripped().isNotEmpty();
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Image/operation/ImageList
		@SuppressWarnings("PMD.CloseResource")
		HttpClient httpClient = client.getHttpClient();
		ClientRequests clientRequests = client.getClientRequests();
		String uri = client.getUri() + "/images/json";
		Request request = httpClient.newRequest(uri).
			param("digests", "true").
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
				JsonNode json = client.getObjectMapper().readTree(serverResponse.getContentAsString());
				throw new FileNotFoundException(json.get("message").textValue());
			}
			default -> throw new AssertionError("Unexpected response: " + clientRequests.toString(serverResponse) +
				"\n" +
				"Request: " + clientRequests.toString(request));
		}
		JsonNode body = Dockers.getResponseBody(client, serverResponse);
		for (JsonNode node : body)
		{
			Image image = getByJson(client, node);
			if (image.getNameToDigest().containsValue(digest))
				return image;
		}
		return null;
	}

	/**
	 * Builds an image.
	 *
	 * @param client the client configuration
	 * @return a new image builder
	 * @throws NullPointerException if {@code client} is null
	 */
	public static ImageBuilder builder(DockerClient client)
	{
		return new ImageBuilder(client);
	}

	/**
	 * Pushes an image to a remote repository.
	 *
	 * @param client the client configuration
	 * @param name   the name of the image to push. For example, {@code docker.io/nasa/rocket-ship}
	 * @param tag    the tag to push
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 */
	public static ImagePusher pusher(DockerClient client, String name, String tag)
	{
		return new ImagePusher(client, name, tag);
	}

	/**
	 * @param client the client configuration
	 * @param json   the JSON representation of the node
	 * @return the image
	 * @throws NullPointerException if any of the arguments are null
	 */
	private static Image getByJson(DockerClient client, JsonNode json)
	{
		String id = json.get("id").textValue();
		Map<String, String> nameToTag = new LinkedHashMap<>();
		for (JsonNode nameAndTag : json.get("RepoTags"))
		{
			String[] tokens = nameAndTag.textValue().split(":");
			assert that(tokens, "tokens").length().isEqualTo(2).elseThrow();

			if (!tokens[0].equals("<none>") && !tokens[1].equals("<none>"))
				nameToTag.put(tokens[0], tokens[1]);
		}
		Map<String, String> nameToDigest = new LinkedHashMap<>();
		for (JsonNode nameAndDigest : json.get("RepoDigests"))
		{
			String[] tokens = nameAndDigest.textValue().split("@");
			assert that(tokens, "tokens").length().isEqualTo(2).elseThrow();

			if (!tokens[0].equals("<none>") && !tokens[1].equals("<none>"))
				nameToDigest.put(tokens[0], tokens[1]);
		}
		return new Image(client, id, nameToTag, nameToDigest);
	}

	private final DockerClient client;
	private final String id;
	private final Map<String, String> nameToDigest;
	private final Map<String, String> nameToTag;

	/**
	 * Creates a new reference to a docker image.
	 *
	 * @param client       the client configuration
	 * @param id           the ID of the image
	 * @param nameToDigest a mapping from each image's name to its digest
	 * @param nameToTag    a mapping from each image's name to its tag
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code id}, the keys, or values of {@code nameToDigest} or
	 *                                  {@code nameToTags} contain leading or trailing whitespace, or are empty
	 */
	public Image(DockerClient client, String id, Map<String, String> nameToDigest,
		Map<String, String> nameToTag)
	{
		requireThat(id, "id").isStripped().isNotNull();
		requireThat(client, "client").isNotNull();
		requireThat(nameToDigest, "nameToDigest").isNotNull();
		for (Entry<String, String> entry : nameToDigest.entrySet())
		{
			requireThat(entry.getKey(), "name").withContext(nameToDigest, "nameToDigest").isStripped().
				isNotEmpty();
			requireThat(entry.getValue(), "tag").withContext(nameToDigest, "nameToDigest").isStripped().
				isNotEmpty();
		}
		requireThat(nameToTag, "nameToTags").isNotNull();
		for (Entry<String, String> entry : nameToTag.entrySet())
		{
			requireThat(entry.getKey(), "name").withContext(nameToTag, "nameToTag").isStripped().
				isNotEmpty();
			requireThat(entry.getValue(), "tag").withContext(nameToTag, "nameToTag").isStripped().
				isNotEmpty();
		}
		this.id = id;
		this.client = client;
		this.nameToDigest = Map.copyOf(nameToDigest);
		this.nameToTag = Map.copyOf(nameToTag);
	}

	/**
	 * Returns the ID of the image.
	 *
	 * @return the ID
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * Returns a mapping from the image's name to its digest.
	 *
	 * @return an empty map if the image was not pushed to any repositories
	 */
	public Map<String, String> getNameToDigest()
	{
		return nameToDigest;
	}

	/**
	 * Returns a mapping from the image's name to its tag.
	 *
	 * @return an empty map if the image was not pushed to any repositories
	 */
	public Map<String, String> getNameToTag()
	{
		return nameToTag;
	}

	/**
	 * Tags the image.
	 *
	 * @param repository the repository to tag in (e.g. {@code docker.io/nasa/rocket-ship})
	 * @param tag        the name of the tag (e.g., {@code latest})
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws ImageNotFoundException   if the image does not exist
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws TimeoutException         if the request times out before receiving a response. This might
	 *                                  indicate network latency or server overload.
	 * @throws InterruptedException     if the thread is interrupted while waiting for a response. This can
	 *                                  happen due to shutdown signals.
	 */
	public void tag(String repository, String tag)
		throws ImageNotFoundException, IOException, TimeoutException, InterruptedException
	{
		requireThat(repository, "repository").isStripped().isNotEmpty();
		requireThat(tag, "tag").isStripped().isNotEmpty();

		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Image/operation/ImageTag
		@SuppressWarnings("PMD.CloseResource")
		HttpClient httpClient = client.getHttpClient();
		ClientRequests clientRequests = client.getClientRequests();
		String uri = client.getUri() + "/images/" + id + "/tag";
		Request request = httpClient.newRequest(uri).
			param("repo", repository).
			param("tag", tag).
			transport(client.getTransport()).
			method(POST);
		ContentResponse serverResponse = clientRequests.send(request);
		switch (serverResponse.getStatus())
		{
			case CREATED_201 ->
			{
			}
			case NOT_FOUND_404 ->
			{
				JsonNode json = client.getObjectMapper().readTree(serverResponse.getContentAsString());
				throw new FileNotFoundException(json.get("message").textValue());
			}
			default -> throw new AssertionError("Unexpected response: " + clientRequests.toString(serverResponse) +
				"\n" +
				"Request: " + clientRequests.toString(request));
		}
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Image other && other.id.equals(id);
	}

	@Override
	public int hashCode()
	{
		return id.hashCode();
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(Image.class).
			add("id", id).
			add("nameToTag", nameToTag).
			add("nameToDigest", nameToDigest).
			toString();
	}
}