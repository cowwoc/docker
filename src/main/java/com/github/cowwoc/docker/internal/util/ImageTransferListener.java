package com.github.cowwoc.docker.internal.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.docker.client.DockerClient;
import com.github.cowwoc.docker.exception.ImageNotFoundException;
import com.github.cowwoc.pouch.core.WrappedCheckedException;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.eclipse.jetty.http.HttpStatus.FORBIDDEN_403;
import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.util.BufferUtil.EMPTY_BUFFER;

/**
 * Logs the output of "docker push" or "docker pull" incrementally.
 */
public final class ImageTransferListener extends JsonStreamListener
{
	/**
	 * Defines the frequency at which it is acceptable to log the same message to indicate that the thread is
	 * still active. This helps in monitoring the progress and ensuring the thread has not become unresponsive.
	 */
	private static final Duration PROGRESS_FREQUENCY = Duration.ofSeconds(2);
	private final AtomicReference<String> lastStatus = new AtomicReference<>("");
	private final AtomicReference<Instant> timeOfLastStatus = new AtomicReference<>(Instant.MIN);

	/**
	 * Creates a new instance.
	 *
	 * @param client the client configuration
	 * @throws NullPointerException if {@code client} is null
	 */
	public ImageTransferListener(DockerClient client)
	{
		super(client);
	}

	@Override
	protected void processUnknownProperties(JsonNode json)
	{
		JsonNode node = json.get("status");
		if (node != null)
		{
			warnOnUnexpectedProperties(json, "status", "id");
			String status = node.textValue();
			String id = node.get("id").textValue();

			Instant now = Instant.now();
			String message = status + ", id: " + id;
			if (!status.equals(lastStatus.get()) ||
				Duration.between(timeOfLastStatus.get(), now).compareTo(PROGRESS_FREQUENCY) >= 0)
			{
				// Only log the status if it's changed or PROGRESS_FREQUENCY has elapsed
				lastStatus.set(message);
				timeOfLastStatus.set(now);
				log.info(message);
			}
			return;
		}
		IOException e = new IOException("Unexpected response: " + json.toPrettyString());
		if (exception != null)
			e.addSuppressed(exception);
		exception = e;
	}

	@Override
	public void onComplete(Result result)
	{
		try
		{
			Response response = result.getResponse();
			decodeBytes(EMPTY_BUFFER, true);
			switch (response.getStatus())
			{
				case OK_200 -> processResponse(true);
				case FORBIDDEN_403 ->
				{
					// Example: Surpassed storage quota
					JsonNode body = getResponseBody();
					IOException e = new IOException(body.get("message").textValue());
					if (exception != null)
						e.addSuppressed(exception);
					exception = e;
				}
				case NOT_FOUND_404 ->
				{
					JsonNode body = getResponseBody();
					ImageNotFoundException e = new ImageNotFoundException(body.get("message").textValue());
					if (exception != null)
						e.addSuppressed(exception);
					exception = e;
				}
				case INTERNAL_SERVER_ERROR_500 ->
				{
					IOException e = new IOException(responseAsString.toString());
					if (exception != null)
						e.addSuppressed(exception);
					exception = e;
				}
				default ->
				{
					IOException ioe = new IOException("Unexpected response: " + client.toString(response) + "\n" +
						"Request: " + client.toString(result.getRequest()));
					if (exception != null)
						ioe.addSuppressed(exception);
					exception = ioe;
				}
			}
		}
		finally
		{
			exceptionReady.countDown();
		}
	}

	/**
	 * @return the server response as JSON
	 */
	private JsonNode getResponseBody()
	{
		try
		{
			return client.getObjectMapper().readTree(responseAsString.toString());
		}
		catch (JsonProcessingException e)
		{
			throw WrappedCheckedException.wrap(e);
		}
	}
}