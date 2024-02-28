package miniJava.ContextualAnalysis;

import miniJava.AbstractSyntaxTrees.*;
import miniJava.SyntacticAnalyzer.SourcePosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

public class IdTable {
    private static class MemberIdTable {
        public final ClassDecl classDecl;
        public final HashMap<String, FieldDecl> fieldIdTable = new HashMap<>();
        public final HashMap<String, MethodDecl> methodIdTable = new HashMap<>();

        MemberIdTable(ClassDecl classDecl) {
            this.classDecl = classDecl;
        }
    }

    public static class DeclScopeHandler {
        private final List<Declaration> declList = new ArrayList<>(); // list of decls in increasing scope nesting order
        private final List<Integer> scopeLevelList = new ArrayList<>(1); // list of scope levels

        public DeclScopeHandler() {}

        public int size() {
            return declList.size();
        }

        public boolean isEmpty() {
            return declList.isEmpty();
        }

        public void push(Declaration decl, int scopeLevel) throws IdentificationError {
            if (!scopeLevelList.isEmpty() && scopeLevelList.get(scopeLevelList.size()-1) == scopeLevel) {
                throw new IdentificationError(decl.posn, String.format("Multiple definitions for variable %s", decl.name));
            }
            declList.add(decl);
            scopeLevelList.add(scopeLevel);
        }

        public void pop() {
            declList.remove(declList.size()-1);
            scopeLevelList.remove(scopeLevelList.size()-1);
        }

        public Declaration getLast() {
            return declList.get(declList.size()-1);
        }
    }

    private HashMap<String, MemberIdTable> classIdTable = new HashMap<>();
    private final HashMap<String, DeclScopeHandler> idTable = new HashMap<>();
    private final Stack<List<String>> idStack = new Stack<>();
    private int scopeLevel = 0;

    public IdTable() {
        idStack.push(new ArrayList<>());
    }

    void openScope() {
        idStack.push(new ArrayList<>());
        scopeLevel++;
    }

    void closeScope() {
        for (String id : idStack.lastElement()) {
            DeclScopeHandler handler = idTable.get(id);
            handler.pop();
            if (handler.isEmpty()) idTable.remove(id);
        }
        idStack.pop();
        scopeLevel--;
    }

    public void addScopedDecl(Declaration decl) {
        if (!idTable.containsKey(decl.name))
            idTable.put(decl.name, new DeclScopeHandler());
        idTable.get(decl.name).push(decl, Math.min(scopeLevel, 2));
        idStack.lastElement().add(decl.name);
    }

    public void addClassDecl(ClassDecl decl) {
        if (classIdTable.containsKey(decl.name))
            throw new IdentificationError(decl.posn, String.format("Multiple definitions for class %s", decl.name));
        classIdTable.put(decl.name, new MemberIdTable(decl));
        for (FieldDecl fieldDecl : decl.fieldDeclList)
            addFieldDecl(decl.name, fieldDecl);
        for (MethodDecl methodDecl : decl.methodDeclList)
            addMethodDecl(decl.name, methodDecl);
    }

    private void addFieldDecl(String className, FieldDecl decl) {
        HashMap<String, FieldDecl> fieldIdTable = classIdTable.get(className).fieldIdTable;
        if (fieldIdTable.containsKey(decl.name))
            throw new IdentificationError(decl.posn, String.format("Multiple definitions for field %s.%s", className, decl.name));
        fieldIdTable.put(decl.name, decl);
    }

    private void addMethodDecl(String className, MethodDecl decl) {
        HashMap<String, MethodDecl> methodIdTable = classIdTable.get(className).methodIdTable;
        if (methodIdTable.containsKey(decl.name))
            throw new IdentificationError(decl.posn, String.format("Multiple definitions for method %s.%s", className, decl.name));
        methodIdTable.put(decl.name, decl);
    }

    public Declaration getScopedDecl(SourcePosition posn, String name) {
        if (!idTable.containsKey(name))
            throw new IdentificationError(posn, String.format("Undeclared variable %s", name));
        return idTable.get(name).getLast();
    }

    public MemberDecl getClassMember(SourcePosition posn, String className, String memberName) {
        if (!classIdTable.containsKey(className))
            throw new IdentificationError(posn, String.format("Undeclared class %s", className));
        MemberIdTable memberIdTable = classIdTable.get(className);
        if (memberIdTable.fieldIdTable.containsKey(memberName))
            return memberIdTable.fieldIdTable.get(memberName);
        if (memberIdTable.methodIdTable.containsKey(memberName))
            return memberIdTable.methodIdTable.get(memberName);
        throw new IdentificationError(posn, String.format("Undeclared member %s.%s", className, memberName));
    }

    public ClassDecl getClassDecl(SourcePosition posn, String className) {
        if (!classIdTable.containsKey(className))
            throw new IdentificationError(posn, String.format("Undeclared class %s", className));
        return classIdTable.get(className).classDecl;
    }

    public FieldDecl getFieldDecl(SourcePosition posn, String className, String fieldName) {
        if (!classIdTable.containsKey(className))
            throw new IdentificationError(posn, String.format("Undeclared class %s", className));
        MemberIdTable memberIdTable = classIdTable.get(className);
        if (!memberIdTable.fieldIdTable.containsKey(fieldName))
            throw new IdentificationError(posn, String.format("Undeclared field %s.%s", className, fieldName));
        return memberIdTable.fieldIdTable.get(fieldName);
    }

    public MethodDecl getMethodDecl(SourcePosition posn, String className, String methodName) {
        if (!classIdTable.containsKey(className))
            throw new IdentificationError(posn, String.format("Undeclared class %s", className));
        MemberIdTable memberIdTable = classIdTable.get(className);
        if (!memberIdTable.methodIdTable.containsKey(methodName))
            throw new IdentificationError(posn, String.format("Undeclared method %s.%s", className, methodName));
        return memberIdTable.methodIdTable.get(methodName);
    }
}
