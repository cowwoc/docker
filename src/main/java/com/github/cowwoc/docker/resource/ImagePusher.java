package com.github.cowwoc.docker.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.cowwoc.docker.client.DockerClient;
import com.github.cowwoc.docker.internal.util.ClientRequests;
import com.github.cowwoc.docker.internal.util.StreamListener;
import com.github.cowwoc.docker.internal.util.ToStringBuilder;
import com.github.cowwoc.pouch.core.WrappedCheckedException;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.eclipse.jetty.http.HttpStatus.FORBIDDEN_403;
import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.eclipse.jetty.util.BufferUtil.EMPTY_BUFFER;

/**
 * Pushes an image to a remote repository.
 */
public final class ImagePusher
{
	private final DockerClient client;
	private final String name;
	private final String tag;
	private String platform = "";
	private ObjectNode credentials;
	private final Logger log = LoggerFactory.getLogger(ImagePusher.class);

	/**
	 * Creates a new instance.
	 *
	 * @param client the client configuration
	 * @param name   the name of the image to push. For example, {@code docker.io/nasa/rocket-ship}. The image
	 *               must be present in the local image store with the same name.
	 * @param tag    the tag to push
	 * @throws NullPointerException     if any of the arguments are null
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 */
	public ImagePusher(DockerClient client, String name, String tag)
	{
		requireThat(client, "client").isNotNull();
		requireThat(name, "name").isStripped().isNotEmpty();
		requireThat(tag, "tag").isStripped().isNotEmpty();
		this.client = client;
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
	 * @throws NullPointerException     if any of the parameters are absent
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
	 * @throws NullPointerException     if any of the mandatory parameters are absent
	 * @throws IllegalArgumentException if any of the arguments contain leading or trailing whitespace or are
	 *                                  empty
	 * @throws IllegalStateException    if the client is closed
	 */
	public ImagePusher credentials(String username, String password, String email, String serverAddress)
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#section/Authentication
		requireThat(username, "username").isStripped().isNotEmpty();
		requireThat(password, "password").isStripped().isNotEmpty();
		requireThat(email, "email").isNotNull().isStripped();
		requireThat(serverAddress, "serverAddress").isNotNull().isStripped();

		ObjectNode credentials = client.getObjectMapper().createObjectNode();
		credentials.put("username", username);
		credentials.put("password", password);
		this.credentials = credentials;
		return this;
	}

