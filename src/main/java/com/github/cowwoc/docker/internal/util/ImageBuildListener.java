package com.github.cowwoc.docker.internal.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.docker.client.DockerClient;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;

import java.io.IOException;

import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.util.BufferUtil.EMPTY_BUFFER;

/**
 * Logs the output of "docker build" incrementally.
 */
public final class ImageBuildListener extends JsonStreamListener
{
	/**
	 * Lines to log using the {@code INFO} level.
	 */
	private final StringBuilder linesToLog = new StringBuilder();
	public String imageId;

	/**
	 * Creates a new instance.
	 *
	 * @param client the client configuration
	 * @throws NullPointerException if {@code client} is null
	 */
	public ImageBuildListener(DockerClient client)
	{
		super(client);
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
			assert (imageId == null);
			warnOnUnexpectedProperties(json, "aux");
			warnOnUnexpectedProperties(node, "ID");
			JsonNode idNode = node.get("ID");
			imageId = idNode.textValue();
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
		decodeBytes(EMPTY_BUFFER, true);
		processResponse(true);
		if (!linesToLog.isEmpty())
			log.info(linesToLog.toString());

		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Image/operation/ImageBuild
		Response response = result.getResponse();
		switch (response.getStatus())
		{
			case OK_200 -> processResponse(true);
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
		exceptionReady.countDown();
	}
}