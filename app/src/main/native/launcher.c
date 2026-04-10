#include <android/log.h>
#include <dlfcn.h>
#include <errno.h>
#include <grp.h>
#include <limits.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#define LOG_TAG "RootLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static const char *kShellPackage = "com.android.shell";
static const char *kShellContext = "u:r:shell:s0";
static const char *kPackagesListPath = "/data/system/packages.list";
static const char *kAppProcessPath = "/system/bin/app_process";
static const uid_t kShellUid = 2000;
static const gid_t kRequiredShellGids[] = {
        2000, // shell

        1002, // bluetooth
        1004, // input
        1005, // audio
        1007, // log
        1011, // adb
        1013, // media
        1015, // sdcard_rw
        1024, // mtp
        1028, // sdcard_r
        1065, // reserved_disk
        1078, // ext_data_rw
        1079, // ext_obb_rw
        1096, // update_engine_log

        3001, // net_bt_admin
        3002, // net_bt
        3003, // inet
        3006, // net_bw_stats
        3007, // net_bw_acct
        3009, // readproc
        3010, // wakelock
        3011, // uhid
        3012, // readtracefs
        3013, // virtualmachine
};

typedef struct {
    const char *apk_path;
    const char *process_name;
    const char *starter_class;
    const char *token;
    const char *package_name;
    const char *service_class;
    const char *debug_name;
    int uid;
} LauncherArgs;

typedef struct {
    gid_t *items;
    size_t count;
    size_t capacity;
} GidList;

static bool starts_with(const char *value, const char *prefix) {
    return strncmp(value, prefix, strlen(prefix)) == 0;
}

static bool parse_int(const char *value, int *out) {
    char *end_ptr = NULL;
    long parsed = strtol(value, &end_ptr, 10);
    if (value[0] == '\0' || end_ptr == value || *end_ptr != '\0') {
        return false;
    }
    *out = (int) parsed;
    return true;
}

static bool parse_args(int argc, char **argv, LauncherArgs *out) {
    memset(out, 0, sizeof(*out));
    out->uid = -1;

    for (int i = 1; i < argc; ++i) {
        if (starts_with(argv[i], "--apk=")) {
            out->apk_path = argv[i] + 6;
        } else if (starts_with(argv[i], "--process-name=")) {
            out->process_name = argv[i] + 15;
        } else if (starts_with(argv[i], "--starter-class=")) {
            out->starter_class = argv[i] + 16;
        } else if (starts_with(argv[i], "--token=")) {
            out->token = argv[i] + 8;
        } else if (starts_with(argv[i], "--package=")) {
            out->package_name = argv[i] + 10;
        } else if (starts_with(argv[i], "--class=")) {
            out->service_class = argv[i] + 8;
        } else if (starts_with(argv[i], "--uid=")) {
            if (!parse_int(argv[i] + 6, &out->uid)) {
                LOGE("Invalid uid: %s", argv[i] + 6);
                return false;
            }
        } else if (starts_with(argv[i], "--debug-name=")) {
            out->debug_name = argv[i] + 13;
        }
    }

    return out->apk_path != NULL
           && out->process_name != NULL
           && out->starter_class != NULL
           && out->token != NULL
           && out->package_name != NULL
           && out->service_class != NULL
           && out->uid >= 0;
}

static bool append_gid_unique(GidList *list, gid_t gid) {
    size_t i;
    for (i = 0; i < list->count; ++i) {
        if (list->items[i] == gid) {
            return true;
        }
    }

    if (list->count == list->capacity) {
        size_t new_capacity = list->capacity == 0 ? 16 : list->capacity * 2;
        gid_t *new_items = (gid_t *) realloc(list->items, sizeof(gid_t) * new_capacity);
        if (new_items == NULL) {
            LOGE("realloc gids failed: %s", strerror(errno));
            return false;
        }
        list->items = new_items;
        list->capacity = new_capacity;
    }

    list->items[list->count++] = gid;
    return true;
}

static void free_gid_list(GidList *list) {
    free(list->items);
    list->items = NULL;
    list->count = 0;
    list->capacity = 0;
}

