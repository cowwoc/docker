package com.github.cowwoc.anchor4j.core.resource;

/**
 * Represents a blocking operation that waits for a monitored process to terminate.
 */
@FunctionalInterface
public interface WaitFor
{
	/**
	 * Waits for the monitored process has terminated. If the process has already terminated, this method
	 * returns immediately. Otherwise, it blocks the calling thread until termination occurs.
	 *
	 * @return the exit code of the process
	 * @throws InterruptedException if the thread is interrupted before the monitored process has terminated
	 */
	int apply() throws InterruptedException;
}