	/**
	 * Pushes the image to the remote registry.
	 *
	 * @throws IllegalStateException if the client is closed
	 * @throws IOException           if an I/O error occurs. These errors are typically transient, and retrying
	 *                               the request may resolve the issue.
	 * @throws TimeoutException      if the request times out before receiving a response. This might indicate
	 *                               network latency or server overload.
	 * @throws InterruptedException  if the thread is interrupted while waiting for a response. This can happen
	 *                               due to shutdown signals.
	 */
	public void push() throws IOException, TimeoutException, InterruptedException
	{
		// https://docs.docker.com/reference/api/engine/version/v1.47/#tag/Image/operation/ImagePush
		@SuppressWarnings("PMD.CloseResource")
		HttpClient httpClient = client.getHttpClient();
		ClientRequests clientRequests = client.getClientRequests();
		String uri = client.getUri() + "/images/" + name + "/push";

		ObjectMapper om = client.getObjectMapper();
		ObjectNode platformNode = om.createObjectNode();
		String[] platformComponents = platform.split("/");
		platformNode.put("os", platformComponents[0]);
		if (platformComponents.length > 1)
			platformNode.put("architecture", platformComponents[1]);
		if (platformComponents.length > 2)
			platformNode.put("variant", platformComponents[2]);

		ImagePushListener responseListener = new ImagePushListener();
		Request request = httpClient.newRequest(uri).
			param("tag", tag).
			param("platform", platformNode.toString()).
			transport(client.getTransport()).
			method(POST);
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

		clientRequests.send(request, responseListener);
		if (!responseListener.responseComplete.await(5, TimeUnit.MINUTES))
			throw new TimeoutException();
		if (responseListener.exception != null)
		{
			if (responseListener.exception instanceof FileNotFoundException child)
			{
				FileNotFoundException parent = new FileNotFoundException();
				parent.initCause(child);
				throw parent;
			}
			throw new IOException(responseListener.exception);
		}
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

	/**
	 * Logs the output of "docker push" incrementally.
	 */
	private final class ImagePushListener extends StreamListener
	{
		/**
		 * Defines the frequency at which it is acceptable to log the same message to indicate that the thread is
		 * still active. This helps in monitoring the progress and ensuring the thread has not become
		 * unresponsive.
		 */
		private static final Duration PROGRESS_FREQUENCY = Duration.ofSeconds(2);
		private final AtomicReference<String> lastStatus = new AtomicReference<>("");
		private final AtomicReference<Instant> timeOfLastStatus = new AtomicReference<>(Instant.MIN);

		/**
		 * Creates a new instance.
		 */
		public ImagePushListener()
		{
		}

		@Override
		protected void processObject(String jsonAsString)
		{
			try
			{
				JsonNode json = client.getObjectMapper().readTree(jsonAsString);
				JsonNode statusNode = json.get("status");
				if (statusNode != null)
				{
					String status = statusNode.textValue();
					Instant now = Instant.now();
					if (!status.equals(lastStatus.get()) ||
						Duration.between(timeOfLastStatus.get(), now).compareTo(PROGRESS_FREQUENCY) >= 0)
					{
						// Only log the status if it's changed or PROGRESS_FREQUENCY has elapsed
						lastStatus.set(status);
						timeOfLastStatus.set(now);
						log.info(status);
					}
				}
				JsonNode errorDetailNode = json.get("errorDetail");
				if (errorDetailNode != null)
				{
					String message = errorDetailNode.get("message").textValue();
					IOException ioe = new IOException(message);
					if (exception != null)
						ioe.addSuppressed(exception);
					exception = ioe;
				}
			}
			catch (JsonProcessingException e)
			{
				if (exception != null)
					e.addSuppressed(exception);
				exception = e;
			}
		}

		@Override
		public void onComplete(Result result)
		{
			decodeObjects(EMPTY_BUFFER, true);
			processObject(true);

			Response response = result.getResponse();
			try
			{
				switch (response.getStatus())
				{
					case OK_200 ->
					{
					}
					case FORBIDDEN_403 ->
					{
						// Example: Surpassed storage quota
						ContentResponse serverResponse = (ContentResponse) result.getResponse();
						JsonNode json = client.getObjectMapper().readTree(serverResponse.getContentAsString());
						exception = new FileNotFoundException(json.get("message").textValue());
					}
					case NOT_FOUND_404 ->
					{
						ContentResponse serverResponse = (ContentResponse) result.getResponse();
						JsonNode json = client.getObjectMapper().readTree(serverResponse.getContentAsString());
						FileNotFoundException e = new FileNotFoundException(json.get("message").textValue());
						if (exception != null)
							e.addSuppressed(exception);
						exception = e;
					}
					case INTERNAL_SERVER_ERROR_500 ->
					{
						ContentResponse serverResponse = (ContentResponse) result.getResponse();
						JsonNode json = client.getObjectMapper().readTree(serverResponse.getContentAsString());
						IOException e = new IOException(json.get("message").textValue());
						if (exception != null)
							e.addSuppressed(exception);
						exception = e;
					}
					default ->
					{
						ClientRequests clientRequests = client.getClientRequests();
						throw new AssertionError(
							"Unexpected response: " + clientRequests.toString(response) + "\n" +
								"Request: " + clientRequests.toString(result.getRequest()));
					}
				}
			}
			catch (JsonProcessingException e)
			{
				throw new AssertionError(e);
			}
			responseComplete.countDown();
		}
	}
}