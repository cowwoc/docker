package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.core.internal.util.ToStringBuilder;
import com.github.cowwoc.anchor4j.docker.exception.ResourceNotFoundException;
import com.github.cowwoc.anchor4j.docker.internal.client.InternalDocker;
import com.github.cowwoc.anchor4j.docker.internal.resource.ImageAccess;
import com.github.cowwoc.anchor4j.docker.internal.resource.SharedSecrets;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * A docker image.
 *
 * <h2>Terminology</h2>
 * An image's <b>reference</b> is an identifier having the format
 * {@code [HOST[:PORT]/][NAMESPACE/]REPOSITORY[:TAG|@DIGEST]}.
 * <p>
 * Where:
 * <ul>
 *   <li>{@code HOST} refers to the registry host where the image resides. If omitted, the image is
 *   assumed to reside on Docker Hub ({@code docker.io})</li>
 *   <li>{@code PORT} refers to the registry port number. If omitted, the default port number (443 for
 *   HTTPS, 80 for HTTP) is used.</li>
 *   <li>{@code NAMESPACE} typically refers to the user or organization that published the image. If omitted,
 *   it defaults to {@code library}, reserved for Docker Official Images.</li>
 *   <li>{@code REPOSITORY} is the name that groups related images, typically representing a specific
 *   application, service, or component within the given namespace in the registry.</li>
 *   <li>{@code TAG} is the version or variant of the image.</li>
 *   <li>{@code DIGEST} is a SHA256 hash of the image's content that uniquely and immutably identifies it.
 *   The digest ensures content integrity - even if tags change or move, the digest always points to the
 *   exact image.</li>
 *   <li>An image reference includes either a tag or a digest, but not both. If both the tag and digest are
 *   omitted, a default tag of {@code latest} is used.</li>
 * </ul>
 * <p>
 * An image's <b>ID</b> is an automatically assigned identifier that is unique per host and may be equal to the image digest.
 */
public final class Image
{
	static
	{
		SharedSecrets.setImageAccess(new ImageAccess()
		{
			@Override
			public Image get(InternalDocker client, String id, Map<String, Set<String>> referenceToTags,
				Map<String, String> referenceToDigest)
			{
				return new Image(client, id, referenceToTags, referenceToDigest);
			}

			@Override
			public ImagePuller pull(InternalDocker client, String reference)
			{
				return new ImagePuller(client, reference);
			}

			@Override
			public ImagePusher push(InternalDocker client, String reference)
			{
				return new ImagePusher(client, reference);
			}

			@Override
			public ImageRemover remove(InternalDocker client, String reference)
			{
				return new ImageRemover(client, reference);
			}
		});
	}

	private final InternalDocker client;
	private final String id;
	private final Map<String, Set<String>> referenceToTags;
	private final Map<String, String> referenceToDigest;

	/**
	 * Creates a reference to an image.
	 *
	 * @param client            the client configuration
	 * @param id                the image's ID
	 * @param referenceToTags   a mapping from the image's name to its tags
	 * @param referenceToDigest a mapping from the image's name to its digest
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if:
	 *                                  <ul>
	 *                                    <li>any of the arguments, including map keys and values, contain
	 *                                    whitespace.</li>
	 *                                    <li>{@code id} or any of the map keys and values are empty.</li>
	 *                                  </ul>
	 */
	private Image(InternalDocker client, String id, Map<String, Set<String>> referenceToTags,
		Map<String, String> referenceToDigest)
	{
		assert that(client, "client").isNotNull().elseThrow();
		assert that(id, "id").doesNotContainWhitespace().isNotEmpty().elseThrow();
		assert validateReferenceParameters(referenceToTags, referenceToDigest);
		this.id = id;
		this.client = client;

		// Create immutable copies of the tags
		Map<String, Set<String>> nameToImmutableTags = new HashMap<>();
		for (Entry<String, Set<String>> entry : referenceToTags.entrySet())
			nameToImmutableTags.put(entry.getKey(), Set.copyOf(entry.getValue()));
		// Create an immutable copy of the outer map
		this.referenceToTags = Map.copyOf(nameToImmutableTags);
		this.referenceToDigest = Map.copyOf(referenceToDigest);
	}

