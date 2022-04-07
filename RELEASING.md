# Releasing the SDK

1. Update `CHANGELOG.md` to reflect the changes done since the last release.
1. Create a PR to master with the name "RELEASE <major.minor.patch>"
1. Once the PR has been merged, head over to the [releases page](https://github.com/schibsted/account-sdk-android-web/releases) and create a new release.
1. Enter all the following fields before publishing the release:
    * Tag version: The version you are releasing, following semantic versioning, example "2.5.17".
    * Release title: Should be the same as the version number, example "2.5.17".
    * Description: A list of changes since the last release, same as written in the changelog.

The release will be published via Travis as soon as the build for the tag completes.
