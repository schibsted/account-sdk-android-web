# Releasing the SDK

1. Once all changes to be included in the new release has been merged,
   [create a new release via Github](https://github.com/schibsted/account-sdk-android-web/releases/new).
3. Enter the following fields:
    * Tag: The version you are releasing, following semantic versioning, example "2.5.17".
    * Release title: Should be the same as the git tag, example "2.5.17".
    * Description: Click the "Generate release notes" button
      and edit the generated text for readibility.

The release will be published via Github Actions as soon as the workflow for the tag completes.
