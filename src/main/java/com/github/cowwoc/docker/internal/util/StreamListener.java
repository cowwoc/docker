package com.github.cowwoc.docker.internal.util;

import org.eclipse.jetty.client.Response;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.concurrent.CountDownLatch;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Processes JSON objects returned by a server response.
 */
public abstract class StreamListener implements Response.Listener
{
	/**
	 * The exception that was thrown by the listener.
	 */
	public IOException exception;
	/**
	 * A CountDownLatch that counts down to zero once the server response is processed.
	 */
	public final CountDownLatch responseComplete = new CountDownLatch(1);
	private final CharsetDecoder decoder = UTF_8.newDecoder();
	private final CharBuffer charBuffer = CharBuffer.allocate(200);
	private final StringBuilder objects = new StringBuilder();

	/**
	 * Creates a new instance.
	 */
	protected StreamListener()
	{
	}

	@Override
	public void onContent(Response response, ByteBuffer content)
	{
		decodeObjects(content, false);
		processObject(false);
	}

	/**
	 * Decodes the server response into JSON objects.
	 *
	 * @param input      the bytes to decode
	 * @param endOfInput {@code true} if the end of the input stream has been reached
	 */
	protected void decodeObjects(ByteBuffer input, boolean endOfInput)
	{
		try
		{
			while (true)
			{
				CoderResult result = decoder.decode(input, charBuffer, endOfInput);
				charBuffer.flip();
				objects.append(charBuffer);
				charBuffer.clear();
				if (result.isError())
					result.throwException();
				if (result.isUnderflow())
					break;
			}
		}
		catch (CharacterCodingException e)
		{
			if (exception != null)
				e.addSuppressed(exception);
			exception = e;
		}
	}

	/**
	 * Processes the objects that have been decoded so far.
	 *
	 * @param endOfInput {@code true} if the end of the input stream has been reached
	 */
	protected void processObject(boolean endOfInput)
	{
		String object;
		while (true)
		{
			int newline = objects.indexOf(System.lineSeparator());
			if (newline == -1)
				break;
			object = objects.substring(0, newline);
			objects.delete(0, newline + System.lineSeparator().length());
			processObject(object);
		}
		if (endOfInput)
			processObject(objects.toString());
	}

	/**
	 * Processes a single JSON object returned by the server.
	 *
	 * @param jsonAsString the string representation of a JSON object
	 * @throws NullPointerException if {@code jsonAsString} is null
	 */
	protected abstract void processObject(String jsonAsString);
}