static bool parse_gid_csv(char *csv, GidList *list) {
    char *save_ptr = NULL;
    char *token = strtok_r(csv, ",", &save_ptr);
    while (token != NULL) {
        if (token[0] != '\0') {
            gid_t gid = (gid_t) strtoul(token, NULL, 10);
            if (!append_gid_unique(list, gid)) {
                return false;
            }
        }
        token = strtok_r(NULL, ",", &save_ptr);
    }
    return true;
}

static bool ensure_required_shell_gids(GidList *list) {
    size_t i;
    for (i = 0; i < sizeof(kRequiredShellGids) / sizeof(kRequiredShellGids[0]); ++i) {
        if (!append_gid_unique(list, kRequiredShellGids[i])) {
            return false;
        }
    }
    return true;
}

static bool read_shell_identity(uid_t *uid_out, GidList *static_gids_out) {
    FILE *input = fopen(kPackagesListPath, "r");
    if (input == NULL) {
        LOGW("Failed to open %s: %s, continue with defaults", kPackagesListPath, strerror(errno));
        return false;
    }

    {
        const size_t package_len = strlen(kShellPackage);
        char line[8192];
        while (fgets(line, sizeof(line), input) != NULL) {
            char *save_ptr = NULL;
            char *field = NULL;
            char *uid_field = NULL;
            char *gids_field = NULL;
            int index = 0;

            if (!starts_with(line, kShellPackage)) {
                continue;
            }
            if (line[package_len] != ' ' && line[package_len] != '\t') {
                continue;
            }

            field = strtok_r(line, " \t\r\n", &save_ptr);
            while (field != NULL) {
                if (index == 1) {
                    uid_field = field;
                } else if (index == 5) {
                    gids_field = field;
                    break;
                }
                field = strtok_r(NULL, " \t\r\n", &save_ptr);
                ++index;
            }

            if (uid_field == NULL || gids_field == NULL) {
                LOGW("Unexpected packages.list format for %s, continue with defaults",
                     kShellPackage);
                fclose(input);
                return false;
            }

            *uid_out = (uid_t) strtoul(uid_field, NULL, 10);
            if (!parse_gid_csv(gids_field, static_gids_out)) {
                fclose(input);
                return false;
            }

            fclose(input);
            return true;
        }
    }

    fclose(input);
    LOGW("Package %s not found in %s, continue with defaults", kShellPackage, kPackagesListPath);
    return false;
}

static bool read_shell_dynamic_gids(uid_t uid, GidList *out) {
    int group_count = 16;
    gid_t *groups = NULL;

    while (true) {
        gid_t *new_groups = (gid_t *) realloc(groups, sizeof(gid_t) * (size_t) group_count);
        int requested = group_count;
        int result;

        if (new_groups == NULL) {
            LOGE("realloc dynamic gids failed: %s", strerror(errno));
            free(groups);
            return false;
        }
        groups = new_groups;

        result = getgrouplist("shell", uid, groups, &requested);
        if (result >= 0) {
            group_count = requested;
            break;
        }
        if (requested <= group_count || requested > 256) {
            LOGE("getgrouplist failed for uid=%u", (unsigned) uid);
            free(groups);
            return false;
        }
        group_count = requested;
    }

    {
        int i;
        for (i = 0; i < group_count; ++i) {
            if (!append_gid_unique(out, groups[i])) {
                free(groups);
                return false;
            }
        }
    }

    free(groups);
    return true;
}


static char *format_arg(const char *prefix, const char *value) {
    size_t size = strlen(prefix) + strlen(value) + 1;
    char *out = (char *) malloc(size);
    if (out == NULL) {
        LOGE("malloc failed: %s", strerror(errno));
        return NULL;
    }
    snprintf(out, size, "%s%s", prefix, value);
    return out;
}

static void log_shell_identity(void) {
    FILE *pipe = popen("/system/bin/sh -c 'id 2>&1'", "r");
    char line[512];

    if (pipe == NULL) {
        LOGE("popen(id) failed: %s", strerror(errno));
        return;
    }

    while (fgets(line, sizeof(line), pipe) != NULL) {
        size_t length = strlen(line);
        while (length > 0 && (line[length - 1] == '\n' || line[length - 1] == '\r')) {
            line[--length] = '\0';
        }
        LOGI("shell identity: %s", line);
    }

    if (pclose(pipe) != 0) {
        LOGE("pclose(id) failed: %s", strerror(errno));
    }
}

