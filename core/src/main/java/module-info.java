/**
 * A Java client for Docker.
 */
module com.github.cowwoc.docker.core
{
	requires com.github.cowwoc.docker.generated;
	requires org.eclipse.jetty.client;
	requires org.apache.commons.compress;
	requires org.apache.sshd.osgi;
	requires com.github.cowwoc.requirements10.jackson;
	requires org.apache.commons.codec;
	requires org.apache.commons.lang3;
	requires com.github.cowwoc.pouch.core;
	requires com.fasterxml.jackson.datatype.jsr310;
	requires com.fasterxml.jackson.databind;
	requires org.threeten.extra;
	requires com.google.protobuf;
	requires io.netty.transport;
	requires io.netty.codec;
	requires io.netty.codec.http;
	requires io.netty.codec.http2;
	requires io.netty.handler;
	requires io.netty.common;
	requires io.netty.buffer;
	requires io.netty.transport.unix.common;
	requires io.grpc;
	requires io.grpc.netty;
	requires io.grpc.stub;
	requires io.grpc.services;
	requires proto.google.common.protos;

	exports com.github.cowwoc.docker.client;
	exports com.github.cowwoc.docker.exception;
	exports com.github.cowwoc.docker.resource;
	// Used by tests
	exports com.github.cowwoc.docker.internal.buildkit to com.github.cowwoc.docker.test;
	exports com.github.cowwoc.docker.internal.client to com.github.cowwoc.docker.test;
	exports com.github.cowwoc.docker.internal.util to com.github.cowwoc.docker.test;
}