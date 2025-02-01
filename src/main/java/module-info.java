/**
 * A Java client for Docker.
 */
module com.github.cowwoc.docker
{
	requires transitive org.eclipse.jetty.client;
	requires org.apache.commons.compress;
	requires org.apache.sshd.osgi;
	requires com.github.cowwoc.requirements10.jackson;
	requires org.apache.commons.codec;
	requires org.apache.commons.lang3;
	requires com.github.cowwoc.pouch.core;
	requires com.fasterxml.jackson.datatype.jsr310;
	requires com.fasterxml.jackson.databind;
	requires jdk.jfr;

	exports com.github.cowwoc.docker.client;
	exports com.github.cowwoc.docker.exception;
	exports com.github.cowwoc.docker.resource;
	exports com.github.cowwoc.docker.internal.util to com.github.cowwoc.docker.test;
}