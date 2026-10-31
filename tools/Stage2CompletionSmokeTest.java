package com.example.pocketpythonide;

/** The editor-only IDE must not advertise Lab-only scientific modules. */
public final class Stage2CompletionSmokeTest {
    public static void main(String[] args) {
        PythonCompletions.Result ide = PythonCompletions.suggest("tor", 3,
                CompletionCatalog.EMPTY);
        if (ide.items.stream().anyMatch(item -> item.label.equals("torch")))
            throw new AssertionError("IDE advertised torch");
        PythonCompletions.Result lab = PythonCompletions.suggest("tor", 3,
                new LabCompletionCatalog());
        if (lab.items.stream().noneMatch(item -> item.label.equals("torch")))
            throw new AssertionError("Lab lost torch completion");
        PythonCompletions.Result member = PythonCompletions.suggest(
                "import torch\ntorch.te", 21, CompletionCatalog.EMPTY);
        if (member.items.stream().anyMatch(item -> item.label.equals("tensor")))
            throw new AssertionError("IDE advertised torch members");
        System.out.println("Stage 2 completion scope OK");
    }
}
