#include <errno.h>
#include <libgen.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

/* Clang asks for an executable linker path. The APK extracts native objects
 * under lib*.so names, while LLD selects the ELF driver from argv[0]. */
int main(int argc, char **argv) {
    char *copy = strdup(argv[0]);
    if (!copy) return 125;
    const char *directory = dirname(copy);
    size_t size = strlen(directory) + strlen("/liblld_exec.so") + 1;
    char *executable = malloc(size);
    char **arguments = calloc((size_t)argc + 1, sizeof(*arguments));
    if (!executable || !arguments) return 125;
    snprintf(executable, size, "%s/liblld_exec.so", directory);
    arguments[0] = "ld.lld";
    for (int i = 1; i < argc; i++) arguments[i] = argv[i];
    arguments[argc] = NULL;
    execv(executable, arguments);
    fprintf(stderr, "Cannot start Android linker: %s\n", strerror(errno));
    return 126;
}
