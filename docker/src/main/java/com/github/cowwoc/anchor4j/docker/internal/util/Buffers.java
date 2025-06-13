package com.github.cowwoc.anchor4j.docker.internal.util;

import java.nio.ByteBuffer;

/**
 * Helper functions for nio buffers.
 */
public final class Buffers
{
	/**
	 * Returns a read-only copy of a {@code ByteBuffer}.
	 * <p>
	 * If {@code source} is already read-only, it will be returned without making a copy.
	 *
	 * @param source the buffer to copy
	 * @return a read-only copy of {@code source}
	 */
	public static ByteBuffer copyOf(ByteBuffer source)
	{
		if (source.isReadOnly())
			return source.asReadOnlyBuffer();
		return ByteBuffer.allocate(source.remaining()).
			put(source.duplicate()).
			flip().
			asReadOnlyBuffer();
	}

	private Buffers()
	{
	}
}