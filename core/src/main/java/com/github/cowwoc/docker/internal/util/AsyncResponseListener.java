package com.github.cowwoc.docker.internal.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.docker.internal.client.InternalClient;
import org.eclipse.jetty.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
	/**
	 * The logger.
	 */
	protected final Logger log = LoggerFactory.getLogger(AsyncResponseListener.class);

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
	 * Processes a JSON object returned by the server.
	 *
	 * @param object the object
	 * @throws NullPointerException if {@code object} is null
	 */
	protected void processObject(String object)
	{
		try
		{
			JsonNode json = client.getJsonMapper().readTree(object);
			JsonNode node = json.get("message");
			if (node != null)
			{
				String message = node.textValue();
				Json.warnOnUnexpectedProperties(log, json, "message");
				log.info(Strings.removeNewlineFromEnd(message));
				return;
			}
			node = json.get("errorDetail");
			if (node != null)
			{
				Json.warnOnUnexpectedProperties(log, json, "errorDetail", "error");
				Json.warnOnUnexpectedProperties(log, node, "code", "message");
				String message = node.get("message").textValue();
				exceptions.add(new IOException(message));
				return;
			}
			node = json.get("error");
			if (node != null)
			{
				Json.warnOnUnexpectedProperties(log, json, "error");
				String message = node.textValue();
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
	 * Returns an exception that encapsulates all the errors encountered by the operation. This method should be
	 * called only after the request has completed; otherwise, additional errors may still occur.
	 *
	 * @return null if no errors occurred
	 */
	public IOException getException()
	{
		return Exceptions.combineAsIOException(exceptions);
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