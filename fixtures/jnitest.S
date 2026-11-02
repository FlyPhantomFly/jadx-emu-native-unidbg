// Test library for jadx-emu-native-unidbg. Class jnitest/Native, nothing here is from a real app.
//
//   static native int     triple(int)           x * 3
//   static native boolean isEven(int)
//   static native long    mulLong(long, long)
//   static native String  name(int)             a tiny string table; entries are stored xor 0x5a and
//                                               decrypted at call time, the shape of a native decryptor
//   static native int     length(byte[])        GetArrayLength
//   static native byte[]  same(byte[])          returns its argument
//   native int            bump(int)             instance method, x + 100
//   static native void    touch()               does nothing; for the void path
//
// Build: scripts/build-fixture.sh (needs aarch64-linux-gnu binutils). The result is checked in as
// src/test/resources/libjnitest.so so the tests don't need a toolchain.

    .text
    .align 2

    .global JNI_OnLoad
    .type JNI_OnLoad, %function
JNI_OnLoad:
    mov     w0, #6
    movk    w0, #1, lsl #16            // JNI_VERSION_1_6
    ret

    .global Java_jnitest_Native_triple
    .type Java_jnitest_Native_triple, %function
Java_jnitest_Native_triple:
    add     w0, w2, w2, lsl #1
    ret

    .global Java_jnitest_Native_isEven
    .type Java_jnitest_Native_isEven, %function
Java_jnitest_Native_isEven:
    and     w0, w2, #1
    eor     w0, w0, #1
    ret

    .global Java_jnitest_Native_mulLong
    .type Java_jnitest_Native_mulLong, %function
Java_jnitest_Native_mulLong:
    mul     x0, x2, x3
    ret

    .global Java_jnitest_Native_bump
    .type Java_jnitest_Native_bump, %function
Java_jnitest_Native_bump:
    add     w0, w2, #100
    ret

    .global Java_jnitest_Native_touch
    .type Java_jnitest_Native_touch, %function
Java_jnitest_Native_touch:
    ret

    .global Java_jnitest_Native_same
    .type Java_jnitest_Native_same, %function
Java_jnitest_Native_same:
    mov     x0, x2
    ret

    .global Java_jnitest_Native_length
    .type Java_jnitest_Native_length, %function
Java_jnitest_Native_length:
    ldr     x8, [x0]
    ldr     x8, [x8, #0x558]           // GetArrayLength
    mov     x1, x2
    br      x8

    .global Java_jnitest_Native_name
    .type Java_jnitest_Native_name, %function
Java_jnitest_Native_name:
    cmp     w2, #2
    b.hi    2f                          // index out of range -> null
    stp     x29, x30, [sp, #-48]!
    mov     x29, sp
    adr     x4, enc_table
    add     x4, x4, w2, uxtw #4         // &entry[idx], 16 bytes each
    add     x5, sp, #16                 // 16-byte stack buffer
    mov     x6, #0
    mov     w9, #0x5a                   // the key (not encodable as an eor immediate)
1:  ldrb    w7, [x4, x6]
    eor     w7, w7, w9
    strb    w7, [x5, x6]
    add     x6, x6, #1
    cmp     x6, #16
    b.lo    1b
    mov     x1, x5
    ldr     x8, [x0]
    ldr     x8, [x8, #0x538]           // NewStringUTF
    blr     x8
    ldp     x29, x30, [sp], #48
    ret
2:  mov     x0, #0
    ret

    .align 4
enc_table:
    .byte 0x3b, 0x36, 0x2a, 0x32, 0x3b, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a    // "alpha" xor 0x5a
    .byte 0x38, 0x28, 0x3b, 0x2c, 0x35, 0x77, 0x31, 0x3f, 0x23, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a    // "bravo-key" xor 0x5a
    .byte 0x12, 0x3f, 0x36, 0x36, 0x35, 0x76, 0x7a, 0x2f, 0x34, 0x33, 0x3e, 0x38, 0x3d, 0x7b, 0x5a, 0x5a    // "Hello, unidbg!" xor 0x5a
