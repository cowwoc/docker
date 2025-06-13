package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.docker.client.Docker;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * An element returned by {@link Docker#listContexts()}.
 *
 * @param name        the name of the context
 * @param current     {@code true} if this is the current user context
 * @param description a description of the context
 * @param endpoint    the configuration of the target Docker Engine
 * @param error       an explanation of why the context is unavailable, or an empty string if the context is
 *                    available
 */
public record ContextElement(String name, boolean current, String description, String endpoint, String error)
{
	/**
	 * Creates a context element.
	 *
	 * @param name        the name of the context
	 * @param current     {@code true} if this is the current user context
	 * @param description a description of the context
	 * @param endpoint    the configuration of the target Docker Engine
	 * @param error       an explanation of why the context is unavailable, or an empty string if the context is
	 *                    available
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>{@code name}, {@code endpoint} or {@code error} contain
	 *                                    whitespace.</li>
	 *                                    <li>{@code description} contains leading or trailing whitespace.</li>
	 *                                    <li>{@code name} or {@code endpoint} are empty.</li>
	 *                                  </ul>
	 */
	public ContextElement
	{
		assert that(name, "name").doesNotContainWhitespace().isNotEmpty().elseThrow();
		assert that(description, "description").isStripped().elseThrow();
		assert that(endpoint, "endpoint").doesNotContainWhitespace().isNotEmpty().elseThrow();
		assert that(error, "error").doesNotContainWhitespace().elseThrow();
	}
}