package com.github.cowwoc.anchor4j.core.internal.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.anchor4j.core.internal.client.InternalClient;

import java.util.regex.Pattern;

/**
 * Common functionality shared by all parsers.
 */
public abstract class AbstractParser
{
	/**
	 * The network connection has been unexpectedly terminated.
	 */
	public static final Pattern CONNECTION_RESET = Pattern.compile("error during connect: " +
		"[^ ]+ \"[^\"]+\": EOF");
	/**
	 * Splits Strings on a {@code \n}.
	 */
	protected static final Pattern SPLIT_LINES = Pattern.compile("\n");
	/**
	 * Splits Strings on a {@code :}.
	 */
	protected static final Pattern SPLIT_ON_COLON = Pattern.compile(":");
	/**
	 * Splits Strings on a {@code @}.
	 */
	protected static final Pattern SPLIT_ON_AT_SIGN = Pattern.compile("@");
	/**
	 * Splits Strings on a {@code /}.
	 */
	protected static final Pattern SPLIT_ON_SLASH = Pattern.compile("/");
	/**
	 * The client configuration.
	 */
	protected final InternalClient client;

	/**
	 * Creates a parser.
	 *
	 * @param client the client configuration
	 */
	public AbstractParser(InternalClient client)
	{
		assert client != null;
		this.client = client;
	}

	/**
	 * Returns the value of a {@code boolean} child node.
	 *
	 * @param parent the parent node
	 * @param name   the name of the child node
	 * @return {@code false} if the child node was not found
	 */
	public boolean getBoolean(JsonNode parent, String name)
	{
		JsonNode childNode = parent.get(name);
		return childNode != null && childNode.booleanValue();
	}
}