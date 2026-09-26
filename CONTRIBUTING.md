# Contributing

This is a personal tablet-focused build shared for others to inspect and use.
There is no promised maintenance schedule, device coverage, or response time
for issues and contributions.

Describe the problem, expected behavior, and reproduction steps before making
large changes. Keep changes focused and preserve upstream copyright notices.

For launcher changes, use the Gradle project under `payload/`. For engine or
packaging changes, use `bash build.sh` from the repository root. A successful
launcher build alone does not verify a complete OpenMW APK.

Include the validation performed with each contribution. Controller archive
and storage migration tests are under `payload/app/src/test`; Android-specific
tests are under `payload/app/src/androidTest`. Native and device behavior
should be checked on a suitable ARM64 Android device.

Do not commit signing keys, API credentials, local SDK paths, generated build
outputs, game data, downloaded mods, saves, or personal configuration. Review
the staged diff, including newly added binary files, before committing.

Sanitize logs before attaching them to issues. Report credential exposure
without posting the credential itself. Add the origin and applicable notices
when introducing any third-party code, artwork, or prebuilt library.
