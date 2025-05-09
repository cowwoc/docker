package com.github.cowwoc.docker.internal.buildkit;

import com.google.protobuf.ByteString;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * BuildKit helper functions.
 */
public final class BuildKit
{
	private static final HexFormat hexFormat = HexFormat.of();

	/**
	 * Returns the digest of a value.
	 *
	 * @param value the serialized representation of a value
	 * @return the digest
	 * @throws NullPointerException if {@code value} is null
	 */
	public static String getDigest(ByteString value)
	{
		try
		{
			MessageDigest generator = MessageDigest.getInstance("SHA-256");
			byte[] digest = generator.digest(value.toByteArray());
			return "sha256:" + hexFormat.formatHex(digest);
		}
		catch (NoSuchAlgorithmException e)
		{
			// Deployment-time decision
			throw new AssertionError(e);
		}
	}

	private BuildKit()
	{
	}
}
