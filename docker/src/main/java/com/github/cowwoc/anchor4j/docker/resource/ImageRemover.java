package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.core.internal.client.CommandResult;
import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.anchor4j.docker.exception.ResourceInUseException;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * Removes an image.
 */
public final class ImageRemover
{
	private final InternalDocker client;
	private final String id;
	private boolean force;
	private boolean doNotPruneParents;

	/**
	 * Creates a container remover.
	 *
	 * @param client the client configuration
	 * @param id     the image's ID or {@link Image reference}
	 * @throws NullPointerException     if {@code id} is null
	 * @throws IllegalArgumentException if {@code reference}:
	 *                                  <ul>
	 *                                    <li>is empty.</li>
	 *                                    <li>contains any character other than lowercase letters (a–z),
	 *                                    digits (0–9), and the following characters: {@code '.'}, {@code '/'},
	 *                                    {@code ':'}, {@code '_'}, {@code '-'}, {@code '@'}.</li>
	 *                                  </ul>
	 */
	ImageRemover(InternalDocker client, String id)
	{
		assert that(client, "client").isNotNull().elseThrow();
		client.validateImageReference(id, "id");
		this.client = client;
		this.id = id;
	}

	/**
	 * Indicates that the image should be removed even if it is tagged in multiple repositories.
	 *
	 * @return this
	 */
	public ImageRemover force()
	{
		this.force = true;
		return this;
	}

	/**
	 * Prevents automatic removal of untagged parent images when this image is removed.
	 *
	 * @return this
	 */
	public ImageRemover doNotPruneParents()
	{
		this.doNotPruneParents = true;
		return this;
	}

	/**
	 * Removes the image. If the image does not exist, this method has no effect.
	 *
	 * @throws ResourceInUseException if the image is tagged in multiple repositories or in use by containers
	 *                                and {@link #force()} was not used
	 * @throws IOException            if an I/O error occurs. These errors are typically transient, and retrying
	 *                                the request may resolve the issue.
	 * @throws InterruptedException   if the thread is interrupted before the operation completes. This can
	 *                                happen due to shutdown signals.
	 */
	public void remove() throws IOException, InterruptedException
	{
		// https://docs.docker.com/reference/cli/docker/container/rm/
		List<String> arguments = new ArrayList<>(5);
		arguments.add("image");
		arguments.add("rm");
		if (force)
			arguments.add("--force");
		if (doNotPruneParents)
			arguments.add("--no-prune");
		arguments.add(id);
		CommandResult result = client.run(arguments);
		client.getImageParser().remove(result);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(ImageRemover.class).
			add("force", force).
			add("doNotPruneParents", doNotPruneParents).
			toString();
	}
}