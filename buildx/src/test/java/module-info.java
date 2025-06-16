module com.github.cowwoc.anchor4j.buildx.test
{
	requires com.github.cowwoc.anchor4j.core.test;
	requires com.github.cowwoc.anchor4j.buildx;
	requires org.slf4j;
	requires ch.qos.logback.core;
	requires ch.qos.logback.classic;
	requires com.github.cowwoc.requirements11.java;
	requires com.github.cowwoc.pouch.core;
	requires org.apache.commons.compress;
	requires org.bouncycastle.pkix;
	requires org.bouncycastle.provider;
	requires com.fasterxml.jackson.databind;
	requires org.testng;

	opens com.github.cowwoc.anchor4j.buildx.test.resource to org.testng;
}