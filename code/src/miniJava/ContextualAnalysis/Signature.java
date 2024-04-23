package miniJava.ContextualAnalysis;

import miniJava.AbstractSyntaxTrees.MethodDecl;
import miniJava.AbstractSyntaxTrees.SigGroup;
import miniJava.AbstractSyntaxTrees.TypeDenoter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// unique identifier of a method
public class Signature {
    public String name = "";
    public SigGroup group = null;
    public final MethodDecl decl;
    public final List<TypeDenoter> argTypes = new ArrayList<>();
    public int size() {
        return argTypes.size();
    }
    public Signature(MethodDecl decl) {
        this.decl = decl;
    }

    public int hashCode() {
        return Objects.hash(name.hashCode(), argTypes.hashCode());
    }

    public String toString() {
        StringBuilder res = new StringBuilder(name);
        res.append('(');
        boolean first = true;
        for (TypeDenoter type : argTypes) {
            if (first) first = false;
            else res.append(',');
            res.append(TypeChecker.typeStr(type));
        }
        res.append(')');
        return res.toString();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Signature && obj.toString().equals(toString());
    }
}
