# CI dependency bootstrap

The NeoForge 1.21.1 build depends on Universal artifacts that cannot be
reliably reproduced from the public `0.1.0` Maven release alone. The test
workflow therefore builds the dependencies from pinned source commits and
publishes them to the GitHub runner's local Maven repository.

- Universal Gradle plugin: `f6d8cf1242ebeb9eb68ed840ecc508496a4f2a8e`
- Universal API: `525e5b580b73bf516167695ba5b7db272990c440`

`universal-gradle-plugin.patch` fixes creation checks for the target shadow
and transform tasks. `universal-api.patch` pins the NeoGradle userdev plugin
instead of resolving a moving `7.0.+` version. The replacement Universal API
settings file includes only the common API and the dependencies needed to
publish the NeoForge v3955 artifact used by Minecraft 1.21.1.

When either source commit changes, update its workflow environment value. Each
dependency has an independent cache whose key includes its commit and relevant
patch or settings files.
