package miniJava.ContextualAnalysis;

import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.ErrorReporter;

// handles contextual analysis
public class Matcher {
    ErrorReporter errors;
    public Matcher(ErrorReporter errors) {
        this.errors = errors;
    }
    public void match(Package prog) {
        IdTable scopedIdTable = new IdTable();
        ScopedIdentification si = new ScopedIdentification(errors);
        try {
            prog.visit(si, scopedIdTable);
        } catch (IdentificationError idErr) {
            errors.clear();
            errors.reportError(idErr.posn, idErr.getMessage());
        }
    }
}
