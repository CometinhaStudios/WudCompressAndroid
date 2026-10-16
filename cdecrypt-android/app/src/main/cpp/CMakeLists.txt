cmake_minimum_required(VERSION 3.22.1)
project(cdecrypt_android C)

include(FetchContent)
FetchContent_Declare(cdecrypt_orig
    GIT_REPOSITORY https://github.com/VitaSmith/cdecrypt.git
    GIT_TAG master
    GIT_SHALLOW TRUE)
FetchContent_MakeAvailable(cdecrypt_orig)

add_library(cdecrypt_jni SHARED
    bridge.c
    ${cdecrypt_orig_SOURCE_DIR}/cdecrypt.c
    ${cdecrypt_orig_SOURCE_DIR}/util.c
    ${cdecrypt_orig_SOURCE_DIR}/aes.c
    ${cdecrypt_orig_SOURCE_DIR}/sha1.c)
target_include_directories(cdecrypt_jni PRIVATE ${cdecrypt_orig_SOURCE_DIR})
target_compile_definitions(cdecrypt_jni PRIVATE _GNU_SOURCE AES_ROM_TABLES)
target_compile_options(cdecrypt_jni PRIVATE -Wno-unknown-pragmas -Wno-multichar -Wno-sequence-point)
find_library(log-lib log)
target_link_libraries(cdecrypt_jni ${log-lib})
