#include <dlfcn.h>
#include <stdio.h>

int main(int argc, char **argv) {
    if (argc < 2) {
        fputs("expected project shared-library path\n", stderr);
        return 2;
    }
    void *library = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (!library) {
        fprintf(stderr, "dlopen: %s\n", dlerror());
        return 3;
    }
    int (*project_main)(int, char **) = (int (*)(int, char **))dlsym(library, "main");
    if (!project_main) {
        fprintf(stderr, "dlsym(main): %s\n", dlerror());
        dlclose(library);
        return 4;
    }
    int result = project_main(argc - 1, argv + 1);
    fflush(stdout);
    fflush(stderr);
    dlclose(library);
    return result;
}
