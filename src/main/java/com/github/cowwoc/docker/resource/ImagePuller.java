package com.github.cowwoc.docker.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.cowwoc.docker.client.DockerClient;
import com.github.cowwoc.docker.exception.ImageNotFoundException;
import com.github.cowwoc.docker.internal.util.ImageTransferListener;
import com.github.cowwoc.docker.internal.util.ToStringBuilder;
import com.github.cowwoc.pouch.core.WrappedCheckedException;
import org.eclipse.jetty.client.Request;

import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jetty.http.HttpMethod.POST;

/**
 * Pulls an image from a remote repository.
 */
public final class ImagePuller
{
	private final DockerClient client;
	private final String id;
	private String platform = "";
	private ObjectNode credentials;

	/**
	 * Creates a new instance.
	 *
	 * @param client the client configuration
	 * @param id     an image's name or ID. If a name is specified, it may include a tag or a digest.
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if {@code id} contains leading or trailing whitespace or is empty
	 */
	public ImagePuller(DockerClient client, String id)
	{
		requireThat(client, "client").isNotNull();
		// WORKAROUND: https://github.com/docker/docs/issues/21793
		// Remote images must have a name
		requireThat(id, "id").isStripped().isNotEmpty().doesNotStartWith("sha256:");
		this.client = client;
		this.id = id;
	}

	/**
	 * Sets the platform to pull.
	 *
	 * @param platform the platform of the image
	 * @return this
	 * @throws NullPointerException     if {@code platform} is null
	 * @throws IllegalArgumentException if {@code platform} contains leading or trailing whitespace or is empty
	 */
	public ImagePuller platform(String platform)
	{
		requireThat(platform, "platform").isStripped().isNotEmpty();
		this.platform = platform;
		return this;
	}

	/**
	 * Sets the registry credentials.
	 *
	 * @param username the user's name
	 * @param password the user's password
	 * @return this
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 */
	public ImagePuller credentials(String username, String password)
	{
		return credentials(username, password, "", "");
	}

	/**
	 * Sets the registry credentials.
	 *
	 * @param username      the user's name
	 * @param password      the user's password
	 * @param email         (optional) the user's email address, or an empty string if absent
	 * @param serverAddress (optional) the name of the registry server, or an empty string if absent
	 * @return this
	 * @throws NullPointerException     if any of the mandatory parameters are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 * @throws IllegalStateException    if the client is closed
	 */
	public ImagePuller credentials(String username, String password, String email, String serverAddress)
	{
		requireThat(username, "username").isStripped().isNotEmpty();
		requireThat(password, "password").isStripped().isNotEmpty();
		requireThat(email, "email").isNotNull().isStripped();
		requireThat(serverAddress, "serverAddress").isNotNull().isStripped();

		// https://docs.docker.com/reference/api/engine/version/v1.47/#section/Authentication
		ObjectNode credentials = client.getObjectMapper().createObjectNode();
		credentials.put("username", username);
		credentials.put("password", password);
		this.credentials = credentials;
		return this;
	}

	/**
	 * Pulls the image from the remote registry.
	 *
	 * @return the image
	 * @throws IllegalStateException  if the client is closed
	 * @throws ImageNotFoundException if the referenced image could not be found
	 * @throws IOException            if an I/O error occurs. These errors are typically transient, and retrying
	 *                                the request may resolve the issue.
	 * @throws TimeoutException       if the request times out before receiving a response. This might indicate
	 *                                network latency or server overload.
	 * @throws InterruptedException   if the thread is interrupted while waiting for a response. This can happen
	 *                                due to shutdown signals.
	 */
	public Image pull() throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Image/operation/ImageCreate
		URI uri = client.getServer().resolve("images/create");
		Request request = client.createRequest(uri).
			param("fromImage", id);
		if (!platform.isEmpty())
			request.param("platform", platform);
		request.method(POST);
		if (credentials != null)
		{
			String credentialsAsString;
			try
			{
				credentialsAsString = client.getObjectMapper().writeValueAsString(credentials);
			}
			catch (JsonProcessingException e)
			{
				throw WrappedCheckedException.wrap(e);
			}
			String encodedCredentials = Base64.getEncoder().encodeToString(credentialsAsString.getBytes(UTF_8));
			request.headers(headers -> headers.put("X-Registry-Auth", encodedCredentials));
		}

		ImageTransferListener responseListener = new ImageTransferListener(client);
		client.send(request, responseListener);
		if (!responseListener.getExceptionReady().await(5, TimeUnit.MINUTES))
			throw new TimeoutException();
		IOException exception = responseListener.getException();
		if (exception != null)
		{
			// Need to wrap the exception to ensure that it contains stack trace elements from the current thread
			throw new IOException(exception);
		}

		String localId = client.removeRegistry(id);
		return Image.getById(client, localId);
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(ImagePuller.class).
			add("name", id).
			add("platform", platform).
			add("credentials", credentials).
			toString();
	}
}