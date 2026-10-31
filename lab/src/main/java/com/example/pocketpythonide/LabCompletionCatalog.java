package com.example.pocketpythonide;

import java.util.HashMap;
import java.util.Map;

/** Lab-only static symbols; the lightweight IDE does not package these. */
public final class LabCompletionCatalog implements CompletionCatalog {
    private static final Map<String, String> MEMBERS = new HashMap<>();
    static {
        MEMBERS.put("torch", "Tensor tensor as_tensor from_numpy zeros zeros_like ones ones_like empty arange linspace rand randn randint eye stack cat reshape flatten matmul mm bmm einsum sum mean std max min argmax argmin softmax sigmoid relu no_grad inference_mode enable_grad set_grad_enabled manual_seed save load device cuda backends nn optim autograd utils linalg __version__ float32 float64 int64 bool");
        MEMBERS.put("torch.nn", "Module Linear Conv1d Conv2d Conv3d BatchNorm1d BatchNorm2d LayerNorm Dropout Embedding Sequential ReLU GELU SiLU Sigmoid Tanh Softmax CrossEntropyLoss MSELoss L1Loss functional init Parameter ModuleList ModuleDict Transformer MultiheadAttention");
        MEMBERS.put("torch.nn.functional", "relu gelu silu softmax log_softmax cross_entropy mse_loss l1_loss dropout linear conv1d conv2d conv3d pad normalize embedding interpolate max_pool2d avg_pool2d layer_norm batch_norm");
        MEMBERS.put("torch.optim", "Optimizer SGD Adam AdamW RMSprop Adagrad lr_scheduler");
        MEMBERS.put("torch.autograd", "Function Variable grad backward functional");
        MEMBERS.put("torch.linalg", "norm vector_norm matrix_norm solve inv pinv svd qr eig eigh det matrix_rank cholesky");
        MEMBERS.put("numpy", "array asarray arange linspace zeros ones empty eye reshape concatenate stack vstack hstack mean sum std var min max argmin argmax dot matmul einsum where clip unique random linalg float32 float64 int64 ndarray __version__ pi inf nan");
        MEMBERS.put("numpy.random", "default_rng rand random randn normal uniform integers randint choice seed shuffle permutation");
        MEMBERS.put("numpy.linalg", "norm solve inv pinv svd qr eig eigh det matrix_rank cholesky");
    }

    @Override public String modules() { return "numpy torch"; }
    @Override public String members(String module) { return MEMBERS.get(module); }
}
