package com.github.cowwoc.docker.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.cowwoc.docker.exception.ImageNotFoundException;
import com.github.cowwoc.docker.internal.client.InternalClient;
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
 * Pushes an image to a remote repository.
 */
public final class ImagePusher
{
	private final InternalClient client;
	private final Image image;
	private final String name;
	private final String tag;
	private String platform = "";
	private ObjectNode credentials;

	/**
	 * Creates a new instance.
	 *
	 * @param client the client configuration
	 * @param image  the image to push
	 * @param name   the name of the image to push. For example, {@code docker.io/nasa/rocket-ship}. The image
	 *               must be present in the local image store with the same name.
	 * @param tag    the tag to push
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 */
	ImagePusher(InternalClient client, Image image, String name, String tag)
	{
		requireThat(client, "client").isNotNull();
		requireThat(image, "image").isNotNull();
		requireThat(tag, "tag").isStripped().isNotEmpty();
		this.client = client;
		this.image = image;
		this.name = name;
		this.tag = tag;
	}

	/**
	 * Sets the platform to push.
	 *
	 * @param platform the platform of the image
	 * @return this
	 * @throws NullPointerException     if {@code platform} is null
	 * @throws IllegalArgumentException if {@code platform} contains leading or trailing whitespace or is empty
	 */
	public ImagePusher platform(String platform)
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
	public ImagePusher credentials(String username, String password)
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
	public ImagePusher credentials(String username, String password, String email, String serverAddress)
	{
		requireThat(username, "username").isStripped().isNotEmpty();
		requireThat(password, "password").isStripped().isNotEmpty();
		requireThat(email, "email").isNotNull().isStripped();
		requireThat(serverAddress, "serverAddress").isNotNull().isStripped();

		// https://docs.docker.com/reference/api/engine/version/v1.47/#section/Authentication
		ObjectNode credentials = client.getJsonMapper().createObjectNode();
		credentials.put("username", username);
		credentials.put("password", password);
		this.credentials = credentials;
		return this;
	}

	/**
	 * Pushes the image to the remote registry.
	 *
	 * @return the image that was pushed
	 * @throws IllegalStateException  if the client is closed
	 * @throws ImageNotFoundException if the referenced image could not be found
	 * @throws IOException            if an I/O error occurs. These errors are typically transient, and retrying
	 *                                the request may resolve the issue.
	 * @throws TimeoutException       if the request times out before receiving a response. This might indicate
	 *                                network latency or server overload.
	 * @throws InterruptedException   if the thread is interrupted while waiting for a response. This can happen
	 *                                due to shutdown signals.
	 */
	public Image push() throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Image/operation/ImagePush
		String encodedName = name.replace("/", "%2F");
		URI uri = client.getServer().resolve("images/" + encodedName + "/push");
		Request request = client.createRequest(uri).
			param("tag", tag);
		if (!platform.isEmpty())
		{
			JsonMapper jm = client.getJsonMapper();
			ObjectNode platformNode = jm.createObjectNode();
			String[] platformComponents = platform.split("/");
			platformNode.put("os", platformComponents[0]);
			if (platformComponents.length > 1)
				platformNode.put("architecture", platformComponents[1]);
			if (platformComponents.length > 2)
				platformNode.put("variant", platformComponents[2]);
			request.param("platform", platformNode.toString());
		}
		if (credentials != null)
		{
			String credentialsAsString;
			try
			{
				credentialsAsString = client.getJsonMapper().writeValueAsString(credentials);
			}
			catch (JsonProcessingException e)
			{
				throw WrappedCheckedException.wrap(e);
			}
			String encodedCredentials = Base64.getEncoder().encodeToString(credentialsAsString.getBytes(UTF_8));
			request.headers(headers -> headers.put("X-Registry-Auth", encodedCredentials));
		}
		request.method(POST);

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
		return image;
	}

	@Override
	public String toString()
	{
		return new ToStringBuilder(ImagePusher.class).
			add("name", name).
			add("tag", tag).
			add("platform", platform).
			add("credentials", credentials).
			toString();
	}
}