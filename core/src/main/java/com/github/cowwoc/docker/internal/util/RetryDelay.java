package com.github.cowwoc.docker.internal.util;

import org.threeten.extra.Temporals;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.that;

/**
 * Generates a sequence of durations to wait before retrying a failed operation. This implementation uses the
 * truncated exponential backoff algorithm.
 */
public final class RetryDelay
{
	/**
	 * The initial delay.
	 */
	private final Duration initial;
	/**
	 * The maximum delay.
	 */
	private final Duration maximum;
	/**
	 * The multiplier that is applied to a value when advancing to the next value in the sequence.
	 */
	private final float multiplier;
	/**
	 * The current delay.
	 */
	private Duration delay;

	/**
	 * @param initial    the initial delay to sleep
	 * @param maximum    the maximum delay to sleep
	 * @param multiplier the multiplier to apply to the current delay after each retry
	 * @throws NullPointerException     if {@code initial} or {@code timeLimit} are null
	 * @throws IllegalArgumentException if {@code initial} is negative. If {@code maximum} is less than
	 *                                  {@code initial}. If {@code multiplier} is less than {@code 1.0}.
	 */
	public RetryDelay(Duration initial, Duration maximum, float multiplier)
	{
		requireThat(initial, "initial").isNotNull();
		requireThat(!initial.isNegative(), "!initial.isNegative()").isTrue();
		requireThat(maximum, "maximum").isGreaterThanOrEqualTo(initial, "initial");
		requireThat(multiplier, "multiplier").isGreaterThanOrEqualTo(1.0f);

		this.initial = initial;
		this.maximum = maximum;
		this.multiplier = multiplier;
		this.delay = initial;
	}

	/**
	 * Resets the delay to its initial value.
	 */
	public void reset()
	{
		this.delay = initial;
	}

	/**
	 * Sleeps until it is time to retry an operation.
	 *
	 * @param timeLeft the maximum duration that the thread may sleep
	 * @throws NullPointerException if {@code timeLeft} is null
	 * @throws InterruptedException if the thread is interrupted
	 */
	public void sleep(Duration timeLeft) throws InterruptedException
	{
		requireThat(timeLeft, "timeLeft").isNotNull();
		assert that(delay, "delay").isLessThanOrEqualTo(maximum, "maximum").elseThrow();

		Duration newDelay = Temporals.multiply(delay, multiplier);
		ThreadLocalRandom random = ThreadLocalRandom.current();
		newDelay = Duration.ofSeconds(
			random.nextLong((long) (newDelay.toSeconds() * 0.9), newDelay.getSeconds() + 1),
			random.nextInt((int) Math.round(newDelay.getNano() * 0.9), newDelay.getNano() + 1));

		Duration oldDelay = delay;
		delay = Collections.min(List.of(newDelay, maximum));

		Duration duration = Collections.min(List.of(timeLeft, oldDelay));
		Thread.sleep(duration);
	}

	/**
	 * Sleeps until it is time to retry an operation.
	 *
	 * @throws InterruptedException if the thread is interrupted
	 */
	public void sleep() throws InterruptedException
	{
		sleep(maximum);
	}

	/**
	 * Returns the maximum duration for which the next invocation of {@link #sleep(Duration)} will sleep.
	 *
	 * @param timeLeft the maximum duration that the thread may sleep
	 * @return the maximum duration that the next invocation of {@link #sleep(Duration)} will sleep
	 */
	public Duration getNextDelay(Duration timeLeft)
	{
		return Collections.min(List.of(timeLeft, delay));
	}
}