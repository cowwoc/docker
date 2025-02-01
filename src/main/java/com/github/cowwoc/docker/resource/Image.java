package com.github.cowwoc.docker.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.docker.client.DockerClient;
import com.github.cowwoc.docker.exception.ImageNotFoundException;
import com.github.cowwoc.docker.internal.client.InternalClient;
import com.github.cowwoc.docker.internal.util.ToStringBuilder;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
		InternalClient ic = (InternalClient) client;

		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Image/operation/ImageList
		URI uri = ic.getServer().resolve("images/json");
		Request request = ic.createRequest(uri).
			param("digests", "true").
			method(GET);

		ContentResponse serverResponse = ic.send(request);
		switch (serverResponse.getStatus())
		{
			case OK_200 ->
			{
				// success
			}
			default -> throw new AssertionError("Unexpected response: " + ic.toString(serverResponse) + "\n" +
				"Request: " + ic.toString(request));
		}
		JsonNode body = ic.getResponseBody(serverResponse);
		List<Image> images = new ArrayList<>();
		for (JsonNode node : body)
			images.add(getByJson(ic, node));
		return images;
	}

	/**
	 * Looks up an image by its name or ID.
	 *
	 * @param client the client configuration
	 * @param id     an identifier of the image. Local images may be identified by their name, digest or ID.
	 *               Remote images may be identified by their name or ID. If a name is specified, it may include
	 *               a tag or a digest.
	 * @return null if no match is found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code id} contains leading or trailing whitespace or is empty
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
		requireThat(id, "id").isStripped().isNotEmpty();
		InternalClient ic = (InternalClient) client;

		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Image/operation/ImageInspect
		String encodedId = id.replace("/", "%2F");
		URI uri = ic.getServer().resolve("images/" + encodedId + "/json");
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
		InternalClient ic = (InternalClient) client;

		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Image/operation/ImageList
		URI uri = ic.getServer().resolve("images/json");
		Request request = ic.createRequest(uri).
			param("digests", "true").
			method(GET);

		ContentResponse serverResponse = ic.send(request);
		switch (serverResponse.getStatus())
		{
			case OK_200 ->
			{
				// success
			}
			default -> throw new AssertionError("Unexpected response: " + ic.toString(serverResponse) + "\n" +
				"Request: " + ic.toString(request));
		}
		JsonNode body = ic.getResponseBody(serverResponse);
		for (JsonNode node : body)
		{
			Image image = getByJson(ic, node);
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
		return new ImagePuller((InternalClient) client, id);
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
		return new ImageBuilder((InternalClient) client);
	}

	/**
	 * @param client the client configuration
	 * @param json   the JSON representation of the node
	 * @return the image
	 * @throws NullPointerException if any of the arguments are null
	 */
	private static Image getByJson(InternalClient client, JsonNode json)
	{
		String id = json.get("Id").textValue();
		Map<String, Set<String>> nameToTags = new LinkedHashMap<>();
		for (JsonNode nameAndTag : json.get("RepoTags"))
		{
			// Format: registry:optionalPort/repository/image:tag
			String nameAndTagAsString = nameAndTag.textValue();
			int lastColon = nameAndTagAsString.lastIndexOf(':');
			assert that(lastColon, "lastColon").withContext(nameAndTagAsString, "nameAndTag").isNotEqualTo(-1).
				elseThrow();

			String name = nameAndTagAsString.substring(0, lastColon);
			String tag = nameAndTagAsString.substring(lastColon);

			nameToTags.computeIfAbsent(name, _ -> new HashSet<>()).add(tag);
		}
		Map<String, String> nameToDigest = new LinkedHashMap<>();
		for (JsonNode nameAndDigest : json.get("RepoDigests"))
		{
			String[] nameAndDigestAsString = nameAndDigest.textValue().split("@");
			assert that(nameAndDigestAsString, "nameAndDigestAsString").length().isEqualTo(2).elseThrow();

			String name = nameAndDigestAsString[0];
			String digest = nameAndDigestAsString[1];

			if (!name.equals("<none>") && !digest.equals("<none>"))
				nameToDigest.put(name, digest);
		}
		return new Image(client, id, nameToTags, nameToDigest);
	}

	private final InternalClient client;
	private final String id;
	private final Map<String, Set<String>> nameToTags;
	private final Map<String, String> nameToDigest;

	/**
	 * Creates a new reference to a docker image.
	 *
	 * @param client       the client configuration
	 * @param id           the ID of the image
	 * @param nameToTags   a mapping from each image's name to its tags
	 * @param nameToDigest a mapping from each image's name on remote repositories to its digest
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code id}, the keys, or values of {@code nameToTags} or
	 *                                  {@code nameToDigest} contain leading or trailing whitespace, or are
	 *                                  empty
	 */
	Image(InternalClient client, String id, Map<String, Set<String>> nameToTags,
		Map<String, String> nameToDigest)
	{
		assert that(client, "client").isNotNull().elseThrow();
		assert that(id, "id").isStripped().isNotEmpty().elseThrow();
		assert that(nameToTags, "nameToTags").isNotNull().elseThrow();
		for (Entry<String, Set<String>> entry : nameToTags.entrySet())
		{
			assert that(entry.getKey(), "name").withContext(nameToTags, "nameToTags").isStripped().
				isNotEmpty().elseThrow();
			for (String tag : entry.getValue())
			{
				assert that(tag, "tag").withContext(nameToTags, "nameToTags").isStripped().isNotEmpty().
					elseThrow();
			}
		}

		assert that(nameToDigest, "nameToDigests").isNotNull().elseThrow();
		for (Entry<String, String> entry : nameToDigest.entrySet())
		{
			assert that(entry.getKey(), "name").withContext(nameToDigest, "nameToDigests").isStripped().
				isNotEmpty().elseThrow();
			assert that(entry.getValue(), "digest").withContext(nameToDigest, "nameToDigests").isStripped().
				isNotEmpty().elseThrow();
		}
		this.id = id;
		this.client = client;

		// Create immutable copies of the tags
		Map<String, Set<String>> nameToImmutableTags = new HashMap<>();
		for (Entry<String, Set<String>> entry : nameToTags.entrySet())
			nameToImmutableTags.put(entry.getKey(), Set.copyOf(entry.getValue()));
		// Create an immutable copy of the outer map
		this.nameToTags = Map.copyOf(nameToImmutableTags);
		this.nameToDigest = Map.copyOf(nameToDigest);
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
	 * Returns a mapping of an image's name to its associated tags.
	 * <p>
	 * Locally, an image might have a name such as {@code nasa/rocket-ship} with tags {@code {"1.0", "latest"}},
	 * all referring to the same revision. In a remote repository, the same image could have a fully qualified
	 * name like {@code docker.io/nasa/rocket-ship} and be associated with multiple tags, such as
	 * {@code {"1.0", "2.0", "latest"}}, all referring to the same revision.
	 *
	 * @return an empty map if the image has no tags
	 */
	public Map<String, Set<String>> getNameToTags()
	{
		return nameToTags;
	}

	/**
	 * Returns a mapping of an image's name on remote registries to its associated digest.
	 * <p>
	 * For example, an image might have a name such as {@code docker.io/nasa/rocket-ship} with digest
	 * {@code "sha256:afcc7f1ac1b49db317a7196c902e61c6c3c4607d63599ee1a82d702d249a0ccb"}.
	 *
	 * @return an empty map if the image has not been pushed to any repositories
	 */
	public Map<String, String> getNameToDigest()
	{
		return nameToDigest;
	}

	/**
	 * Tags the image.
	 *
	 * @param name the name of the image (e.g. {@code nasa/rocket-ship} locally or
	 *             {@code docker.io/nasa/rocket-ship} on a remote repository)
	 * @param tag  the name of the tag (e.g., {@code latest})
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
	public void tag(String name, String tag)
		throws ImageNotFoundException, IOException, TimeoutException, InterruptedException
	{
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(tag, "tag").isStripped().isNotEmpty();

		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Image/operation/ImageTag
		String encodedId = id.replace("/", "%2F");
		URI uri = client.getServer().resolve("images/" + encodedId + "/tag");
		Request request = client.createRequest(uri).
			param("repo", name).
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
				JsonNode json = client.getJsonMapper().readTree(serverResponse.getContentAsString());
				throw new ImageNotFoundException(json.get("message").textValue());
			}
			default -> throw new AssertionError("Unexpected response: " + client.toString(serverResponse) + "\n" +
				"Request: " + client.toString(request));
		}
	}

	/**
	 * Pushes an image to a remote repository.
	 *
	 * @param name the name of the image to push. For example, {@code docker.io/nasa/rocket-ship}
	 * @param tag  the tag to push
	 * @return the push configuration
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 */
	public ImagePusher pusher(String name, String tag)
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
			add("nameToTag", nameToTags).
			add("nameToDigest", nameToDigest).
			toString();
	}
}