static void exec_app_process(const LauncherArgs *args) {
    char uid_text[32];
    char *nice_name_arg = NULL;
    char *token_arg = NULL;
    char *package_arg = NULL;
    char *service_arg = NULL;
    char *uid_arg = NULL;
    char *debug_arg = NULL;
    char *exec_args[11];
    size_t index = 0;

    snprintf(uid_text, sizeof(uid_text), "%d", args->uid);

    nice_name_arg = format_arg("--nice-name=", args->process_name);
    token_arg = format_arg("--token=", args->token);
    package_arg = format_arg("--package=", args->package_name);
    service_arg = format_arg("--class=", args->service_class);
    uid_arg = format_arg("--uid=", uid_text);
    if (args->debug_name != NULL) {
        debug_arg = format_arg("--debug-name=", args->debug_name);
    }

    if (nice_name_arg == NULL || token_arg == NULL || package_arg == NULL
        || service_arg == NULL || uid_arg == NULL
        || (args->debug_name != NULL && debug_arg == NULL)) {
        free(nice_name_arg);
        free(token_arg);
        free(package_arg);
        free(service_arg);
        free(uid_arg);
        free(debug_arg);
        exit(1);
    }

    if (setenv("CLASSPATH", args->apk_path, 1) != 0) {
        LOGE("setenv(CLASSPATH) failed: %s", strerror(errno));
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
    if (debug_arg != NULL) {
        exec_args[index++] = debug_arg;
    }
    exec_args[index] = NULL;

    execv(kAppProcessPath, exec_args);
    LOGE("execv(%s) failed: %s", kAppProcessPath, strerror(errno));
    free(nice_name_arg);
    free(token_arg);
    free(package_arg);
    free(service_arg);
    free(uid_arg);
    free(debug_arg);
    exit(1);
}

int main(int argc, char **argv) {
    LauncherArgs args;
    GidList gids = {0};
    uid_t shell_uid = kShellUid;

    if (!parse_args(argc, argv, &args)) {
        LOGE("Missing required launcher args");
        return 1;
    }

    if (!read_shell_identity(&shell_uid, &gids)) {
        LOGI("Proceeding without packages.list static gids");
    }

    if (!read_shell_dynamic_gids(shell_uid, &gids)) {
        free_gid_list(&gids);
        return 1;
    }

    if (!ensure_required_shell_gids(&gids)) {
        LOGE("Failed to append required shell gids");
        free_gid_list(&gids);
        return 1;
    }

    pid_t child = fork();
    if (child < 0) {
        LOGE("fork failed: %s", strerror(errno));
        free_gid_list(&gids);
        return 1;
    }

    if (child == 0) {
        if (gids.count > 0 && setgroups((int) gids.count, gids.items) != 0) {
            LOGE("setgroups failed: %s", strerror(errno));
            _exit(1);
        }

        if (setresgid(shell_uid, shell_uid, shell_uid) != 0) {
            LOGE("setresgid(%u) failed: %s", (unsigned) shell_uid, strerror(errno));
            _exit(1);
        }

        if (setresuid(shell_uid, shell_uid, shell_uid) != 0) {
            LOGE("setresuid(%u) failed: %s", (unsigned) shell_uid, strerror(errno));
            _exit(1);
        }

        LOGI("Child: switching to shell uid=%u gids=%zu and exec app_process",
             (unsigned) shell_uid, gids.count);
        log_shell_identity();
        free_gid_list(&gids);
        exec_app_process(&args);
        /* exec_app_process calls exit(1) on failure, but just in case */
        _exit(1);
    }

    free_gid_list(&gids);
    int status = 0;
    waitpid(child, &status, 0);

    if (WIFEXITED(status) && WEXITSTATUS(status) == 0) {
        return 0;
    }

    LOGW("Shell-mode exec failed (status=%d), retrying as root", status);
    exec_app_process(&args);
    return 1;
}
