package com.example.pocketpythonide;

import org.json.JSONObject;

/** CPU Python/PyTorch host with private scripts and its own runtime asset. */
public final class LabActivity extends WorkbenchActivity {
    @Override protected boolean hasRuntime() { return true; }
    @Override protected String starterScript() {
        return "import torch\n\n"
                + "print('torch:', torch.__version__)\n"
                + "x = torch.tensor([[1.0, 2.0], [3.0, 4.0]])\n"
                + "print('x @ x =', (x @ x).tolist())\n";
    }
    @Override protected String environmentSummary() {
        return "CPython 3.14.6 · PyTorch 2.14.1 · NumPy 2.5.4\nAndroid arm64 · 本地 CPU 运行";
    }
    @Override protected String librariesSummary() { return "torch 2.14.1\nnumpy 2.5.4"; }
    @Override protected String statusSummary() { return "{ }  Python 3.14.6"; }
    @Override protected CompletionCatalog completionCatalog() { return new LabCompletionCatalog(); }
    @Override protected String probeProgressText() {
        return "正在导入 NumPy 与 PyTorch，并检查矩阵运算及自动求导…\n";
    }
    @Override protected String formatProbe(JSONObject json) {
        return "Python " + json.optString("python") + " · " + json.optString("machine")
                + "\nPyTorch " + json.optString("torch")
                + " · NumPy " + json.optString("numpy")
                + "\n矩阵乘法：" + json.optString("matrix_multiply")
                + "\n自动求导：" + json.optString("autograd")
                + "\nNumPy 桥接：" + json.optString("numpy_bridge")
                + "\n文件读写：" + json.optString("file_io")
                + "\n\n[自检通过]";
    }
    @Override protected String probeBackend() throws Exception {
        return LabRuntimeClient.execute(this, "probe", null, null, null);
    }
    @Override protected String runBackend(String source, String filename, String workdir)
            throws Exception {
        return LabRuntimeClient.execute(this, "run", source, filename, workdir);
    }
}
