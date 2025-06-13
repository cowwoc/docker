/**
 * A Docker client.
 */
module com.github.cowwoc.anchor4j.docker
{
	requires transitive com.github.cowwoc.anchor4j.core;
	requires com.github.cowwoc.requirements11.jackson;
	requires com.github.cowwoc.pouch.core;
	requires com.fasterxml.jackson.databind;
	requires org.slf4j;

	exports com.github.cowwoc.anchor4j.docker.client;
	exports com.github.cowwoc.anchor4j.docker.exception;
	exports com.github.cowwoc.anchor4j.docker.resource;
	// Needed by unit tests
	exports com.github.cowwoc.anchor4j.docker.internal.util to com.github.cowwoc.anchor4j.docker.test;
	exports com.github.cowwoc.anchor4j.docker.internal.client to com.github.cowwoc.anchor4j.docker.test;
	exports com.github.cowwoc.anchor4j.docker.internal.resource to com.github.cowwoc.anchor4j.docker.test;
}