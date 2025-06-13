/**
 * Common code.
 */
module com.github.cowwoc.anchor4j.core
{
	requires org.slf4j;
	requires com.github.cowwoc.requirements11.java;
	requires com.fasterxml.jackson.databind;
	requires java.naming;

	exports com.github.cowwoc.anchor4j.core.client;
	exports com.github.cowwoc.anchor4j.core.resource;

	exports com.github.cowwoc.anchor4j.core.internal.client to
		com.github.cowwoc.anchor4j.buildx, com.github.cowwoc.anchor4j.docker,
		com.github.cowwoc.anchor4j.docker.test, com.github.cowwoc.anchor4j.buildx.test;
	exports com.github.cowwoc.anchor4j.core.internal.util to
		com.github.cowwoc.anchor4j.buildx, com.github.cowwoc.anchor4j.docker,
		com.github.cowwoc.anchor4j.docker.test, com.github.cowwoc.anchor4j.buildx.test;
	exports com.github.cowwoc.anchor4j.core.internal.resource to
		com.github.cowwoc.anchor4j.buildx, com.github.cowwoc.anchor4j.docker;
}