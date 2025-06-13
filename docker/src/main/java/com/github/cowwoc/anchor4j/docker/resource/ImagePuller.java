package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.anchor4j.docker.client.Docker;
import com.github.cowwoc.anchor4j.docker.exception.ResourceNotFoundException;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Pulls an image from a registry.
 */
public final class ImagePuller
{
	private final InternalDocker client;
	private final String reference;
	private String platform = "";

	/**
	 * Creates an image puller.
	 *
	 * @param client    the client configuration
	 * @param reference the image's reference
	 * @throws NullPointerException     if {@code reference} is null
	 * @throws IllegalArgumentException if {@code reference}:
	 *                                  <ul>
	 *                                    <li>is empty.</li>
	 *                                    <li>contains any character other than lowercase letters (a–z),
	 *                                    digits (0–9), and the following characters: {@code '.'}, {@code '/'},
	 *                                    {@code ':'}, {@code '_'}, {@code '-'}, {@code '@'}.</li>
	 *                                  </ul>
	 */
	ImagePuller(InternalDocker client, String reference)
	{
		assert that(client, "client").isNotNull().elseThrow();
		client.validateImageReference(reference, "reference");
		this.client = client;
		this.reference = reference;
	}

	/**
	 * Sets the platform to pull.
	 *
	 * @param platform the platform of the image
	 * @return this
	 * @throws NullPointerException     if {@code platform} is null
	 * @throws IllegalArgumentException if {@code platform} contains whitespace or is empty
	 */
	public ImagePuller platform(String platform)
	{
		requireThat(platform, "platform").doesNotContainWhitespace().isNotEmpty();
		this.platform = platform;
		return this;
	}

	/**
	 * Pulls the image from a registry.
	 *
	 * @return the ID of the pulled image
	 * @throws ResourceNotFoundException if the image does not exist or may require {@code docker login}
	 * @throws IOException               if an I/O error occurs. These errors are typically transient, and
	 *                                   retrying the request may resolve the issue.
	 * @throws InterruptedException      if the thread is interrupted before the operation completes. This can
	 *                                   happen due to shutdown signals.
	 * @see Docker#login(String, String, String)
	 */
	public String pull() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/image/pull/
		List<String> arguments = new ArrayList<>(5);
		arguments.add("image");
		arguments.add("pull");
		if (!platform.isEmpty())
		{
			arguments.add("platform");
			arguments.add(platform);
		}
		arguments.add(reference);
		CommandResult result = client.run(arguments);
		return client.getImageParser().pull(result, reference);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(ImagePuller.class).
			add("platform", platform).
			toString();
	}
}