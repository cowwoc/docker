[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.cowwoc.anchor4j/anchor4j/badge.svg)](https://search.maven.org/search?q=g:com.github.cowwoc.anchor4j)
[![build-status](https://github.com/cowwoc/anchor4j/actions/workflows/Build/badge.svg)](https://github.com/cowwoc/anchor4j/actions/?query=workflow%3Abuild)

# <img src="docs/logo.svg" width=64 height=64 alt="logo"> Anchor4j

[![API](https://img.shields.io/badge/api_docs-5B45D5.svg)](https://cowwoc.github.io/anchor4j/0.10/)
[![Changelog](https://img.shields.io/badge/changelog-A345D5.svg)](docs/changelog.md)

A modern Java client for [Docker](https://www.docker.com/): fluent API, test-friendly, and Kubernetes-ready (no Docker required).

✅ A fluent, strongly typed API.<br>
✅ Built-in support for image build, pull and run with and without Docker Engine.<br>
✅ Easy integration with CI and tests.<br>
✅ First-class support for capturing stdout/stderr and process exit status.<br>

To get started, add this Maven dependency:

```xml

<dependency>
	<groupId>com.github.cowwoc.anchor4j</groupId>
	<artifactId>anchor4j</artifactId>
	<version>0.10</version>
</dependency>
```

## Example

```java
import com.github.cowwoc.anchor4j.docker.client.Docker;

import java.io.IOException;

class Example
{
	public static void main(String[] args)
		throws IOException, InterruptedException
	{
		Docker docker = Docker.connect();

		String imageId = docker.buildImage().
			platform("linux/amd64").
			export(Exporter.dockerImage().build()).
			build(".");

		docker.tagImage(id, "rocket-ship");
		docker.pushImage("rocket-ship").push();
	}
}
```

## Getting Started

See the [API documentation](https://cowwoc.github.io/docker/0.10/) for more details.

## 💖 Support Ongoing Development 💖

If you find this project helpful, please consider [sponsoring me](https://github.com/sponsors/cowwoc).
Maintaining quality open-source software takes significant time and effort—especially while balancing family
life with young children. Your support helps make this work sustainable.

## Missing Features?

The `anchor4j` API covers a wide range of functionality, and since my time is limited, new features are added
based on user requests. If there's a property or capability you'd like to see added,
please [open a new issue](issues/new).

## Licensing

The `core` and `docker` modules are dual-licensed:

- ✅ [ModernJDK License](docs/modern-jdk-license.md) (free for personal use or small businesses):
  - You may use, modify, and redistribute this software only when:
    - It is compiled for and executed on the latest generally available (GA) Java Development Kit (JDK)
      version at the time of deployment.
    - You are an individual or a company with 10 or fewer employees.
    - You do not rebrand or white-label the software.
  - You are not required to update existing deployments when a newer JDK is released.

- 💼 [Commercial License](docs/commercial-license.md):
  - Required for:
    - Compiling, running, or distributing the software for older (non-GA) JDK versions.
    - Use by companies with more than 10 employees.
    - White-labeling or rebranding the library in any form.
  - Pricing:
    - Standard pricing is $10 USD per user per month.
    - This covers use of the software with older JDK versions.
    - White-labeling/rebranding requires a separate agreement and is not covered by the standard pricing.

The `buildx` module is only available under the commercial license.

## Dependencies

* See [Third party licenses](LICENSE-3RD-PARTY.md) for the licenses of the dependencies.