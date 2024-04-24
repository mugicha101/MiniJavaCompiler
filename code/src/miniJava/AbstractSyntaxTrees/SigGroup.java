package miniJava.AbstractSyntaxTrees;

import miniJava.ContextualAnalysis.Signature;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

// For resolving MethodDecls which have the same name but different signatures
// Not visitable
public class SigGroup extends MemberDecl {
    public final Set<Signature> sigs = new HashSet<>();
    public final ClassDecl parent;
    public boolean lastRefStatic; // true if last reference was from static context
    public ClassDecl lastContext; // context of last reference

    public SigGroup(String name, ClassDecl parent) {
        super(false, false, null, name, null);
        this.parent = parent;
    }

    public void add(Signature sig) {
        sigs.add(sig);
        sig.group = this;
    }

    @Override
    public <A, R> R visit(Visitor<A, R> v, A o) {
        throw new RuntimeException("SigGroup not visitable");
    }

    public int hashCode() {
        return Objects.hash(name.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SigGroup && ((SigGroup)obj).name.equals(name);
    }
}
