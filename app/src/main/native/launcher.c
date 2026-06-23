#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <grp.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#define LOG_TAG "RootLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static const char *kAppProcessPath = "/system/bin/app_process";
static const uid_t kShellUid = 2000;

static const gid_t kRequiredShellGids[] = {
        2000, /* shell           */
        1002, /* bluetooth       */
        1004, /* input           */
        1005, /* audio           */
        1007, /* log             */
        1011, /* adb             */
        1013, /* media           */
        1015, /* sdcard_rw       */
        1024, /* mtp             */
        1028, /* sdcard_r        */
        1065, /* reserved_disk   */
        1078, /* ext_data_rw     */
        1079, /* ext_obb_rw      */
        1096, /* update_engine_log */
        3001, /* net_bt_admin    */
        3002, /* net_bt          */
        3003, /* inet            */
        3006, /* net_bw_stats    */
        3007, /* net_bw_acct     */
        3009, /* readproc        */
        3010, /* wakelock        */
        3011, /* uhid            */
        3012, /* readtracefs     */
        3013, /* virtualmachine  */
};

typedef struct {
    const char *apk_path;
    const char *process_name;
    const char *starter_class;
    const char *token;
    const char *package_name;
    const char *service_class;
    const char *debug_name;
    const char *log_file;
    int uid;
    bool keep_root;
} LauncherArgs;

/* ── 文件日志 ── */

static int g_log_fd = -1;

static void flogf(const char *fmt, ...) {
    if (g_log_fd < 0) return;
    char buf[1024];
    va_list ap;
    va_start(ap, fmt);
    int n = vsnprintf(buf, sizeof(buf) - 1, fmt, ap);
    va_end(ap);
    if (n > 0) {
        buf[n] = '\n';
        write(g_log_fd, buf, (size_t) (n + 1));
    }
}

