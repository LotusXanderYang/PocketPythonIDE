package com.example.pocketpythonide;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fast, offline Python suggestions. Never evaluates the user's source. */
final class PythonCompletions {
    static final class Item {
        final String label;
        final String kind;
        final String insert;
        final int caretBack;

        Item(String label, String kind, boolean callable) {
            this.label = label;
            this.kind = kind;
            this.insert = callable ? label + "()" : label;
            this.caretBack = callable ? 1 : 0;
        }
    }

    static final class Result {
        final int start;
        final int end;
        final List<Item> items;

        Result(int start, int end, List<Item> items) {
            this.start = start;
            this.end = end;
            this.items = items;
        }
    }

    private static final String KEYWORDS = "and as assert async await break case class continue def del elif else except False finally for from global if import in is lambda match nonlocal not or pass raise return True try while with yield None";
    private static final String BUILTIN_FUNCTIONS = "abs aiter all anext any ascii bin bool breakpoint bytearray bytes callable chr classmethod compile complex delattr dict dir divmod enumerate eval exec filter float format frozenset getattr globals hasattr hash help hex id input int isinstance issubclass iter len list locals map max memoryview min next object oct open ord pow print property range repr reversed round set setattr slice sorted staticmethod str sum super tuple type vars zip __import__";
    private static final String BUILTIN_VALUES = "Ellipsis NotImplemented __name__ __file__ __doc__";
    private static final String MODULES = "abc argparse asyncio collections csv dataclasses datetime functools importlib io itertools json logging math os pathlib random re shutil statistics string subprocess sys tempfile threading time typing unittest urllib warnings zipfile numpy torch";
    private static final Map<String, String> MEMBERS = new HashMap<>();
    private static final Pattern DEF = Pattern.compile("(?m)^\\s*(?:async\\s+)?def\\s+([\\p{L}_][\\p{L}\\p{N}_]*)\\s*\\(");
    private static final Pattern CLASS = Pattern.compile("(?m)^\\s*class\\s+([\\p{L}_][\\p{L}\\p{N}_]*)");
    private static final Pattern ASSIGN = Pattern.compile("(?m)^\\s*([\\p{L}_][\\p{L}\\p{N}_]*)\\s*(?::[^=\\n]+)?=(?!=)");
    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+([\\w.]+)(?:\\s+as\\s+(\\w+))?");
    private static final Pattern FROM_IMPORT = Pattern.compile("(?m)^\\s*from\\s+([\\w.]+)\\s+import\\s+([\\w]+)(?:\\s+as\\s+(\\w+))?");

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
        MEMBERS.put("math", "pi e tau inf nan sqrt exp log log10 sin cos tan asin acos atan atan2 floor ceil fabs factorial isclose isfinite hypot degrees radians prod sumprod");
        MEMBERS.put("os", "path getcwd chdir listdir scandir walk makedirs mkdir remove rename getenv environ sep name stat");
        MEMBERS.put("os.path", "join exists isfile isdir dirname basename abspath realpath split splitext expanduser getsize");
        MEMBERS.put("sys", "version version_info path modules platform argv executable stderr stdout stdin exit");
        MEMBERS.put("pathlib", "Path PurePath PurePosixPath");
        MEMBERS.put("json", "load loads dump dumps JSONDecoder JSONEncoder");
        MEMBERS.put("re", "compile search match fullmatch findall finditer sub split escape Pattern Match");
        MEMBERS.put("collections", "Counter defaultdict deque OrderedDict namedtuple ChainMap");
        MEMBERS.put("itertools", "chain combinations combinations_with_replacement count cycle repeat islice groupby permutations product accumulate");
    }

    private PythonCompletions() { }

    static Result suggest(String source, int cursor) {
        if (cursor < 0 || cursor > source.length()) return empty(cursor);
        int start = cursor;
        while (start > 0 && identifierPart(source.charAt(start - 1))) start--;
        if (start == cursor || !identifierStart(source.charAt(start))) return empty(cursor);
        if (cursor < source.length() && identifierPart(source.charAt(cursor))) return empty(cursor);
        if (insideStringOrComment(source, cursor)) return empty(cursor);
        String prefix = source.substring(start, cursor);
        String receiver = null;
        if (start > 0 && source.charAt(start - 1) == '.') {
            int dotStart = start - 1;
            int left = dotStart;
            while (left > 0 && (identifierPart(source.charAt(left - 1)) || source.charAt(left - 1) == '.')) left--;
            receiver = source.substring(left, dotStart);
            if (receiver.isEmpty() || receiver.startsWith(".") || receiver.endsWith("..")) return empty(cursor);
        }

        LinkedHashMap<String, Item> found = new LinkedHashMap<>();
        if (receiver == null) {
            addWords(found, KEYWORDS, "关键字", false);
            addWords(found, BUILTIN_FUNCTIONS, "内置函数", true);
            addWords(found, BUILTIN_VALUES, "内置名称", false);
            addWords(found, MODULES, "模块", false);
            addDeclarations(found, source);
        } else {
            String resolved = resolveReceiver(receiver, source);
            String words = MEMBERS.get(resolved);
            if (words != null) addWords(found, words, "模块成员", false);
        }
        ArrayList<Item> matches = new ArrayList<>();
        for (Item item : found.values()) {
            if (!item.label.equals(prefix) && item.label.startsWith(prefix)) matches.add(item);
        }
        matches.sort(Comparator.comparingInt((Item item) -> priority(item, prefix))
                .thenComparing(item -> item.label));
        if (matches.size() > 8) matches.subList(8, matches.size()).clear();
        return new Result(start, cursor, matches);
    }

    private static int priority(Item item, String prefix) {
        if (item.label.equals("print")) return 0;
        if (item.kind.equals("当前文件")) return 1;
        if (item.kind.equals("内置函数")) return 2;
        if (item.kind.equals("关键字")) return 3;
        return 4;
    }

    private static void addWords(Map<String, Item> result, String words, String kind, boolean callable) {
        for (String word : words.split(" ")) result.putIfAbsent(word, new Item(word, kind, callable));
    }

    private static void addDeclarations(Map<String, Item> result, String source) {
        // The editor accepts 2 MB files; bound the scan so typing stays responsive.
        String code = maskStringsAndComments(source.length() > 100_000
                ? source.substring(source.length() - 100_000) : source);
        addMatches(result, DEF.matcher(code), "当前文件", true, 1);
        addMatches(result, CLASS.matcher(code), "当前文件", true, 1);
        addMatches(result, ASSIGN.matcher(code), "当前文件", false, 1);
        Matcher imports = IMPORT.matcher(code);
        while (imports.find()) {
            String alias = imports.group(2) != null ? imports.group(2) : imports.group(1).split("\\.")[0];
            result.put(alias, new Item(alias, "导入", false));
        }
        Matcher fromImports = FROM_IMPORT.matcher(code);
        while (fromImports.find()) {
            String alias = fromImports.group(3) != null ? fromImports.group(3) : fromImports.group(2);
            result.put(alias, new Item(alias, "导入", false));
        }
    }

    private static void addMatches(Map<String, Item> result, Matcher matcher, String kind,
            boolean callable, int group) {
        while (matcher.find()) {
            String name = matcher.group(group);
            result.put(name, new Item(name, kind, callable));
        }
    }

    private static String resolveReceiver(String receiver, String source) {
        int dot = receiver.indexOf('.');
        String first = dot < 0 ? receiver : receiver.substring(0, dot);
        String rest = dot < 0 ? "" : receiver.substring(dot);
        String code = maskStringsAndComments(source.length() > 100_000
                ? source.substring(source.length() - 100_000) : source);
        Matcher imports = IMPORT.matcher(code);
        while (imports.find()) {
            String module = imports.group(1);
            String alias = imports.group(2) != null ? imports.group(2) : module.split("\\.")[0];
            if (alias.equals(first)) return (imports.group(2) == null ? first : module) + rest;
        }
        Matcher fromImports = FROM_IMPORT.matcher(code);
        while (fromImports.find()) {
            String alias = fromImports.group(3) != null ? fromImports.group(3) : fromImports.group(2);
            if (alias.equals(first)) return fromImports.group(1) + "." + fromImports.group(2) + rest;
        }
        return "";
    }

    private static boolean insideStringOrComment(String source, int cursor) {
        int quote = 0;
        boolean triple = false;
        boolean comment = false;
        for (int i = 0; i < cursor; i++) {
            char c = source.charAt(i);
            if (comment) { if (c == '\n') comment = false; continue; }
            if (quote != 0) {
                if (c == '\\') { i++; continue; }
                if (c == quote) {
                    if (triple) {
                        if (i + 2 < cursor && source.charAt(i + 1) == quote
                                && source.charAt(i + 2) == quote) { quote = 0; triple = false; i += 2; }
                    } else quote = 0;
                }
                continue;
            }
            if (c == '#') { comment = true; continue; }
            if (c == '\'' || c == '"') {
                quote = c;
                triple = i + 2 < cursor && source.charAt(i + 1) == c && source.charAt(i + 2) == c;
                if (triple) i += 2;
            }
        }
        return quote != 0 || comment;
    }

    private static String maskStringsAndComments(String source) {
        StringBuilder masked = new StringBuilder(source);
        int quote = 0;
        boolean triple = false;
        boolean comment = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\n') { comment = false; if (!triple) quote = 0; continue; }
            if (comment) { masked.setCharAt(i, ' '); continue; }
            if (quote != 0) {
                masked.setCharAt(i, ' ');
                if (c == '\\') { if (i + 1 < source.length()) masked.setCharAt(++i, ' '); continue; }
                if (c == quote) {
                    if (triple) {
                        if (i + 2 < source.length() && source.charAt(i + 1) == quote
                                && source.charAt(i + 2) == quote) {
                            masked.setCharAt(++i, ' ');
                            masked.setCharAt(++i, ' ');
                            quote = 0; triple = false;
                        }
                    } else quote = 0;
                }
                continue;
            }
            if (c == '#') { comment = true; masked.setCharAt(i, ' '); continue; }
            if (c == '\'' || c == '"') {
                quote = c;
                triple = i + 2 < source.length() && source.charAt(i + 1) == c && source.charAt(i + 2) == c;
                masked.setCharAt(i, ' ');
                if (triple) { masked.setCharAt(++i, ' '); masked.setCharAt(++i, ' '); }
            }
        }
        return masked.toString();
    }

    private static boolean identifierStart(char value) {
        return value == '_' || Character.isUnicodeIdentifierStart(value);
    }

    private static boolean identifierPart(char value) {
        return value == '_' || Character.isUnicodeIdentifierPart(value);
    }

    private static Result empty(int cursor) { return new Result(cursor, cursor, new ArrayList<>()); }
}
