module com.github.cowwoc.docker.test
{
	requires com.github.cowwoc.docker;
	requires org.testng;
	requires com.github.cowwoc.requirements10.java;

	opens com.github.cowwoc.docker.test.internal.util to org.testng;
}