	/**
	 * Validates an image's reference-related parameters.
	 *
	 * @param referenceToTags   a mapping from the image's reference to its tags
	 * @param referenceToDigest a mapping from the image's reference to its digest
	 * @return true
	 * @throws AssertionError if any of the repositories, tags or digests contain whitespace or are empty
	 */
	static boolean validateReferenceParameters(Map<String, Set<String>> referenceToTags,
		Map<String, String> referenceToDigest)
	{
		for (Entry<String, Set<String>> entry : referenceToTags.entrySet())
		{
			assert that(entry.getKey(), "reference").withContext(referenceToTags, "referenceToTags").
				doesNotContainWhitespace().isNotEmpty().elseThrow();
			for (String tag : entry.getValue())
			{
				assert that(tag, "tag").withContext(referenceToTags, "referenceToTags").doesNotContainWhitespace().
					isNotEmpty().elseThrow();
			}
		}
		for (Entry<String, String> entry : referenceToDigest.entrySet())
		{
			assert that(entry.getKey(), "reference").withContext(referenceToTags, "referenceToDigest").
				doesNotContainWhitespace().isNotEmpty().elseThrow();
			assert that(entry.getValue(), "digest").withContext(referenceToDigest, "referenceToDigest").
				doesNotContainWhitespace().isNotEmpty().elseThrow();
		}
		return true;
	}

	/**
	 * Returns the image's ID.
	 *
	 * @return the ID
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * Returns the image's references.
	 *
	 * @return the references
	 */
	public Set<String> getReferences()
	{
		return referenceToTags.keySet();
	}

	/**
	 * Returns a mapping of an image's name to its associated tags.
	 * <p>
	 * Locally, an image might have a name such as {@code nasa/rocket-ship} with tags {@code {"1.0", "latest"}},
	 * all referring to the same revision. In a registry, the same image could have a fully qualified name like
	 * {@code docker.io/nasa/rocket-ship} and be associated with multiple tags, such as
	 * {@code {"1.0", "2.0", "latest"}}, all referring to the same revision.
	 *
	 * @return an empty map if the image has no tags
	 */
	public Map<String, Set<String>> getReferenceToTags()
	{
		return referenceToTags;
	}

	/**
	 * Returns a mapping of an image's name on registries to its associated digest.
	 * <p>
	 * For example, an image might have a name such as {@code docker.io/nasa/rocket-ship} with digest
	 * {@code "sha256:afcc7f1ac1b49db317a7196c902e61c6c3c4607d63599ee1a82d702d249a0ccb"}.
	 *
	 * @return an empty map if the image has not been pushed to any repositories
	 */
	public Map<String, String> getReferenceToDigest()
	{
		return referenceToDigest;
	}

	/**
	 * Creates a new reference to this image.
	 * <p>
	 * If the target reference already exists, this method has no effect.
	 *
	 * @param target the new reference
	 * @throws NullPointerException      if {@code target} is null
	 * @throws IllegalArgumentException  if {@code target}'s format is invalid
	 * @throws ResourceNotFoundException if the image no longer exists
	 * @throws IOException               if an I/O error occurs. These errors are typically transient, and
	 *                                   retrying the request may resolve the issue.
	 * @throws InterruptedException      if the thread is interrupted before the operation completes. This can
	 *                                   happen due to shutdown signals.
	 */
	public void tag(String target)
		throws IOException, InterruptedException
	{
		client.tagImage(id, target);
	}

	/**
	 * Removes the image.
	 *
	 * @return an image remover
	 */
	public ImageRemover remover()
	{
		return client.removeImage(id);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id, referenceToTags, referenceToDigest);
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Image other && other.id.equals(id) &&
			other.referenceToTags.equals(referenceToTags) && other.referenceToDigest.equals(referenceToDigest);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(Image.class).
			add("id", id).
			add("referenceToTag", referenceToTags).
			add("referenceToDigest", referenceToDigest).
			toString();
	}
}