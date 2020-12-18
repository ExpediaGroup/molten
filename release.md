Release process
===============
To release current master to Maven Central repository, follow these steps.

Assuming the version on master is `1.0.0-SNAPSHOT` and the target release version is `1.0.0`.

1. Update [changelog](CHANGELOG.md) by moving all changes from `[Unreleased]` to a new section => `[1.0.0]`
    - Add additional information if necessary.
    - Push changes to master.
2. Go to [Create a new release](https://github.com/ExpediaGroup/molten/releases/new) page.
3. Set `Tag version` to the desired version to be released prefixed with `v` => `v1.0.0`
4. Set `Release title` to same as `Tag version` => `v1.0.0` 
5. Copy changes from Changelog to `Description`.
    - Add additional information if necessary.
    - Enhance formatting if necessary.
6. Check `This is a pre-release` if necessary.
    - Be sure to keep version in sync with this. e.g. `1.0.0-beta`
7. Click `Publish release` to kick off the release workflow.
    - Be sure to verify it has succeeded.
8. Bump SNAPSHOT version on `master` to next patch version => `1.0.1-SNAPSHOT`
    - `mvn org.codehaus.mojo:versions-maven-plugin:2.8.1:set -DnewVersion=1.0.1-SNAPSHOT`
