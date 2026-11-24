#define _GNU_SOURCE
#include <android/dlext.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <linux/memfd.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

static int make_library_fd(void) {
    const unsigned int flags = MFD_CLOEXEC | MFD_ALLOW_SEALING;
#ifdef MFD_EXEC
    int fd = (int) syscall(SYS_memfd_create, "extera-analyzer", flags | MFD_EXEC);
    if (fd >= 0 || errno != EINVAL) {
        return fd;
    }
#endif
    return (int) syscall(SYS_memfd_create, "extera-analyzer", flags);
}

static int is_analyzer_process(void) {
    int fd = open("/proc/self/cmdline", O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        return 0;
    }
    char command[256] = {0};
    const ssize_t length = read(fd, command, sizeof(command) - 1);
    close(fd);
    const char suffix[] = ":analyzer";
    const size_t name_length = strlen(command);
    return length > 0 && name_length >= sizeof(suffix) - 1 &&
            strcmp(command + name_length - (sizeof(suffix) - 1), suffix) == 0;
}

JNIEXPORT jboolean JNICALL
Java_com_exteragram_messenger_plugins_analyzer_AnalyzerNative_loadUpdate(
        JNIEnv *env, jclass clazz, jbyteArray data, jstring payload, jstring signature) {
    if (data == NULL || payload == NULL || signature == NULL || !is_analyzer_process()) {
        return JNI_FALSE;
    }
    const jsize length = (*env)->GetArrayLength(env, data);
    if (length <= 0 || length > 32 * 1024 * 1024) {
        return JNI_FALSE;
    }
    const jmethodID verify = (*env)->GetStaticMethodID(env, clazz, "verifyUpdate",
            "([BLjava/lang/String;Ljava/lang/String;)Z");
    if (verify == NULL) {
        return JNI_FALSE;
    }
    jbyteArray snapshot = (*env)->NewByteArray(env, length);
    if (snapshot == NULL) {
        return JNI_FALSE;
    }
    const int fd = make_library_fd();
    if (fd < 0) {
        return JNI_FALSE;
    }

    jbyte buffer[32 * 1024];
    jsize offset = 0;
    while (offset < length) {
        const jsize remaining = length - offset;
        const jsize size = remaining < (jsize) sizeof(buffer) ? remaining : (jsize) sizeof(buffer);
        (*env)->GetByteArrayRegion(env, data, offset, size, buffer);
        if ((*env)->ExceptionCheck(env)) {
            close(fd);
            return JNI_FALSE;
        }
        (*env)->SetByteArrayRegion(env, snapshot, offset, size, buffer);
        if ((*env)->ExceptionCheck(env)) {
            close(fd);
            return JNI_FALSE;
        }
        jsize written = 0;
        while (written < size) {
            const ssize_t count = write(fd, buffer + written, size - written);
            if (count < 0 && errno == EINTR) {
                continue;
            }
            if (count <= 0) {
                close(fd);
                return JNI_FALSE;
            }
            written += (jsize) count;
        }
        offset += size;
    }

    if (fchmod(fd, 0500) != 0 ||
            fcntl(fd, F_ADD_SEALS, F_SEAL_WRITE | F_SEAL_GROW | F_SEAL_SHRINK | F_SEAL_SEAL) != 0) {
        close(fd);
        return JNI_FALSE;
    }

    const jboolean valid = (*env)->CallStaticBooleanMethod(env, clazz, verify, snapshot, payload, signature);
    (*env)->DeleteLocalRef(env, snapshot);
    if ((*env)->ExceptionCheck(env) || !valid) {
        close(fd);
        return JNI_FALSE;
    }

    const android_dlextinfo info = {.flags = ANDROID_DLEXT_USE_LIBRARY_FD, .library_fd = fd};
    void *library = android_dlopen_ext("libextera_analyzer_ota.so", RTLD_NOW | RTLD_LOCAL, &info);
    close(fd);
    if (library == NULL) {
        return JNI_FALSE;
    }

    JNINativeMethod methods[] = {
        {"analyze", "([B[B)[B", dlsym(library, "Java_com_exteragram_messenger_plugins_analyzer_AnalyzerNative_analyze")},
        {"diff", "([B[B)[B", dlsym(library, "Java_com_exteragram_messenger_plugins_analyzer_AnalyzerNative_diff")},
    };
    if (methods[0].fnPtr == NULL || methods[1].fnPtr == NULL) {
        dlclose(library);
        return JNI_FALSE;
    }
    return (*env)->RegisterNatives(env, clazz, methods, sizeof(methods) / sizeof(methods[0])) == JNI_OK;
}
