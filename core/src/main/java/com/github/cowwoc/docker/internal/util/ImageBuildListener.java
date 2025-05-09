package com.github.cowwoc.docker.internal.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.cowwoc.docker.internal.client.InternalClient;
import com.github.moby.buildkit.v1.ControlOuterClass.StatusResponse;
import com.github.moby.buildkit.v1.ControlOuterClass.Vertex;
import com.github.moby.buildkit.v1.ControlOuterClass.VertexLog;
import com.github.moby.buildkit.v1.ControlOuterClass.VertexStatus;
import com.google.protobuf.InvalidProtocolBufferException;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.slf4j.event.Level;

import java.io.IOException;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.concurrent.LinkedBlockingQueue;

import static com.github.cowwoc.docker.internal.util.Json.warnOnUnexpectedProperties;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.util.BufferUtil.EMPTY_BUFFER;

/**
 * Logs the output of "docker build" incrementally.
 */
@SuppressWarnings("ClassEscapesDefinedScope")
public final class ImageBuildListener extends JsonStreamListener
{
	private final Decoder base64 = Base64.getDecoder();
	private String imageId;

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
		JsonNode node = json.get("aux");
		if (node != null)
		{
			warnOnUnexpectedProperties(log, json, "id", "aux");
			try
			{
				if (node.isTextual())
				{
					// BuildKit output
					String id = json.get("id").textValue();
					assert that(id, "id").isEqualTo("moby.buildkit.trace").elseThrow();

					byte[] aux = base64.decode(node.textValue());
					StatusResponse statusResponse = StatusResponse.parseFrom(aux);
					processStatus(statusResponse);
					return;
				}
				warnOnUnexpectedProperties(log, node, "ID");
				assert (imageId == null) : imageId;
				imageId = node.get("ID").textValue();
				return;
			}
			catch (InvalidProtocolBufferException e)
			{
				exceptions.add(e);
				return;
			}
		}
		exceptions.add(new IOException("Unexpected response: " + json.toPrettyString()));
	}

	private void processStatus(StatusResponse status)
	{
		assert that(status.getWarningsCount(), "status.getWarningsCount()").isEqualTo(0).elseThrow();

		for (Vertex vertex : status.getVertexesList())
		{
			if (!(vertex.hasStarted() && vertex.hasCompleted()))
				continue;
			String name = vertex.getName();
			logMessage(name, Level.INFO);
		}
		for (VertexStatus vertex : status.getStatusesList())
			logMessage(vertex.getID(), Level.DEBUG);
		for (VertexLog vertex : status.getLogsList())
			logMessage(vertex.getMsg().toStringUtf8(), Level.DEBUG);
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

			// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Image/operation/ImageBuild
			Response response = result.getResponse();
			switch (response.getStatus())
			{
				case OK_200 -> processResponse(true);
				case BAD_REQUEST_400 ->
				{
					JsonNode body = getResponseBody();
					exceptions.add(new IOException(body.get("message").textValue()));
				}
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

	/**
	 * @return the ID of the new image
	 */
	public String getImageId()
	{
		return imageId;
	}
}