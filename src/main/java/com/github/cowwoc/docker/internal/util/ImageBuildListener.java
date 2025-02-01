package com.github.cowwoc.docker.internal.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.docker.internal.client.InternalClient;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.util.BufferUtil.EMPTY_BUFFER;

/**
 * Logs the output of "docker build" incrementally.
 */
@SuppressWarnings("ClassEscapesDefinedScope")
public final class ImageBuildListener extends JsonStreamListener
{
	public String imageId;

	/**
	 * Creates a new instance.
	 *
	 * @param client the client configuration
	 * @throws NullPointerException if {@code client} is null
	 */
	public ImageBuildListener(InternalClient client)
	{
		super(client, new LinkedBlockingQueue<>());
	}

	@Override
	protected void processUnknownProperties(JsonNode json)
	{
		JsonNode node = json.get("stream");
		if (node != null)
		{
			warnOnUnexpectedProperties(json, "stream");
			linesToLog.append(node.textValue());
			Strings.logLines(linesToLog, log);
			return;
		}
		node = json.get("aux");
		if (node != null)
		{
			warnOnUnexpectedProperties(json, "aux");
			warnOnUnexpectedProperties(node, "ID");
			assert (imageId == null);
			JsonNode idNode = node.get("ID");
			imageId = idNode.textValue();
			return;
		}
		// BUG: https://github.com/docker/docs/issues/21803
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
			decodeBytes(EMPTY_BUFFER, true);
			processResponse(true);
			if (!linesToLog.isEmpty())
				log.info(linesToLog.toString());

			// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Image/operation/ImageBuild
			Response response = result.getResponse();
			switch (response.getStatus())
			{
				case OK_200 -> processResponse(true);
				case INTERNAL_SERVER_ERROR_500 -> exceptions.add(
					new IOException("Unexpected response: " + responseAsString + "\n" +
						"Request: " + client.toString(result.getRequest())));
				default -> exceptions.add(new IOException("Unexpected response: " + client.toString(response) + "\n" +
					"Request: " + client.toString(result.getRequest())));
			}
		}
		finally
		{
			responseReady.countDown();
		}
	}
}