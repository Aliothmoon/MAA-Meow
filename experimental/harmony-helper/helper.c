// Experimental, bounded-lifetime shell helper. No arbitrary command execution.
#define _POSIX_C_SOURCE 200809L
#include <arpa/inet.h>
#include <errno.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

static int execute(char *const argv[])
{
    pid_t pid = fork();
    if (pid < 0) return -1;
    if (pid == 0) {
        execv(argv[0], argv);
        _exit(127);
    }
    int status;
    while (waitpid(pid, &status, 0) < 0) {
        if (errno != EINTR) return -1;
    }
    return WIFEXITED(status) ? WEXITSTATUS(status) : -1;
}

static int number(const char *s, long min, long max)
{
    char *end;
    errno = 0;
    long value = strtol(s, &end, 10);
    return !errno && *s && !*end && value >= min && value <= max;
}

int main(int argc, char **argv)
{
    if (argc != 3 || !number(argv[1], 1024, 65535)) {
        fprintf(stderr, "usage: maa-harmony-helper PORT TOKEN_FILE\n");
        return 2;
    }
    char token[130];
    FILE *secret = fopen(argv[2], "r");
    if (!secret || !fgets(token, sizeof(token), secret)) return 2;
    fclose(secret);
    token[strcspn(token, "\r\n")] = 0;
    if (strlen(token) < 32) return 2;
    signal(SIGPIPE, SIG_IGN);
    // Hard expiry even if a peer sends partial input or launches a stuck tool.
    alarm(300);
    int server = socket(AF_INET, SOCK_STREAM, 0);
    struct sockaddr_in addr = {0};
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    addr.sin_port = htons((unsigned short)atoi(argv[1]));
    if (server < 0 || bind(server, (struct sockaddr *)&addr, sizeof(addr)) ||
        listen(server, 1)) {
        perror("listen");
        return 1;
    }
    printf("ready uid=%ld pid=%ld port=%s lifetime=300s\n",
           (long)getuid(), (long)getpid(), argv[1]);
    fflush(stdout);
    int stopping = 0;
    while (!stopping) {
        int client = accept(server, NULL, NULL);
        if (client < 0) {
            if (errno == EINTR) continue;
            break;
        }
        FILE *io = fdopen(client, "r+");
        if (!io) { close(client); continue; }
        setvbuf(io, NULL, _IONBF, 0);
        char line[512];
        if (!fgets(line, sizeof(line), io)) { fclose(io); continue; }
        line[strcspn(line, "\r\n")] = 0;
        if (strcmp(line, token)) {
            fprintf(io, "ERR auth\n");
            fclose(io);
            continue;
        }
        fprintf(io, "OK auth\n");
        while (fgets(line, sizeof(line), io)) {
            char *words[8];
            int count = 0;
            char *save = NULL;
            for (char *word = strtok_r(line, " \r\n", &save); word && count < 8;
                 word = strtok_r(NULL, " \r\n", &save)) words[count++] = word;
            if (count == 1 && !strcmp(words[0], "status")) {
                fprintf(io, "OK uid=%ld pid=%ld\n", (long)getuid(), (long)getpid());
            } else if (count == 1 && !strcmp(words[0], "stop")) {
                fprintf(io, "OK stopping\n");
                stopping = 1;
                break;
            } else if (count == 1 && !strcmp(words[0], "capture")) {
                char *cmd[] = {"/bin/snapshot_display", "-f",
                    "/data/local/tmp/maa-helper-frame.jpeg", NULL};
                unlink(cmd[2]);
                int result = execute(cmd);
                fprintf(io, "%s capture exit=%d\n",
                        result == 0 && access(cmd[2], R_OK) == 0 ? "OK" : "ERR", result);
            } else if (count == 3 && !strcmp(words[0], "tap") &&
                       number(words[1], 0, 16383) && number(words[2], 0, 16383)) {
                char *cmd[] = {"/bin/uinput", "-T", "-c", words[1], words[2], NULL};
                int result = execute(cmd);
                fprintf(io, "%s tap exit=%d\n", result == 0 ? "OK" : "ERR", result);
            } else if (count == 6 && !strcmp(words[0], "swipe") &&
                       number(words[1], 0, 16383) && number(words[2], 0, 16383) &&
                       number(words[3], 0, 16383) && number(words[4], 0, 16383) &&
                       number(words[5], 1, 15000)) {
                char *cmd[] = {"/bin/uinput", "-T", "-m", words[1], words[2],
                               words[3], words[4], words[5], NULL};
                int result = execute(cmd);
                fprintf(io, "%s swipe exit=%d\n", result == 0 ? "OK" : "ERR", result);
            } else fprintf(io, "ERR command\n");
        }
        fclose(io);
    }
    close(server);
    return 0;
}
