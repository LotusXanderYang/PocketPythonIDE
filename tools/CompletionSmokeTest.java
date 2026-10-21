package com.example.pocketpythonide;

/** Run on the host JDK to guard core completion behavior without an Android device. */
public final class CompletionSmokeTest {
    private static void expect(String source, String name, boolean present) {
        PythonCompletions.Result result = PythonCompletions.suggest(source, source.length());
        boolean found = result.items.stream().anyMatch(item -> item.label.equals(name));
        if (found != present)
            throw new AssertionError("Expected " + name + " present=" + present + " for " + source);
    }

    public static void main(String[] args) {
        expect("p", "print", true);
        expect("pr", "print", true);
        expect("# p", "print", false);
        expect("'p", "print", false);
        expect("import torch\ntorch.te", "tensor", true);
        expect("import torch as t\nt.nn.Li", "Linear", true);
        expect("def predictor(value):\n    return value\npre", "predictor", true);
        expect("# def phantom():\npha", "phantom", false);
        expect("import numpy as np\nnp.ar", "array", true);
        expect("torch.te", "tensor", false);
        System.out.println("Completion smoke test passed");
    }
}
