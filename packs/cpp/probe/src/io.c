#include <stdio.h>

int main(void) {
    char input[64];
    if (!fgets(input, sizeof input, stdin)) return 3;
    fprintf(stdout, "stdout:%s", input);
    fprintf(stderr, "stderr:probe\n");
    return 7;
}
