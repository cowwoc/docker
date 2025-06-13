package com.github.cowwoc.anchor4j.docker.internal.resource;

import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.resource.Image;
import com.github.cowwoc.anchor4j.docker.resource.ImagePuller;
import com.github.cowwoc.anchor4j.docker.resource.ImagePusher;
import com.github.cowwoc.anchor4j.docker.resource.ImageRemover;

import java.util.Map;
import java.util.Set;

/**
 * Methods that expose non-public behavior or data of images.
 */
public interface ImageAccess
{
	/**
	 * Returns a reference to an image.
	 *
	 * @param client            the client configuration
	 * @param id                the image's ID
	 * @param referenceToTags   a mapping from the image's name to its tags
	 * @param referenceToDigest a mapping from the image's name to its digest
	 * @return an image
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments, including map keys and values, contain
	 *                                    whitespace.</li>
	 *                                    <li>{@code id} or any of the map keys and values are empty.</li>
	 *                                  </ul>
	 */
	Image get(InternalDocker client, String id, Map<String, Set<String>> referenceToTags,
		Map<String, String> referenceToDigest);

	/**
	 * Pulls an image from a registry.
	 *
	 * @param client    the client configuration
	 * @param reference the image's reference
	 * @return an image puller
	 * @throws NullPointerException     if {@code reference} is null
	 * @throws IllegalArgumentException if {@code reference}:
	 *                                  <ul>
	 *                                    <li>contains whitespace or is empty.</li>
	 *                                    <li>contains uppercase letters.</li>
	 *                                  </ul>
	 */
	ImagePuller pull(InternalDocker client, String reference);

	/**
	 * Returns an image pusher.
	 *
	 * @param client    the client configuration
	 * @param reference the image's reference
	 * @return an image pusher
	 * @throws NullPointerException     if {@code reference} is null
	 * @throws IllegalArgumentException if {@code reference}:
	 *                                  <ul>
	 *                                    <li>contains whitespace or is empty.</li>
	 *                                    <li>contains uppercase letters.</li>
	 *                                  </ul>
	 */
	ImagePusher push(InternalDocker client, String reference);

	/**
	 * Removes an image.
	 *
	 * @param client    the client configuration
	 * @param reference the image's reference
	 * @return an image remover
	 * @throws NullPointerException     if {@code reference} is null
	 * @throws IllegalArgumentException if {@code reference}:
	 *                                  <ul>
	 *                                    <li>contains whitespace or is empty.</li>
	 *                                    <li>contains uppercase letters.</li>
	 *                                  </ul>
	 */
	ImageRemover remove(InternalDocker client, String reference);
}