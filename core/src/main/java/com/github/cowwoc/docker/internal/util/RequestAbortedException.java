package com.github.cowwoc.docker.internal.util;

import org.eclipse.jetty.client.Response.Listener;
import org.eclipse.jetty.client.Result;

import java.io.Serial;
import java.util.concurrent.CountDownLatch;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * Signals that a client wishes to abort an ongoing request.
 */
public class RequestAbortedException extends Exception
{
	@Serial
	private static final long serialVersionUID = 0L;
	private final transient CountDownLatch onComplete;

	/**
	 * Creates a new instance.
	 *
	 * @param onComplete a {@code CountDownLatch} that counts down immediately before
	 *                   {@link Listener#onComplete(Result)} completes
	 * @throws NullPointerException if {@code onComplete} is null
	 */
	public RequestAbortedException(CountDownLatch onComplete)
	{
		requireThat(onComplete, "onComplete").isNotNull();
		this.onComplete = onComplete;
	}

	/**
	 * Invoked by the Request immediately before returning from {@link Listener#onComplete(Result)}.
	 */
	public void onComplete()
	{
		onComplete.countDown();
	}
}