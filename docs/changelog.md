Minor updates involving cosmetic changes have been omitted from this list.

See https://github.com/cowwoc/docker/commits/main for a full list.

## Version 0.8 - 2025/02/19

* Replaced support for the legacy builder with BuildKit.
* Bugfix: `ImageBuilder` was not including files recursively when parsing the `ADD` or `COPY` commands.
* Bugfix: `ImagePuller.pull()` was always returning `null`.
* Bugfix: `Container.getById()` was throwing an exception if no match was found instead of returning `null`.
* Replaced `Node.getByName()` with `Node.getById()`.
* Replaced `Container.getByName()` with `Container.getById()`.
* Replaced `Swarm.getNodeById()` and `Swarm.getNodeByName()` with `Node.getById()`.
* Replaced `ContainerNotFoundException` and `ImageNotFoundException` with `ResourceNotFoundException`.
* Renamed `Image.getNameToTag()` to `Image.getNameToTags()`.
* Replaced non-static method `Image.pusher(client, name, tag)` with `Image.pusher(name)` where `name` may
  include a tag. The image already has an associated client, so there is no need to pass it a second time.
* Moved `Container.LogStreams` to `ContainerLogs.Streams`.
* Added `LogStreams.getExceptions()`.
* Added `Network`.
* Added support for Dockerfile commands that span multiple lines such as `ENTRYPOINT []`.

## Version 0.6 - 2025/01/17

* `Image.getById` and `ImagePuller.pull()` now return `null` if the image was not found instead of throwing
  `ImageNotFoundException.`
* Added `ImageBuilder.cacheFrom()`.
* Added `Server.getUri()`.
* Bugfixes
  * `InternalClient` was unintentionally exposed to end-users.
  * `ContainerCreator.environmentVariable()` was always failing.

## Version 0.5 - 2025/01/16

* Added `Image.puller()` for pulling images.
* Converted `Image.pusher()` from static to member method.
* Renamed `ImageBuilder.dockerFile()` to `dockerfile()`.
* Added `Container` for creating and running containers.
* Automatically exclude files from the build context based on the contents of `Dockerfile` and `.dockerignore`
  files.
* `Image.getById()` and `Image.tag()` now throw `ImageNotFoundException` instead of `FileNotFoundException`
  when an image is not found.
* Replaced `Image.getByDigest()` with `Image.getByPredicate()`.
* `ImagePusher` no longer sends `platform` if it's not set.
* Bugfixes
  * `Image.getById()`, `ImagePusher.push()` now handle image names that contain a slash (e.g. `alpine/helm`).
  * Looking up images was always failing because the code was looking for JSON property `id` instead of `Id`.

## Version 0.4 - 2025/01/01

* Bugfixes
  * `ImageBuilder.build()` was calculating the wrong relative path for Dockerfile.
  * `ImagePusher.credentials()` was always throwing an exception due to a typo.

## Version 0.3 - 2025/01/01

* Bugfix: `ImageBuilder.dockerFile` was using the wrong default value.

## Version 0.2 - 2025/01/01

* Renamed `Config.list()` to `Config.getAll()` and `Image.list()` to `Image.getAll()`.

## Version 0.1 - 2025/01/01

* Initial release.