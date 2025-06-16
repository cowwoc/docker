/**
 * Common test code.
 */
module com.github.cowwoc.anchor4j.core.test
{
	requires com.github.cowwoc.anchor4j.core;

	exports com.github.cowwoc.anchor4j.core.test to
		com.github.cowwoc.anchor4j.buildx.test, com.github.cowwoc.anchor4j.docker.test;
}