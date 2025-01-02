Minor updates involving cosmetic changes have been omitted from this list.

See https://github.com/cowwoc/docker/commits/main for a full list.

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