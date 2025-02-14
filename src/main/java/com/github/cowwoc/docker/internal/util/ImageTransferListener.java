package com.github.cowwoc.docker.internal.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.docker.exception.ResourceNotFoundException;
import com.github.cowwoc.docker.internal.client.InternalClient;
import com.github.cowwoc.pouch.core.WrappedCheckedException;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import static org.eclipse.jetty.http.HttpStatus.FORBIDDEN_403;
import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.util.BufferUtil.EMPTY_BUFFER;

/**
 * Logs the output of "docker push" or "docker pull" incrementally.
 */
@SuppressWarnings("ClassEscapesDefinedScope")
public final class ImageTransferListener extends JsonStreamListener
{
	/**
	 * Creates a new instance.
	 *
	 * @param client the client configuration
	 * @throws NullPointerException if {@code client} is null
	 */
	public ImageTransferListener(InternalClient client)
	{
		super(client, new LinkedBlockingQueue<>());
	}

	@Override
	protected void processUnknownProperties(JsonNode json)
	{
		if (json.has("status"))
		{
			processStatus(json);
			return;
		}
		exceptions.add(new IOException("Unexpected response: " + json.toPrettyString()));
	}

	@Override
	public void onComplete(Result result)
	{
		try
		{
			if (result.getRequestFailure() != null)
				exceptions.add(result.getRequestFailure());
			if (result.getResponseFailure() != null)
				exceptions.add(result.getResponseFailure());
			Response response = result.getResponse();
			decodeBytes(EMPTY_BUFFER, true);
			switch (response.getStatus())
			{
				case OK_200 -> processResponse(true);
				case FORBIDDEN_403 ->
				{
					// Example: Surpassed storage quota
					JsonNode body = getResponseBody();
					exceptions.add(new IOException(body.get("message").textValue()));
				}
				case NOT_FOUND_404 ->
				{
					JsonNode body = getResponseBody();
					exceptions.add(new ResourceNotFoundException(body.get("message").textValue()));
				}
				case INTERNAL_SERVER_ERROR_500 -> exceptions.add(new IOException(responseAsString.toString()));
				default -> exceptions.add(new IOException("Unexpected response: " + client.toString(response) + "\n" +
					"Request: " + client.toString(result.getRequest())));
			}
		}
		finally
		{
			responseReady.countDown();
		}
	}

	/**
	 * @return the server response as JSON
	 */
	private JsonNode getResponseBody()
	{
		try
		{
			return client.getJsonMapper().readTree(responseAsString.toString());
		}
		catch (JsonProcessingException e)
		{
			throw WrappedCheckedException.wrap(e);
		}
	}
}