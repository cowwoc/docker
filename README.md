[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.cowwoc.docker/docker/badge.svg)](https://search.maven.org/search?q=g:com.github.cowwoc.docker)
[![build-status](../../workflows/Build/badge.svg)](../../actions?query=workflow%3Abuild)

# <img src="docs/logo.svg" width=64 height=64 alt="logo"> Docker Java Client

[![API](https://img.shields.io/badge/api_docs-5B45D5.svg)](https://cowwoc.github.io/docker/0.1/docs/api/)
[![Changelog](https://img.shields.io/badge/changelog-A345D5.svg)](docs/changelog.md)

A Java client for [Docker](https://www.docker.com/).

To get started, add this Maven dependency:

```xml

<dependency>
	<groupId>com.github.cowwoc.docker</groupId>
	<artifactId>docker</artifactId>
	<version>0.1</version>
</dependency>
```

## Example

```java
import com.github.cowwoc.docker.client.DockerClient;
import com.github.cowwoc.docker.exception.ImageNotFoundException;
import com.github.cowwoc.docker.resource.Image;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

class Example
{
	public static void main(String[] args)
		throws IOException, TimeoutException, InterruptedException, ImageNotFoundException
	{
		try (DockerClient client = DockerClient.usingUnixSocket(Path.of("/var/run/docker.sock")))
		{
			Image image = Image.builder(client).platform("linux/amd64").build();
			Image image2 = Image.getById(client, image.getId());
			assert (image2.equals(image));

			image.tag("rocket-ship", "local-tag");
			Image.pusher(client, "rocket-ship", "remote-tag").
				credentials("username", "Pa33word").
				push();
		}
	}
}
```

## Getting Started

See the [API documentation](https://cowwoc.github.io/docker/0.1/docs/api/) for more details.

## Licenses

* This library is licensed under the [Apache License, Version 2.0](LICENSE)
* See [Third party licenses](LICENSE-3RD-PARTY.md) for the licenses of the dependencies