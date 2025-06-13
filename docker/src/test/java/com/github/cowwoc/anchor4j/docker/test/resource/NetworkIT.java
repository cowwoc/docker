package com.github.cowwoc.anchor4j.docker.test.resource;

import com.github.cowwoc.anchor4j.docker.client.Docker;
import com.github.cowwoc.anchor4j.docker.test.IntegrationTestContainer;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public final class NetworkIT
{
	@Test
	public void get() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker docker = it.getClient();
		docker.getNetwork("default");
		it.onSuccess();
	}
}