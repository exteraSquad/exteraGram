#include <jni.h>
#include <atomic>
#include <csignal>
#include <android/log.h>
#include <cstring>
#include <fcntl.h>
#include <climits>
#include <unistd.h>
#include <sys/syscall.h>

static const char* TAG = "NativeCrashHandler";
static char g_crash_flag_path[PATH_MAX];
static struct sigaction g_old_sa[NSIG];
static struct sigaction g_default_sa;
static std::atomic_flag g_handling_signal = ATOMIC_FLAG_INIT;
static bool g_initialized = false;

static void write_crash_flag() {
    if (g_crash_flag_path[0] != '\0') {
        int fd = open(g_crash_flag_path, O_CREAT | O_TRUNC | O_WRONLY | O_CLOEXEC, 0600);
        if (fd != -1) {
            write(fd, "1", 1);
            fsync(fd);
            close(fd);
        }
    }
}

static void native_crash_signal_handler(int signum, siginfo_t* info, void* /* context */) {
    if (signum <= 0 || signum >= NSIG || g_handling_signal.test_and_set(std::memory_order_relaxed)) {
        _exit(signum > 0 && signum < NSIG ? 128 + signum : 255);
    }

    write_crash_flag();

    sigset_t signal_mask;
    sigemptyset(&signal_mask);
    sigaddset(&signal_mask, signum);

    if (sigaction(signum, &g_old_sa[signum], nullptr) == 0) {
        if (info != nullptr && info->si_code > 0 &&
            !(signum == SIGSEGV && info->si_code == SEGV_MTEAERR) &&
            !(signum == SIGBUS && info->si_code == BUS_MCEERR_AO)) {
            g_handling_signal.clear(std::memory_order_relaxed);
            return;
        }
        if (sigprocmask(SIG_UNBLOCK, &signal_mask, nullptr) == 0) {
            if (info != nullptr) {
                syscall(__NR_rt_tgsigqueueinfo, getpid(), gettid(), signum, info);
            } else {
                raise(signum);
            }
        }
    }

    if (sigaction(signum, &g_default_sa, nullptr) == 0) {
        sigprocmask(SIG_UNBLOCK, &signal_mask, nullptr);
        raise(signum);
    }

    _exit(128 + signum);
}

extern "C" JNIEXPORT void JNICALL
Java_com_exteragram_messenger_plugins_utils_NativeCrashHandler_init(JNIEnv* env, jclass /* clazz */, jstring flagPath) {
    if (g_initialized) {
        return;
    }

    jsize path_length = env->GetStringUTFLength(flagPath);
    if (path_length <= 0 || path_length >= static_cast<jsize>(sizeof(g_crash_flag_path))) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "Invalid native crash flag path.");
        return;
    }

    const char* path_str = env->GetStringUTFChars(flagPath, nullptr);
    if (!path_str) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to get native crash flag path.");
        return;
    }

    memcpy(g_crash_flag_path, path_str, path_length);
    g_crash_flag_path[path_length] = '\0';
    env->ReleaseStringUTFChars(flagPath, path_str);

    struct sigaction sa{};
    memset(&sa, 0, sizeof(sa));
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
    sa.sa_sigaction = native_crash_signal_handler;

    memset(&g_default_sa, 0, sizeof(g_default_sa));
    sigemptyset(&g_default_sa.sa_mask);
    g_default_sa.sa_handler = SIG_DFL;

    int signals[] = { SIGSEGV, SIGABRT, SIGFPE, SIGILL, SIGBUS, SIGTRAP };
    for (int sig : signals) {
        if (sigaction(sig, &sa, &g_old_sa[sig]) == -1) {
            __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to set signal handler for signal %d", sig);
        }
    }
    g_initialized = true;
    __android_log_print(ANDROID_LOG_INFO, TAG, "Native crash handler initialized.");
}