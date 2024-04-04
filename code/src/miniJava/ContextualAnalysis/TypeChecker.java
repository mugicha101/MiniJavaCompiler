package miniJava.ContextualAnalysis;

import miniJava.AbstractSyntaxTrees.ArrayType;
import miniJava.AbstractSyntaxTrees.ClassType;
import miniJava.AbstractSyntaxTrees.TypeDenoter;
import miniJava.AbstractSyntaxTrees.TypeKind;

public class TypeChecker {
    private TypeChecker() {}
    public static String typeStr(TypeDenoter type) {
        if (type == null) return "<base-class-type>";
        switch (type.typeKind) {
            case INT:
                return "int";
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
                            ((ClassType)actual).className.spelling.equals("null")
                                    || ((ClassType)td).className.spelling.equals("null")
                    )
            ) return true;
            String tsExp = typeStr(td);
            if (tsAct.equals(tsExp)) return true;
        }
        return false;
    }
}
