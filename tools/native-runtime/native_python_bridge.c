/* JNI entry point for an app-prefix CPython 3.14 runtime on Android ARM64. */
#include <jni.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef void (*py_initialize_fn)(void);
typedef int (*py_is_initialized_fn)(void);
typedef int (*py_run_simple_string_fn)(const char *, void *);

static void *python_library;
static py_run_simple_string_fn py_run_simple_string;
static pthread_t owner_thread;
static int initialized;

static jstring error_string(JNIEnv *env, const char *message) {
    return (*env)->NewStringUTF(env, message ? message : "Unknown Python error");
}

JNIEXPORT jstring JNICALL
Java_com_example_pocketpythonide_NativePythonBridge_initialize(
        JNIEnv *env, jclass klass, jstring home_java) {
    (void) klass;
    if (initialized) {
        return NULL;
    }
    const char *home = (*env)->GetStringUTFChars(env, home_java, NULL);
    if (!home) {
        return error_string(env, "Cannot read Python home");
    }
    size_t size = strlen(home) + 128;
    char *library_path = malloc(size);
    char *module_path = malloc(size * 2);
    if (!library_path || !module_path) {
        free(library_path);
        free(module_path);
        (*env)->ReleaseStringUTFChars(env, home_java, home);
        return error_string(env, "Out of memory preparing Python paths");
    }
    snprintf(library_path, size, "%s/lib/libpython3.14.so", home);
    snprintf(module_path, size * 2, "%s/lib/python3.14:%s/lib/python3.14/site-packages",
             home, home);
    if (setenv("PYTHONHOME", home, 1) || setenv("PYTHONPATH", module_path, 1)) {
        free(library_path);
        free(module_path);
        (*env)->ReleaseStringUTFChars(env, home_java, home);
        return error_string(env, "Cannot set Python paths");
    }
    python_library = dlopen(library_path, RTLD_NOW | RTLD_GLOBAL);
    free(library_path);
    free(module_path);
    (*env)->ReleaseStringUTFChars(env, home_java, home);
    if (!python_library) {
        return error_string(env, dlerror());
    }
    py_initialize_fn py_initialize =
            (py_initialize_fn) dlsym(python_library, "Py_Initialize");
    py_is_initialized_fn py_is_initialized =
            (py_is_initialized_fn) dlsym(python_library, "Py_IsInitialized");
    py_run_simple_string =
            (py_run_simple_string_fn) dlsym(python_library, "PyRun_SimpleStringFlags");
    if (!py_initialize || !py_is_initialized || !py_run_simple_string) {
        return error_string(env, "CPython 3.14 API symbols are unavailable");
    }
    py_initialize();
    if (!py_is_initialized()) {
        return error_string(env, "CPython initialization failed");
    }
    owner_thread = pthread_self();
    initialized = 1;
    return NULL;
}

JNIEXPORT jint JNICALL
Java_com_example_pocketpythonide_NativePythonBridge_run(
        JNIEnv *env, jclass klass, jbyteArray code_java) {
    (void) klass;
    if (!initialized || !pthread_equal(owner_thread, pthread_self())) {
        return -1;
    }
    jsize length = (*env)->GetArrayLength(env, code_java);
    char *code = malloc((size_t) length + 1);
    if (!code) {
        return -1;
    }
    (*env)->GetByteArrayRegion(env, code_java, 0, length, (jbyte *) code);
    if ((*env)->ExceptionCheck(env)) {
        free(code);
        return -1;
    }
    code[length] = '\0';
    int result = py_run_simple_string(code, NULL);
    free(code);
    return result;
}
