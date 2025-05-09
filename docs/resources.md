Helpful resources
===

* Building Docker from source: https://github.com/moby/moby/issues/43304
* When opening up Docker or BuildKit in IntelliJ or GoLand, follow the instructions in
  `docs/contributing/setup-up-ide.md`
* To run docker server in debug mode, run:

```shell
#!/bin/bash

BASE_PATH=$(dirname $(realpath "$0"))
dlv exec ${BASE_PATH}/bundles/binary/dockerd --headless --listen=127.0.0.1:2345 --api-version=2 --accept-multiclient --check-go-version=false --only-same-user=false -- --debug --host tcp://127.0.0.1:2375 --userland-proxy=false $@
```