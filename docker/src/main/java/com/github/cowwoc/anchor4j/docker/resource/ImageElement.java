package com.github.cowwoc.anchor4j.docker.resource;

import com.github.cowwoc.anchor4j.docker.client.Docker;

import java.util.Map;
import java.util.Set;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.that;

/**
 * An element returned by {@link Docker#listImages()}.
 *
 * @param id                the image's ID
 * @param referenceToTags   a mapping from the image's reference to its tags
 * @param referenceToDigest a mapping from the image's reference to its digest
 */
public record ImageElement(String id, Map<String, Set<String>> referenceToTags,
                           Map<String, String> referenceToDigest)
{
	/**
	 * Creates an image element.
	 *
	 * @param id                the image's ID
	 * @param referenceToTags   a mapping from the image's reference to its tags
	 * @param referenceToDigest a mapping from the image's reference to its digest
	 */
	public ImageElement(String id, Map<String, Set<String>> referenceToTags,
		Map<String, String> referenceToDigest)
	{
		assert that(id, "id").doesNotContainWhitespace().isNotEmpty().elseThrow();
		assert Image.validateReferenceParameters(referenceToTags, referenceToDigest);
		this.id = id;
		this.referenceToTags = Map.copyOf(referenceToTags);
		this.referenceToDigest = Map.copyOf(referenceToDigest);
	}
}