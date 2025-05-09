/**
 * Generated code.
 */
module com.github.cowwoc.docker.generated
{
	requires transitive com.google.protobuf;
	requires transitive proto.google.common.protos;
	requires transitive com.google.common;
	requires transitive io.grpc;
	requires transitive io.grpc.stub;
	requires io.grpc.protobuf;

	exports com.github.moby.buildkit.pb;
	exports com.github.moby.filesync.v1;
	exports com.github.moby.buildkit.v1.frontend;
	exports com.github.moby.buildkit.v1.sourcepolicy;
	exports com.github.moby.buildkit.v1.apicaps;
	exports com.github.moby.buildkit.v1.types;
	exports com.github.tonistiigi.fsutil.types;
	// Needed by BuildKit's toString() implementation
	exports com.github.moby.buildkit.v1 to com.google.protobuf, com.github.cowwoc.docker, com.github.cowwoc.docker.core;
	exports com.google.protobuf.generated to com.google.protobuf;
}