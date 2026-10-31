#include <stdio.h>
#include "answer.h"

int main(void) {
    int input = 0;
    if (scanf("%d", &input) != 1) {
        fputs("expected one integer\n", stderr);
        return 2;
    }
    printf("course-c:%d\n", answer(input));
    return 0;
}
