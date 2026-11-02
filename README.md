# jadx-emu-native-unidbg

A native backend for [jadx-emu](https://github.com/FlyPhantomFly/jadx-emu). It runs an app's JNI methods in [unidbg](https://github.com/zhkl0228/unidbg), an Android emulator for native code, so other extensions can get answers out of a .so, for example a string table that only decrypts in native code.

On its own it does nothing. It registers itself as the backend `unidbg`, and an extension asks for it when it needs native code run.

## Install

You need the jadx-emu 0.3.0 engine installed and published to `~/.m2`. See its README.

```
./scripts/deploy.sh
```

That runs the tests, builds the jar and puts it in the jadx-emu extensions folder. The jar is around 27 MB because unidbg and its libraries are bundled, and jadx doesn't have them. Restart jadx. The log should show `registered native backend 'unidbg'`.

## Use it from an extension

```kotlin
val backend = ctx.emu.nativeBackend("unidbg")
val bridge = backend?.open(NativeRequest(apk, setOf("Lcom/foo/Bar;->decrypt"), listOf("libfoo.so")))
val vm = ctx.emu.engine.newVm(nativeBridge = bridge)
```

Look the backend up when you use it, not in `init`. `apk` is the file the library comes from. For a split app that's the arm64 split, `split_config.arm64_v8a.apk`, and not `base.apk`. List the main library first, then any others from the same apk that it needs.

Only the methods you name run. Every other native method behaves as if there were no backend. Settings go in `NativeRequest.options`: `sdk` is the Android SDK level (default 23), `verbose` turns on unidbg's own tracing, and `jni-onload=false` skips a library's `JNI_OnLoad`.

## Try a library on its own

```
./gradlew run --args="libfoo.so com/foo/Bar decrypt (I)Ljava/lang/String; i:7"
```

Arguments after the signature are typed: `i:7` int, `j:7` long, `z:true` boolean, `s:text` String, `b:00ff` byte[]. Put `--instance` first for a non-static method. `UNIDBG_VERBOSE=1` turns on unidbg's tracing.

## What it handles

Arguments of type int, short, byte, char, boolean, long, String and byte[], and results of those types or void, for static and instance methods. arm64-v8a is what's tested. armeabi-v7a is accepted but hasn't been run.

## What it doesn't

- float and double, other arrays and other object types are refused, so that call stays unknown.
- Native code that calls back into Java isn't supported yet.
- A library whose code is packed on disk has nothing to run until it's unpacked. Brawl Stars' `libg.so` is one: its code segment isn't real code until the app loads it.
- A library that detects emulation may behave differently under unidbg.
- unidbg 0.9.9 hasn't been tried. It needs `UNIDBG_VERSION=0.9.9 UNIDBG_UNICORN2=true` when you build.

## Tests

`./gradlew test` runs real ARM64 code. `fixtures/jnitest.S` is a small test library, including a string table that decrypts at run time, and it's built into `src/test/resources/libjnitest.so`. You only need `scripts/build-fixture.sh` if you change the assembly, and it needs the aarch64 GNU binutils.

## License

GPLv2, because it links the GPLv2 jadx-emu engine. See `LICENSE` and `NOTICE`. The built jar bundles unidbg, which is Apache-2.0.
