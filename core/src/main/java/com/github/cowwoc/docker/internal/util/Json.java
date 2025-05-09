package com.github.cowwoc.docker.internal.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * JSON helper functions.
 */
public final class Json
{
	private Json()
	{
	}

	/**
	 * Warns on unexpected properties.
	 *
	 * @param log           the logger to send warnings to
	 * @param json          the JSON node
	 * @param expectedNames the names of the expected properties
	 * @throws NullPointerException if any of the arguments are null
	 */
	public static void warnOnUnexpectedProperties(Logger log, JsonNode json, String... expectedNames)
	{
		Set<String> propertyNames = new HashSet<>();
		json.fieldNames().forEachRemaining(propertyNames::add);
		for (String name : expectedNames)
			propertyNames.remove(name);
		if (!propertyNames.isEmpty())
		{
			log.warn("""
				Unexpected properties: {}.
				JSON: {}""", propertyNames, json.toPrettyString());
		}
	}
}
