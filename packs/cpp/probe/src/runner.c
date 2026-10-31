#include <dlfcn.h>
#include <stdio.h>

int main(int argc, char **argv) {
    if (argc != 2) {
        fputs("expected one shared-library path\n", stderr);
        return 2;
    }
    void *library = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (!library) {
        fprintf(stderr, "dlopen: %s\n", dlerror());
        return 3;
    }
    int (*entry)(void) = (int (*)(void))dlsym(library, "stage5_entry");
    if (!entry) {
        fprintf(stderr, "dlsym: %s\n", dlerror());
        dlclose(library);
        return 4;
    }
    int value = entry();
    printf("entry-result:%d\n", value);
    dlclose(library);
    return 0;
}
