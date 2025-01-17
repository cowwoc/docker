package com.github.cowwoc.docker.internal.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.docker.internal.client.InternalClient;
import org.eclipse.jetty.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * Processes JSON objects returned by a server response.
 */
public abstract class AsyncResponseListener implements Response.Listener
{
	protected final InternalClient client;
	/**
	 * The exception that was thrown by the listener.
	 */
	protected IOException exception;
	/**
	 * A CountDownLatch that counts down to zero once {@code exception} is ready for use.
	 */
	protected final CountDownLatch exceptionReady = new CountDownLatch(1);
	protected final Logger log = LoggerFactory.getLogger(getClass());

	/**
	 * Creates a new instance.
	 *
	 * @param client the client configuration
	 * @throws NullPointerException if {@code client} is null
	 */
	protected AsyncResponseListener(InternalClient client)
	{
		this.client = client;
	}

	/**
	 * Processes a single JSON object returned by the server.
	 *
	 * @param objectAsString the String representation of the JSON object
	 * @throws NullPointerException if {@code objectAsString} is null
	 */
	protected void processObject(String objectAsString)
	{
		try
		{
			JsonNode json = client.getJsonMapper().readTree(objectAsString);
			JsonNode node = json.get("message");
			if (node != null)
			{
				String message = node.textValue();
				warnOnUnexpectedProperties(json, "message");
				log.info(message);
				return;
			}
			node = json.get("errorDetail");
			if (node != null)
			{
				warnOnUnexpectedProperties(json, "errorDetail", "error");
				warnOnUnexpectedProperties(node, "code", "message");
				String message = node.get("message").textValue();
				log.error(message);

				IOException e = new IOException(message);
				if (exception != null)
					e.addSuppressed(exception);
				exception = e;
				return;
			}
			node = json.get("error");
			if (node != null)
			{
				warnOnUnexpectedProperties(json, "error");
				String message = node.textValue();
				log.error(message);

				IOException e = new IOException(message);
				if (exception != null)
					e.addSuppressed(exception);
				exception = e;
			}
			processUnknownProperties(json);
		}
		catch (JsonProcessingException e)
		{
			if (exception != null)
				e.addSuppressed(exception);
			exception = e;
		}
	}

	/**
	 * Logs a warning on unexpected properties.
	 *
	 * @param json          the JSON node
	 * @param expectedNames the names of the expected properties
	 */
	protected void warnOnUnexpectedProperties(JsonNode json, String... expectedNames)
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

	/**
	 * Returns the exception thrown by the listener.
	 *
	 * @return the exception
	 */
	public IOException getException()
	{
		return exception;
	}

	/**
	 * Returns a {@code CountDownLatch} that reaches zero after the server response is processed.
	 *
	 * @return the {@code CountDownLatch}
	 */
	public CountDownLatch getExceptionReady()
	{
		return exceptionReady;
	}

	/**
	 * Process an unknown property returned by the server.
	 *
	 * @param json the JSON node
	 * @throws NullPointerException if {@code json} is null
	 */
	protected abstract void processUnknownProperties(JsonNode json);
}