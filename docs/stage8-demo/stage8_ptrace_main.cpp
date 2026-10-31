#include <cstdio>

int probe_ptrace();

int main() {
    int result = probe_ptrace();
    std::printf("stage8-ptrace:%d\n", result);
    return result == 0 ? 0 : 1;
}
