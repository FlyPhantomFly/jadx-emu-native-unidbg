# Changes

## 0.1.1

Fixed the build. The tests need smali, which is hosted on Google's Maven repository, and the build file didn't list it, so `./gradlew test` and `scripts/deploy.sh` stopped at compiling the tests. No code changed.

## 0.1.0

The first version, as its own repo. It replaces the `native-unidbg` module that lived inside the unpacker, which could never be loaded: jadx-emu gives every extension jar its own class loader, so the unpacker couldn't see a backend in a second jar. This one registers through the engine's native backend registry instead (jadx-emu 0.3.0).

It also fixes things the old module got wrong. It loads only the libraries you name and runs only the methods you name. A native that returns null gives null, where before it was treated as unhandled. An unknown argument is never guessed at. Wrong settings and missing libraries are refused with a message that says what the apk does have.
