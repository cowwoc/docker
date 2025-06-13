package com.github.cowwoc.anchor4j.docker.internal.resource;

import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.resource.Context;
import com.github.cowwoc.anchor4j.docker.resource.ContextCreator;
import com.github.cowwoc.anchor4j.docker.resource.ContextEndpoint;
import com.github.cowwoc.anchor4j.docker.resource.ContextRemover;

import java.net.URI;

/**
 * Methods that expose non-public behavior or data of docker contexts.
 */
public interface ContextAccess
{
	/**
	 * Returns a reference to a context.
	 *
	 * @param client      the client configuration
	 * @param name        the context's name
	 * @param description the context's description
	 * @param endpoint    the configuration of the target Docker Engine
	 * @return a reference to a context
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments contain whitespace.</li>
	 *                                    <li>{@code name} or {@code endpoint} are empty.</li>
	 *                                  </ul>
	 */
	Context get(InternalDocker client, String name, String description, String endpoint);

	/**
	 * Returns a context creator.
	 *
	 * @param name     the name of the context
	 * @param client   the client configuration
	 * @param endpoint the configuration of the target Docker Engine
	 * @return a context creator
	 * @throws NullPointerException     if {@code name} or {@code endpoint} are null
	 * @throws IllegalArgumentException if {@code name} contains whitespace or is empty
	 * @see ContextEndpoint#builder(URI)
	 */
	ContextCreator create(InternalDocker client, String name, ContextEndpoint endpoint);

	/**
	 * Returns a context remover.
	 *
	 * @param client the client configuration
	 * @param name   the name of the context
	 * @return a context remover
	 * @throws NullPointerException     if {@code name} is null
	 * @throws IllegalArgumentException if {@code name} contains whitespace or is empty
	 */
	ContextRemover remove(InternalDocker client, String name);
}