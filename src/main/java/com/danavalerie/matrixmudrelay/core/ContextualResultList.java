package com.danavalerie.matrixmudrelay.core;

import java.util.List;

public record ContextualResultList(String title,
                                   List<ContextualResult> results,
                                   String emptyMessage,
                                   String footer) {
    public ContextualResultList {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public record ContextualResult(String label, String command, String mapCommand) {
    }
}
