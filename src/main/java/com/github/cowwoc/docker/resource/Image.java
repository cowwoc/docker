package com.github.cowwoc.docker.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.docker.client.DockerClient;
import com.github.cowwoc.docker.exception.ImageNotFoundException;
import com.github.cowwoc.docker.internal.util.ToStringBuilder;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

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
	 * Returns all images.
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
	public static List<Image> getAll(DockerClient client)
		throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Image/operation/ImageList
		URI uri = client.getServer().resolve("images/json");
		Request request = client.createRequest(uri).
			param("digests", "true").
			method(GET);

		ContentResponse serverResponse = client.send(request);
		switch (serverResponse.getStatus())
		{
			case OK_200 ->
			{
				// success
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
		JsonNode body = client.getResponseBody(serverResponse);
		List<Image> images = new ArrayList<>();
		for (JsonNode node : body)
			images.add(getByJson(client, node));
		return images;
	}

	/**
	 * Looks up an image by its name or ID.
	 *
	 * @param client the client configuration
	 * @param id     an identifier of the image. Local images may be identified by their name, digest or ID.
	 *               Remote images may be identified by their name or ID. If a name is specified, it may include
	 *               a tag or a digest.
	 * @return the image
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code id} contains leading or trailing whitespace or is empty
	 * @throws IllegalStateException    if the client is closed
	 * @throws ImageNotFoundException   if the image was not found
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
		requireThat(id, "id").isStripped().isNotEmpty();
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Image/operation/ImageInspect
		String encodedId = id.replace("/", "%2F");
		URI uri = client.getServer().resolve("images/" + encodedId + "/json");
		Request request = client.createRequest(uri).
			method(GET);

		ContentResponse serverResponse = client.send(request);
		switch (serverResponse.getStatus())
		{
			case OK_200 ->
			{
				// success
			}
			case NOT_FOUND_404 ->
			{
				JsonNode json = client.getObjectMapper().readTree(serverResponse.getContentAsString());
				throw new ImageNotFoundException(json.get("message").textValue());
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
		JsonNode body = client.getResponseBody(serverResponse);
		return getByJson(client, body);
	}

	/**
	 * Returns the first image that matches a predicate.
	 *
	 * @param client    the client configuration
	 * @param predicate the predicate
	 * @return null if no match is found
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public static Image getByPredicate(DockerClient client, Predicate<Image> predicate)
		throws IOException, InterruptedException, TimeoutException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Image/operation/ImageList
		URI uri = client.getServer().resolve("images/json");
		Request request = client.createRequest(uri).
			param("digests", "true").
			method(GET);

		ContentResponse serverResponse = client.send(request);
		switch (serverResponse.getStatus())
		{
			case OK_200 ->
			{
				// success
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
		JsonNode body = client.getResponseBody(serverResponse);
		for (JsonNode node : body)
		{
			Image image = getByJson(client, node);
			if (predicate.test(image))
				return image;
		}
		return null;
	}

	/**
	 * Pulls an image from a remote repository.
	 *
	 * @param client the client configuration
	 * @param id     an identifier of the image. Local images may be identified by their name, digest or ID.
	 *               Remote images may be identified by their name or ID. If a name is specified, it may include
	 *               a tag or a digest.
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code id} contains leading or trailing whitespace or is empty
	 */
	public static ImagePuller puller(DockerClient client, String id)
	{
		return new ImagePuller(client, id);
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
	 * @param client the client configuration
	 * @param json   the JSON representation of the node
	 * @return the image
	 * @throws NullPointerException if any of the arguments are null
	 */
	private static Image getByJson(DockerClient client, JsonNode json)
	{
		String id = json.get("Id").textValue();
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
		assert that(client, "client").isNotNull().elseThrow();
		assert that(id, "id").isStripped().isNotEmpty().elseThrow();
		assert that(nameToDigest, "nameToDigest").isNotNull().elseThrow();

		for (Entry<String, String> entry : nameToDigest.entrySet())
		{
			assert that(entry.getKey(), "name").withContext(nameToDigest, "nameToDigest").isStripped().
				isNotEmpty().elseThrow();
			assert that(entry.getValue(), "tag").withContext(nameToDigest, "nameToDigest").isStripped().
				isNotEmpty().elseThrow();
		}
		assert that(nameToTag, "nameToTags").isNotNull().elseThrow();
		for (Entry<String, String> entry : nameToTag.entrySet())
		{
			assert that(entry.getKey(), "name").withContext(nameToTag, "nameToTag").isStripped().
				isNotEmpty().elseThrow();
			assert that(entry.getValue(), "tag").withContext(nameToTag, "nameToTag").isStripped().
				isNotEmpty().elseThrow();
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
		String encodedId = id.replace("/", "%2F");
		URI uri = client.getServer().resolve("images/" + encodedId + "/tag");
		Request request = client.createRequest(uri).
			param("repo", repository).
			param("tag", tag).
			method(POST);

		ContentResponse serverResponse = client.send(request);
		switch (serverResponse.getStatus())
		{
			case CREATED_201 ->
			{
			}
			case NOT_FOUND_404 ->
			{
				JsonNode json = client.getObjectMapper().readTree(serverResponse.getContentAsString());
				throw new ImageNotFoundException(json.get("message").textValue());
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
	}

	/**
	 * Pushes an image to a remote repository.
	 *
	 * @param client the client configuration
	 * @param name   the name of the image to push. For example, {@code docker.io/nasa/rocket-ship}
	 * @param tag    the tag to push
	 * @return the push configuration
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 */
	public ImagePusher pusher(DockerClient client, String name, String tag)
	{
		return new ImagePusher(client, this, name, tag);
	}

	/**
	 * Instantiate the image as a container.
	 *
	 * @return the container configuration
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 */
	public ContainerCreator createContainer()
	{
		return new ContainerCreator(client, id);
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