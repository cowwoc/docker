package com.github.cowwoc.anchor4j.docker.test.resource;

import com.github.cowwoc.anchor4j.docker.client.Docker;
import com.github.cowwoc.anchor4j.docker.resource.ContextElement;
import com.github.cowwoc.anchor4j.docker.test.IntegrationTestContainer;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static com.github.cowwoc.requirements11.java.DefaultJavaValidators.requireThat;

public final class ContextIT
{
	@Test
	public void getClientContext() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		String clientContext = client.getClientContext();
		requireThat(clientContext, "clientContext").isEqualTo(it.getName());
		it.onSuccess();
	}

	@Test
	public void listContexts() throws IOException, InterruptedException, TimeoutException
	{
		IntegrationTestContainer it = new IntegrationTestContainer();
		Docker client = it.getClient();
		List<ContextElement> contexts = client.listContexts();
		boolean matchFound = false;
		for (ContextElement context : contexts)
		{
			if (context.name().equals(it.getName()))
			{
				matchFound = true;
				break;
			}
		}
		requireThat(matchFound, "matchFound").withContext(contexts, "contexts").isTrue();
		it.onSuccess();
	}
}