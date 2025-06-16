/**
 * A buildx client.
 */
module com.github.cowwoc.anchor4j.buildx
{
	requires transitive com.github.cowwoc.anchor4j.core;
	requires com.github.cowwoc.requirements11.annotation;
	requires com.github.cowwoc.requirements11.java;
	requires com.fasterxml.jackson.annotation;
	requires com.github.cowwoc.pouch.core;

	exports com.github.cowwoc.anchor4j.buildx.client;
	exports com.github.cowwoc.anchor4j.buildx.internal.client to com.github.cowwoc.anchor4j.buildx.test;
}