#include <stdio.h>
int main(void) {
    fputs("runtime-failure-probe\n", stderr);
    return 7;
}
