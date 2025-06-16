package com.github.cowwoc.anchor4j.core.client;

import com.github.cowwoc.anchor4j.core.resource.Builder;
import com.github.cowwoc.anchor4j.core.resource.BuilderCreator;
import com.github.cowwoc.anchor4j.core.resource.ImageBuilder;
import com.github.cowwoc.requirements11.annotation.CheckReturnValue;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * Code common to all clients.
 */
public interface Client
{
	/**
	 * Looks up the default builder.
	 *
	 * @return the builder, or {@code null} if no match is found
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted before the operation completes. This can happen
	 *                              due to shutdown signals.
	 */
	Builder getBuilder() throws IOException, InterruptedException;

	/**
	 * Looks up a builder by its name.
	 *
	 * @param name the name of the builder
	 * @return the builder, or {@code null} if no match is found
	 * @throws NullPointerException     if {@code name} is null
	 * @throws IllegalArgumentException if {@code name} contains whitespace or is empty
	 * @throws IOException              if an I/O error occurs. These errors are typically transient, and
	 *                                  retrying the request may resolve the issue.
	 * @throws InterruptedException     if the thread is interrupted before the operation completes. This can
	 *                                  happen due to shutdown signals.
	 */
	Builder getBuilder(String name) throws IOException, InterruptedException;

	/**
	 * Creates a builder.
	 *
	 * @return a builder creator
	 */
	@CheckReturnValue
	BuilderCreator createBuilder();

	/**
	 * Blocks until the default builder is reachable and has a {@code RUNNING} state.
	 *
	 * @param deadline the time that the operation must complete by
	 * @return the builder
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted before the operation completes. This can happen
	 *                              due to shutdown signals.
	 * @throws TimeoutException     if a timeout occurs before the operation completes
	 */
	Builder waitUntilBuilderIsReady(Instant deadline)
		throws IOException, InterruptedException, TimeoutException;

	/**
	 * Returns the platforms that images can be built for.
	 *
	 * @return the platforms
	 * @throws IOException          if an I/O error occurs. These errors are typically transient, and retrying
	 *                              the request may resolve the issue.
	 * @throws InterruptedException if the thread is interrupted before the operation completes. This can happen
	 *                              due to shutdown signals.
	 */
	Set<String> getSupportedBuildPlatforms() throws IOException, InterruptedException;

	/**
	 * Builds an image.
	 *
	 * @return an image builder
	 */
	@CheckReturnValue
	ImageBuilder buildImage();
}