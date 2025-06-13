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
 * Pushes an image to a registry.
 */
public final class ImagePusher
{
	private final InternalDocker client;
	private final String reference;
	private String platform = "";

	/**
	 * Creates an image pusher.
	 *
	 * @param reference the reference to push. For example, {@code docker.io/nasa/rocket-ship}. The image must
	 *                  be present in the local image store with the same name.
	 * @param client    the client configuration
	 * @throws NullPointerException     if {@code reference} is null
	 * @throws IllegalArgumentException if {@code reference}:
	 *                                  <ul>
	 *                                    <li>is empty.</li>
	 *                                    <li>contains any character other than lowercase letters (a–z),
	 *                                    digits (0–9), and the following characters: {@code '.'}, {@code '/'},
	 *                                    {@code ':'}, {@code '_'}, {@code '-'}, {@code '@'}.</li>
	 *                                  </ul>
	 */
	ImagePusher(InternalDocker client, String reference)
	{
		assert that(client, "client").isNotNull().elseThrow();
		client.validateImageReference(reference, "reference");
		this.client = client;
		this.reference = reference;
	}

	/**
	 * Sets the platform to push. By default, all platforms are pushed.
	 *
	 * @param platform the platform of the image, or an empty string to push all platforms
	 * @return this
	 * @throws NullPointerException     if {@code platform} is null
	 * @throws IllegalArgumentException if {@code platform} contains whitespace
	 */
	public ImagePusher platform(String platform)
	{
		requireThat(platform, "platform").doesNotContainWhitespace();
		this.platform = platform;
		return this;
	}

	/**
	 * Pushes the image to a registry.
	 *
	 * @throws ResourceNotFoundException if the referenced image could not be found
	 * @throws IOException               if an I/O error occurs. These errors are typically transient, and
	 *                                   retrying the request may resolve the issue.
	 * @throws InterruptedException      if the thread is interrupted before the operation completes. This can
	 *                                   happen due to shutdown signals.
	 * @see Docker#login(String, String, String)
	 */
	public void push() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/image/push/
		List<String> arguments = new ArrayList<>(5);
		arguments.add("image");
		arguments.add("push");
		if (!platform.isEmpty())
		{
			arguments.add("--platform");
			arguments.add(platform);
		}
		arguments.add(reference);
		CommandResult result = client.run(arguments);
		client.getImageParser().push(result);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(ImagePusher.class).
			add("platform", platform).
			toString();
	}
}