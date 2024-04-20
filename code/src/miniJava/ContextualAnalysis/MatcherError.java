package miniJava.ContextualAnalysis;

import miniJava.SyntacticAnalyzer.SourcePosition;

public class MatcherError extends RuntimeException {
    SourcePosition posn;
    public MatcherError(SourcePosition posn, String message) {
        super(message);
        this.posn = posn;
    }
}
