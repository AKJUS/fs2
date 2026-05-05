# Install

The latest version for Cats Effect 3 is `@VERSION@`, which supports Cats Effect 3 and is cross built for Scala 2.12, 2.13, and 3.2.

The latest version for Cats Effect 2 is `2.5.10`, which supports Cats Effect 2 and is similarly cross built for various Scala versions.

### Dependencies <!-- {docsify-ignore} -->

```
// available for 2.12, 2.13, 3.2
libraryDependencies += "co.fs2" %% "fs2-core" % "@VERSION@"

// optional I/O library
libraryDependencies += "co.fs2" %% "fs2-io" % "@VERSION@"

// optional reactive streams interop
libraryDependencies += "co.fs2" %% "fs2-reactive-streams" % "@VERSION@"

// optional scodec interop
libraryDependencies += "co.fs2" %% "fs2-scodec" % "@VERSION@"
```

The fs2-core as well as fs2-io and fs2-scodec libraries are also supported on Scala.js:

```
libraryDependencies += "co.fs2" %%% "fs2-core" % "@VERSION@"
libraryDependencies += "co.fs2" %%% "fs2-scodec" % "@VERSION@"

// Node.js only, and requires module support to be enabled
libraryDependencies += "co.fs2" %%% "fs2-io" % "@VERSION@"
scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
```

The fs2-core as well as fs2-io and fs2-scodec libraries are also supported on Scala Native:
```
libraryDependencies += "co.fs2" %%% "fs2-core" % "@VERSION@"
libraryDependencies += "co.fs2" %%% "fs2-scodec" % "@VERSION@"

// TCP support requires https://github.com/armanbilge/epollcat/
// TLS support requires https://github.com/aws/s2n-tls
libraryDependencies += "co.fs2" %%% "fs2-io" % "@VERSION@"
```

Release notes for each release are available on [Github](https://github.com/typelevel/fs2/releases/).

If upgrading from the 2.x series, see the [release notes for 3.0.0](https://github.com/typelevel/fs2/releases/tag/v3.0.0) for help with upgrading.

If upgrading from the 1.x series, see the [release notes for 2.0.0](https://github.com/typelevel/fs2/releases/tag/v2.0.0) for help with upgrading.

There are [detailed migration guides](https://github.com/typelevel/fs2/blob/main/docs/) for migrating from older versions.

### Native

For scala native, fs2 has a dependancy on [s2n](https://github.com/aws/s2n-tls). To be able to link a scala-native fs2 based application, s2n will need to be made available to the linker. If you are observing a linker error in the logs similar to the following on native (commonly, if fs2 is a transitive dependency):

```
[error] ld: library 's2n' not found
```

Then the following steps may help. Making s2n available to the linker is a two step process;

1. Install s2n locally
2. Tell the build tool where to find s2n

By way of an example on mac OS;

```sh
brew install s2n
```
And then, we must tell our build tool, where to find this library;

```scala
nativeLinkingOptions ++= Seq(
  "-L/opt/homebrew/Cellar/s2n/1.7.2"
)
```
Note: this an _example_ specific to macOS intended to be illustrative. Update for _your_ system, path, and version as needed.






