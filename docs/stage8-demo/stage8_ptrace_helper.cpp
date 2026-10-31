#include <cerrno>
#include <csignal>
#include <sys/ptrace.h>
#include <sys/wait.h>
#include <unistd.h>

int probe_ptrace() {
    pid_t child = fork();
    if (child < 0) return 10 + errno;
    if (child == 0) {
        if (ptrace(PTRACE_TRACEME, 0, nullptr, nullptr) < 0) _exit(20 + errno);
        raise(SIGSTOP);
        _exit(0);
    }
    int status = 0;
    if (waitpid(child, &status, 0) != child) return 30 + errno;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (!WIFSTOPPED(status)) return 40;
    if (ptrace(PTRACE_CONT, child, nullptr, nullptr) < 0) return 50 + errno;
    if (waitpid(child, &status, 0) != child) return 60 + errno;
    return WIFEXITED(status) && WEXITSTATUS(status) == 0 ? 0 : 70;
}
