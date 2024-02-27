package miniJava.ContextualAnalysis;

import miniJava.SyntacticAnalyzer.SourcePosition;

public class IdentificationError extends RuntimeException {
    SourcePosition posn;
    public IdentificationError(SourcePosition posn, String message) {
        super(message);
        this.posn = posn;
    }
}
