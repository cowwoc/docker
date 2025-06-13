package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.docker.client.Docker;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * An element returned by {@link Docker#listContainers()}.
 *
 * @param id   the container's ID
 * @param name the container's name
 */
public record ContainerElement(String id, String name)
{
	/**
	 * Creates a container element.
	 *
	 * @param id   the container's ID
	 * @param name the container's name
	 */
	public ContainerElement
	{
		assert that(id, "id").doesNotContainWhitespace().isNotEmpty().elseThrow();
		assert that(name, "name").doesNotContainWhitespace().isNotEmpty().elseThrow();
	}
}