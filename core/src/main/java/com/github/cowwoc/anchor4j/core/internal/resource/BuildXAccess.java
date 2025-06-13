package com.github.cowwoc.anchor4j.core.internal.resource;

import com.github.cowwoc.anchor4j.core.internal.client.InternalClient;
import com.github.cowwoc.anchor4j.core.resource.Builder;
import com.github.cowwoc.anchor4j.core.resource.Builder.Status;
import com.github.cowwoc.anchor4j.core.resource.ImageBuilder;

/**
 * Methods that expose non-public behavior or data of BuildX builders.
 */
public interface BuildXAccess
{
	/**
	 * Looks up a builder by its name.
	 *
	 * @param client the client configuration
	 * @param name   the name
	 * @param status the status of the builder
	 * @param error  an explanation of the builder's error status, or an empty string if the status is not
	 *               {@code ERROR}
	 * @return null if no match was found
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code name} contains whitespace or is empty
	 */
	Builder get(InternalClient client, String name, Status status, String error);

	/**
	 * Builds an image.
	 *
	 * @param client       the client configuration
	 * @param errorHandler a callback that enables the listener to handle additional errors
	 * @return an image builder
	 */
	ImageBuilder buildImage(InternalClient client, ErrorHandler errorHandler);

	/**
	 * Looks up a value from its String representation.
	 *
	 * @param value the String representation
	 * @return the matching value
	 * @throws NullPointerException     if {@code json} is null
	 * @throws IllegalArgumentException if no match is found
	 */
	Status getStatusFromString(String value);
}