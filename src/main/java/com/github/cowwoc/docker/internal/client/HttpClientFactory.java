package com.github.cowwoc.docker.internal.client;

import com.github.cowwoc.pouch.core.ConcurrentLazyFactory;
import com.github.cowwoc.pouch.core.WrappedCheckedException;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.time.Duration;

/**
 * Creates and destroys an {@code HttpClient}.
 */
public final class HttpClientFactory extends ConcurrentLazyFactory<HttpClient>
{
	private final Duration connectTimeout;
	private final Duration idleTimeout;
	private final QueuedThreadPool clientExecutor;

	/**
	 * Creates a new HttpClientFactory.
	 *
	 * @param client the client configuration
	 * @throws NullPointerException if {@code client} is null
	 */
	public HttpClientFactory(InternalClient client)
	{
		this.connectTimeout = Duration.ofSeconds(5);
		this.idleTimeout = switch (client.getRunMode())
		{
			case DEBUG -> Duration.ofMinutes(5);
			case RELEASE -> Duration.ofSeconds(30);
		};
		this.clientExecutor = new QueuedThreadPool();
		clientExecutor.setName(HttpClient.class.getSimpleName());
	}

	@Override
	protected HttpClient createValue()
	{
		HttpClient httpClient = new HttpClient();
		httpClient.setExecutor(clientExecutor);
		httpClient.setConnectTimeout(connectTimeout.toMillis());
		httpClient.setIdleTimeout(idleTimeout.toMillis());

		try
		{
			httpClient.start();
		}
		catch (Exception e)
		{
			throw WrappedCheckedException.wrap(e);
		}
		return httpClient;
	}

	@Override
	protected void disposeValue(HttpClient client)
	{
		try
		{
			client.stop();
		}
		catch (Exception e)
		{
			throw WrappedCheckedException.wrap(e);
		}
	}
}