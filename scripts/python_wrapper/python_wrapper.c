/**
 * Python Wrapper for Android
 * 
 * This small C program wraps libpython3.14.so to create a standalone
 * python executable that can be executed on Android.
 * 
 * The official Python.org Android embedded package only contains
 * libpython.so (a shared library), which cannot be executed directly.
 * This wrapper calls Py_BytesMain() to provide the main() entry point.
 * 
 * Build with Android NDK:
 *   aarch64-linux-android28-clang -o python3 python_wrapper.c \
 *       -I${PYTHON_INCLUDE} -L${PYTHON_LIB} -lpython3.14 -pie
 */

#define PY_SSIZE_T_CLEAN
#include <Python.h>

int main(int argc, char *argv[]) {
    return Py_BytesMain(argc, argv);
}
