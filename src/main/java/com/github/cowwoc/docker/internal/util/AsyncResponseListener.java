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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;

/**
 * Processes JSON objects returned by a server response.
 */
@SuppressWarnings("ClassEscapesDefinedScope")
public abstract class AsyncResponseListener implements Response.Listener
{
	/**
	 * The docker client.
	 */
	protected final InternalClient client;
	/**
	 * The exceptions that were thrown by the operation.
	 */
	protected final BlockingQueue<Throwable> exceptions;
	/**
	 * A CountDownLatch that counts down to zero once the response is ready for processing.
	 */
	protected final CountDownLatch responseReady = new CountDownLatch(1);
	protected final Logger log = LoggerFactory.getLogger(getClass());

	/**
	 * Creates a new instance.
	 *
	 * @param client     the client configuration
	 * @param exceptions a container for exceptions that are thrown by the operation
	 * @throws NullPointerException if any of the arguments are null
	 */
	protected AsyncResponseListener(InternalClient client, BlockingQueue<Throwable> exceptions)
	{
		assert that(client, "client").isNotNull().elseThrow();
		assert that(exceptions, "exceptions").isNotNull().elseThrow();

		this.client = client;
		this.exceptions = exceptions;
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
				exceptions.add(new IOException(message));
				return;
			}
			node = json.get("error");
			if (node != null)
			{
				warnOnUnexpectedProperties(json, "error");
				String message = node.textValue();
				log.error(message);
				exceptions.add(new IOException(message));
			}
			processUnknownProperties(json);
		}
		catch (JsonProcessingException e)
		{
			exceptions.add(e);
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
	 * Returns an exception that encapsulates all the errors encountered by the operation. This method should be
	 * called only after the request has completed; otherwise, additional errors may still occur.
	 *
	 * @return null if no errors occurred
	 */
	public IOException getException()
	{
		if (exceptions.isEmpty())
			return null;
		if (exceptions.size() == 1)
		{
			Throwable first = exceptions.poll();
			if (first instanceof IOException ioe)
				return ioe;
			return new IOException(first);
		}
		StringBuilder combinedMessage = new StringBuilder(38).append("The operation threw ").
			append(exceptions.size()).append(" exceptions.\n");
		int i = 1;
		for (Throwable exception : exceptions)
		{
			combinedMessage.append(i).append(". ").append(exception.getClass().getName());
			String message = exception.getMessage();
			if (message != null)
			{
				combinedMessage.append(": ").
					append(message).
					append('\n');
			}
			++i;
		}
		return new IOException(combinedMessage.toString());
	}

	/**
	 * Returns a {@code CountDownLatch} that reaches zero when the request completes.
	 *
	 * @return the {@code CountDownLatch}
	 */
	public CountDownLatch getRequestComplete()
	{
		return responseReady;
	}

	/**
	 * Process an unknown property returned by the server.
	 *
	 * @param json the JSON node
	 * @throws NullPointerException if {@code json} is null
	 */
	protected abstract void processUnknownProperties(JsonNode json);
}