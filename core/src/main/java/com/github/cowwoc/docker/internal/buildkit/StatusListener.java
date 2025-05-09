package com.github.cowwoc.docker.internal.buildkit;

import com.github.moby.buildkit.v1.ControlOuterClass.StatusRequest;
import com.github.moby.buildkit.v1.ControlOuterClass.StatusResponse;

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;

/**
 * Monitors the status of a build.
 */
public final class StatusListener
{
	private final Build build;
	private final Queue<Throwable> exceptions;
	private final AtomicReference<StatusResponse> response = new AtomicReference<>();

	/**
	 * Creates a new listener.
	 *
	 * @param build      the build to monitor
	 * @param exceptions the queue to add any exceptions to
	 */
	public StatusListener(Build build, Queue<Throwable> exceptions)
	{
		assert that(build, "build").isNotNull().elseThrow();
		assert that(exceptions, "exceptions").isNotNull().elseThrow();
		this.build = build;
		this.exceptions = exceptions;
	}

	/**
	 * Starts the listener.
	 *
	 * @return the listener's thread
	 * @throws InterruptedException if the thread is interrupted before the operation is complete
	 */
	public Thread start() throws InterruptedException
	{
		CountDownLatch started = new CountDownLatch(1);

		Thread thread = Thread.startVirtualThread(() ->
		{
			try
			{
				StatusResponse endOfStream = StatusResponse.newBuilder().build();
				Session session = build.getSession();
				QueuingStreamObserver<StatusResponse> observer = new QueuingStreamObserver<>(session.getClient(),
					endOfStream);
				StatusRequest statusRequest = StatusRequest.newBuilder().
					setRef(build.getId()).
					build();
				session.getControl().status(statusRequest, observer);
				started.countDown();

				BlockingQueue<StatusResponse> elements = observer.getElements();
				response.set(elements.take());
				StatusResponse secondResponse = elements.take();
				assert secondResponse == endOfStream : "Expected a single response. Got: " + secondResponse;
				assert elements.isEmpty() : elements;
			}
			catch (InterruptedException e)
			{
				exceptions.add(e);
			}
			finally
			{
				started.countDown();
			}
		});
		started.await();
		return thread;
	}

	/**
	 * Returns the status of the build.
	 *
	 * @return {@code null} if the response has not been received
	 */
	public StatusResponse getResponse()
	{
		return response.get();
	}
}