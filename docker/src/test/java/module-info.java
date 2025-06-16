module com.github.cowwoc.anchor4j.docker.test
{
	requires com.github.cowwoc.anchor4j.core.test;
	requires com.github.cowwoc.anchor4j.docker;
	requires com.github.cowwoc.requirements11.java;
	requires org.slf4j;
	requires ch.qos.logback.core;
	requires ch.qos.logback.classic;
	requires com.github.cowwoc.pouch.core;
	requires org.apache.commons.compress;
	requires org.bouncycastle.pkix;
	requires org.bouncycastle.provider;
	requires org.testng;

	opens com.github.cowwoc.anchor4j.docker.test.resource to org.testng;
	opens com.github.cowwoc.anchor4j.docker.test to org.testng;
}