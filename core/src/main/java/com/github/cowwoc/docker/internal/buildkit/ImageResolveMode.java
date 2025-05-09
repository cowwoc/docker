package com.github.cowwoc.docker.internal.buildkit;

import java.util.Locale;

/**
 * Determines how image references are resolved.
 * <p>
 * Specifically, whether BuildKit should attempt to resolve image tags to digests and whether it should access
 * a remote registry or rely on local cache.
 * <p>
 * Based on <a
 * href="https://github.com/moby/buildkit/blob/f198c3849aebe9b1b1ddd7286e831834014355ec/util/resolver/pool.go#L248">BuildKit
 * source-code</a>.
 */
public enum ImageResolveMode
{
	/**
	 * Tags are resolved using the registry, unless locally cached.
	 */
	DEFAULT,
	/**
	 * Images are only pulled if not already available in the local cache. Useful for CI where repeatable builds
	 * are needed, but registry access should be minimized.
	 */
	PULL,
	/**
	 * Disables remote resolution. Only uses the local image cache. Tags are not resolved to digests. This mode
	 * is fully offline.
	 */
	LOCAL;

	/**
	 * Returns the GRPC representation of this value.
	 *
	 * @return the GRPC representation
	 */
	public String toGrpc()
	{
		// https://github.com/moby/buildkit/blob/f198c3849aebe9b1b1ddd7286e831834014355ec/solver/pb/attr.go#L29
		return name().toLowerCase(Locale.ROOT);
	}
}