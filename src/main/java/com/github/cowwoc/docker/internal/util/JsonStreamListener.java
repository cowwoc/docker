package com.github.cowwoc.docker.internal.util;

import com.github.cowwoc.docker.internal.client.InternalClient;
import org.eclipse.jetty.client.Response;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Processes JSON objects returned by a server response.
 */
public abstract class JsonStreamListener extends AsyncResponseListener
{
	private final CharsetDecoder decoder = UTF_8.newDecoder();
	private final CharBuffer charBuffer = CharBuffer.allocate(200);
	protected final StringBuilder responseAsString = new StringBuilder();

	/**
	 * Creates a new instance.
	 *
	 * @param client the client configuration
	 * @throws NullPointerException if {@code client} is null
	 */
	protected JsonStreamListener(InternalClient client)
	{
		super(client);
	}

	@Override
	public void onContent(Response response, ByteBuffer content)
	{
		decodeBytes(content, false);
		processResponse(false);
	}

	/**
	 * Decodes the server response into a String.
	 *
	 * @param input      the bytes to decode
	 * @param endOfInput {@code true} if the end of the input stream has been reached
	 */
	protected void decodeBytes(ByteBuffer input, boolean endOfInput)
	{
		try
		{
			while (true)
			{
				CoderResult result = decoder.decode(input, charBuffer, endOfInput);
				charBuffer.flip();
				responseAsString.append(charBuffer);
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
	 * Processes the server response that has been decoded so far.
	 *
	 * @param endOfInput {@code true} if the end of the input stream has been reached
	 */
	protected void processResponse(boolean endOfInput)
	{
		String line;
		while (true)
		{
			int newline = responseAsString.indexOf(System.lineSeparator());
			if (newline == -1)
				break;
			line = responseAsString.substring(0, newline);
			responseAsString.delete(0, newline + System.lineSeparator().length());
			if (!line.isEmpty())
				processObject(line);
		}
		if (endOfInput && !responseAsString.isEmpty())
			processObject(responseAsString.toString());
	}
}