/* 同时写 logcat 和文件 */
#define LOG_IF(level_macro, level, fmt, ...) \
    do { level_macro(fmt, ##__VA_ARGS__); flogf("[" level "] " fmt, ##__VA_ARGS__); } while(0)
#define LOGFI(...) LOG_IF(LOGI, "I", __VA_ARGS__)
#define LOGFW(...) LOG_IF(LOGW, "W", __VA_ARGS__)
#define LOGFE(...) LOG_IF(LOGE, "E", __VA_ARGS__)

/* ── 参数解析 ── */

static bool starts_with(const char *value, const char *prefix) {
    return strncmp(value, prefix, strlen(prefix)) == 0;
}

static bool parse_int(const char *value, int *out) {
    char *end_ptr = NULL;
    long parsed = strtol(value, &end_ptr, 10);
    if (value[0] == '\0' || end_ptr == value || *end_ptr != '\0') return false;
    *out = (int) parsed;
    return true;
}

static bool parse_args(int argc, char **argv, LauncherArgs *out) {
    memset(out, 0, sizeof(*out));
    out->uid = -1;

    for (int i = 1; i < argc; ++i) {
        if (starts_with(argv[i], "--apk="))           out->apk_path      = argv[i] + 6;
        else if (starts_with(argv[i], "--process-name=")) out->process_name  = argv[i] + 15;
        else if (starts_with(argv[i], "--starter-class=")) out->starter_class = argv[i] + 16;
        else if (starts_with(argv[i], "--token="))     out->token         = argv[i] + 8;
        else if (starts_with(argv[i], "--package="))   out->package_name  = argv[i] + 10;
        else if (starts_with(argv[i], "--class="))     out->service_class = argv[i] + 8;
        else if (starts_with(argv[i], "--debug-name=")) out->debug_name   = argv[i] + 13;
        else if (starts_with(argv[i], "--log-file="))  out->log_file      = argv[i] + 11;
        else if (starts_with(argv[i], "--uid=")) {
            if (!parse_int(argv[i] + 6, &out->uid)) {
                LOGE("Invalid uid: %s", argv[i] + 6);
                return false;
            }
        }
        else if (strcmp(argv[i], "--keep-root") == 0) out->keep_root = true;
    }

    return out->apk_path != NULL
           && out->process_name != NULL
           && out->starter_class != NULL
           && out->token != NULL
           && out->package_name != NULL
           && out->service_class != NULL
           && out->uid >= 0;
}

/* ── exec app_process ── */

static char *format_arg(const char *prefix, const char *value) {
    size_t size = strlen(prefix) + strlen(value) + 1;
    char *out = (char *) malloc(size);
    if (out == NULL) { LOGE("malloc failed: %s", strerror(errno)); return NULL; }
    snprintf(out, size, "%s%s", prefix, value);
    return out;
}

static void exec_app_process(const LauncherArgs *args) {
    char uid_text[32];
    char *nice_name_arg = NULL, *token_arg = NULL, *package_arg = NULL;
    char *service_arg = NULL, *uid_arg = NULL, *debug_arg = NULL;
    char *exec_args[11];
    size_t index = 0;

    snprintf(uid_text, sizeof(uid_text), "%d", args->uid);

    nice_name_arg = format_arg("--nice-name=", args->process_name);
    token_arg     = format_arg("--token=",     args->token);
    package_arg   = format_arg("--package=",   args->package_name);
    service_arg   = format_arg("--class=",     args->service_class);
    uid_arg       = format_arg("--uid=",       uid_text);
    if (args->debug_name != NULL)
        debug_arg = format_arg("--debug-name=", args->debug_name);

    if (!nice_name_arg || !token_arg || !package_arg || !service_arg || !uid_arg
        || (args->debug_name != NULL && !debug_arg)) {
        free(nice_name_arg); free(token_arg); free(package_arg);
        free(service_arg);   free(uid_arg);   free(debug_arg);
        exit(1);
    }

    if (setenv("CLASSPATH", args->apk_path, 1) != 0) {
        LOGFE("setenv(CLASSPATH) failed: %s", strerror(errno));
        exit(1);
    }

    exec_args[index++] = (char *) kAppProcessPath;
    exec_args[index++] = (char *) "/system/bin";
    exec_args[index++] = nice_name_arg;
    exec_args[index++] = (char *) args->starter_class;
    exec_args[index++] = token_arg;
    exec_args[index++] = package_arg;
    exec_args[index++] = service_arg;
    exec_args[index++] = uid_arg;
    if (debug_arg != NULL) exec_args[index++] = debug_arg;
    exec_args[index] = NULL;

    LOGFI("execv: %s CLASSPATH=%s nice-name=%s",
          kAppProcessPath, args->apk_path, args->process_name);

    /* 把 stderr 重定向到日志文件，捕获 Java 侧的异常和 System.err 输出 */
    if (g_log_fd >= 0) {
        dup2(g_log_fd, STDERR_FILENO);
    }

    execv(kAppProcessPath, exec_args);
    LOGE("execv(%s) failed: %s", kAppProcessPath, strerror(errno));
    free(nice_name_arg); free(token_arg); free(package_arg);
    free(service_arg);   free(uid_arg);   free(debug_arg);
    exit(1);
}

/* ── main ── */

int main(int argc, char **argv) {
    LauncherArgs args;

    if (!parse_args(argc, argv, &args)) {
        LOGE("Missing required launcher args");
        return 1;
    }

    /* 以 root 身份打开日志文件（demote 之后 fd 依然有效） */
    if (args.log_file != NULL) {
        g_log_fd = open(args.log_file, O_WRONLY | O_CREAT | O_TRUNC, 0644);
        if (g_log_fd < 0) {
            LOGW("Cannot open log file %s: %s", args.log_file, strerror(errno));
        } else {
            /* 确保 app 进程可读 */
            fchmod(g_log_fd, 0644);
        }
    }

    LOGFI("launcher start: apk=%s uid=%d", args.apk_path, args.uid);

    pid_t child = fork();
    if (child < 0) {
        LOGFE("fork failed: %s", strerror(errno));
        return 1;
    }

    if (child == 0) {
        if (!args.keep_root) {
            static const size_t kGidCount =
                    sizeof(kRequiredShellGids) / sizeof(kRequiredShellGids[0]);

            int sg_ret = setgroups((int) kGidCount, kRequiredShellGids);
            if (sg_ret != 0) {
                LOGFW("setgroups(%zu gids) failed: %s — continuing", kGidCount, strerror(errno));
            } else {
                LOGFI("setgroups(%zu gids): ok", kGidCount);
            }

            if (setresgid(kShellUid, kShellUid, kShellUid) != 0) {
                LOGFE("setresgid(%u) failed: %s", (unsigned) kShellUid, strerror(errno));
                _exit(1);
            }
            LOGFI("setresgid(%u): ok", (unsigned) kShellUid);

            if (setresuid(kShellUid, kShellUid, kShellUid) != 0) {
                LOGFE("setresuid(%u) failed: %s", (unsigned) kShellUid, strerror(errno));
                _exit(1);
            }
            LOGFI("setresuid(%u): ok — exec app_process", (unsigned) kShellUid);
        }

        exec_app_process(&args);
        _exit(1);
    }

    int status = 0;
    waitpid(child, &status, 0);

    if (WIFEXITED(status) && WEXITSTATUS(status) == 0) {
        LOGFI("child exited cleanly");
        return 0;
    }

    LOGFE("child exited with status=%d", status);
    return 1;
}
