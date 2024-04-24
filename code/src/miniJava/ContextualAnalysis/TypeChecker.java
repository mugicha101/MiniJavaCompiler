package miniJava.ContextualAnalysis;

import miniJava.AbstractSyntaxTrees.*;

import java.util.*;

public class TypeChecker {
    private TypeChecker() {}
    public static String typeStr(TypeDenoter type) {
        if (type == null) return "<base-class-type>";
        switch (type.typeKind) {
            case INT:
                return "int";
            case LONG:
                return "long";
            case FLOAT:
                return "float";
            case DOUBLE:
                return "double";
            case CHAR:
                return "char";
            case BOOLEAN:
                return "boolean";
            case CLASS:
                return ((ClassType)type).className.spelling;
            case ARRAY:
                return typeStr(((ArrayType)type).eltType) + "[]";
            case VOID:
                return "void";
            case UNSUPPORTED:
                return "<unsupported-type>";
            default:
                return "<error-type>";
        }
    }
    public static boolean typeMatches(TypeDenoter actual, TypeDenoter... expected) {
        boolean isClass = actual.typeKind == TypeKind.CLASS;
        if (actual.typeKind == TypeKind.UNSUPPORTED) return false;
        String tsAct = typeStr(actual);
        for (TypeDenoter td : expected) {
            if (
                    isClass && td.typeKind == TypeKind.CLASS
                            && (
                            ((ClassType) actual).className.spelling.equals("null")
                                    || ((ClassType) td).className.spelling.equals("null")
                    )
            ) return true;
            String tsExp = typeStr(td);
            if (tsAct.equals(tsExp)) return true;
        }
        return false;
    }

    // checks if b is a subclass of a
    // note: a is counted as a subclass of itself
    public static boolean ancestorOf(ClassDecl a, ClassDecl b) {
        if (a.name.equals(b.name)) return true;
        if (a.hierarchyDepth > b.hierarchyDepth) return false;
        while (b.hierarchyDepth > a.hierarchyDepth)
            b = b.parentDecl;
        return a.name.equals(b.name);
    }

    public static boolean validCast(IdTable idTable, TypeDenoter srcType, TypeDenoter castType, boolean explicit) {
        if (srcType == null || castType == null)
            return false;

        // cast class types
        if (srcType instanceof ClassType && castType instanceof ClassType) {
            ClassDecl src = idTable.getClassDecl(srcType.posn, ((ClassType)srcType).className.spelling);
            ClassDecl dst = idTable.getClassDecl(castType.posn, ((ClassType)castType).className.spelling);
            return ancestorOf(dst, src) || (explicit && ancestorOf(src, dst));
        }

        // cast base types
        if (srcType instanceof BaseType && castType instanceof BaseType) {
            TypeKind src = srcType.typeKind;
            TypeKind dst = castType.typeKind;
            if (src == dst) return true;
            if (src == TypeKind.CHAR)
                return dst == TypeKind.INT || dst == TypeKind.LONG || (explicit && (dst == TypeKind.FLOAT || dst == TypeKind.DOUBLE));
            if (src == TypeKind.INT)
                return dst == TypeKind.LONG || (explicit && (dst == TypeKind.FLOAT || dst == TypeKind.DOUBLE || dst == TypeKind.CHAR));
            if (src == TypeKind.LONG)
                return explicit && (dst == TypeKind.INT || dst == TypeKind.FLOAT || dst == TypeKind.DOUBLE || dst == TypeKind.CHAR);
            if (src == TypeKind.FLOAT)
                return dst == TypeKind.DOUBLE || (explicit && (dst == TypeKind.INT || dst == TypeKind.LONG || dst == TypeKind.CHAR));
            if (src == TypeKind.DOUBLE)
                return explicit && (dst == TypeKind.INT || dst == TypeKind.LONG || dst == TypeKind.FLOAT || dst == TypeKind.CHAR);
        }
        return false;
    }

    // check if src implicitly casts to dst
    public static boolean validCast(IdTable idTable, Signature src, Signature dst) {
        if (src.size() != dst.size()) return false;
        for (int i = 0; i < src.size(); ++i) {
            if (!validCast(idTable, src.argTypes.get(i), dst.argTypes.get(i), false))
                return false;
        }
        return true;
    }
}
