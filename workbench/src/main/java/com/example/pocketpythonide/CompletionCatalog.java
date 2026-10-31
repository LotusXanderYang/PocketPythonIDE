package com.example.pocketpythonide;

/** Optional module symbols supplied by an installed execution product. */
public interface CompletionCatalog {
    CompletionCatalog EMPTY = new CompletionCatalog() {
        @Override public String modules() { return ""; }
        @Override public String members(String module) { return null; }
    };

    String modules();
    String members(String module);
}
