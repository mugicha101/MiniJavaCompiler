package miniJava.ContextualAnalysis;

import miniJava.AbstractSyntaxTrees.*;
import miniJava.SyntacticAnalyzer.SourcePosition;

import java.util.*;

public class IdTable {
    private static class MemberIdTable {
        public final ClassDecl classDecl;
        public final HashMap<String, FieldDecl> fieldIdTable = new HashMap<>();
        public final HashMap<String, SigGroup> methodIdTable = new HashMap<>();
        public final List<SigGroup> sigGroups = new ArrayList<>();

        MemberIdTable(ClassDecl classDecl) {
            this.classDecl = classDecl;
        }
    }

    public static class DeclScopeHandler {
        private final List<Declaration> declList = new ArrayList<>(); // list of decls in increasing scope nesting order
        private final List<Integer> scopeLevelList = new ArrayList<>(1); // list of scope levels
        public boolean locked; // true if currently being defined

        public DeclScopeHandler() {}

        public int size() {
            return declList.size();
        }

        public boolean isEmpty() {
            return declList.isEmpty();
        }

        public void push(Declaration decl, int scopeLevel) throws MatcherError {
            if (!scopeLevelList.isEmpty() && scopeLevelList.get(scopeLevelList.size()-1) == scopeLevel) {
                throw new MatcherError(decl.posn, String.format("Multiple definitions for identifier %s", decl.name));
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

    private final Map<String, MemberIdTable> classIdTable = new HashMap<>();
    private final Map<String, DeclScopeHandler> idTable = new HashMap<>();
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
            throw new MatcherError(decl.posn, String.format("Multiple definitions for class %s", decl.name));
        classIdTable.put(decl.name, new MemberIdTable(decl));
    }

    public void addClassMembers(ClassDecl decl) {
        for (FieldDecl fieldDecl : decl.fieldDeclList)
            addFieldDecl(decl.name, fieldDecl);
        for (MethodDecl methodDecl : decl.methodDeclList) {
            addMethodDecl(decl.name, methodDecl);
        }
    }

    private void addFieldDecl(String className, FieldDecl decl) {
        System.out.printf("FIELD: %s.%s\n", className, decl.name);
        HashMap<String, FieldDecl> fieldIdTable = classIdTable.get(className).fieldIdTable;
        if (fieldIdTable.containsKey(decl.name))
            throw new MatcherError(decl.posn, String.format("Multiple definitions for field %s.%s", className, decl.name));
        fieldIdTable.put(decl.name, decl);
    }

    private void addMethodDecl(String className, MethodDecl decl) {
        System.out.printf("METHOD: %s.%s\n", className, decl.name);
        String baseName = decl.name.substring(0, decl.name.indexOf('('));
        MemberIdTable memberIdTable = classIdTable.get(className);
        Map<String, SigGroup> methodIdTable = memberIdTable.methodIdTable;
        if (!methodIdTable.containsKey(baseName)) {
            SigGroup sigGroup = new SigGroup(baseName, decl.parent);
            methodIdTable.put(baseName, sigGroup);
            memberIdTable.sigGroups.add(sigGroup);
        }
        SigGroup sigGroup = methodIdTable.get(baseName);
        if (sigGroup.sigs.contains(decl.signature))
            throw new MatcherError(decl.posn, String.format("Multiple definitions for method %s.%s", className, decl.signature));
        sigGroup.add(decl.signature);
    }

    public Declaration getScopedDecl(SourcePosition posn, String name) {
        if (!idTable.containsKey(name))
            throw new MatcherError(posn, String.format("Undeclared identifier %s", name));
        DeclScopeHandler handler = idTable.get(name);
        if (handler.locked)
            throw new MatcherError(posn, String.format("Cannot reference variable %s within its declaration statement", name));
        return handler.getLast();
    }

    // returns type FieldDecl if member is a field decl and type SigGroupDecl if member is a method decl
    public MemberDecl getClassMember(SourcePosition posn, String className, String memberName) {
        if (!classIdTable.containsKey(className))
            throw new MatcherError(posn, String.format("Undeclared class %s", className));
        MemberIdTable memberIdTable = classIdTable.get(className);
        if (memberIdTable.fieldIdTable.containsKey(memberName))
            return memberIdTable.fieldIdTable.get(memberName);
        if (memberIdTable.methodIdTable.containsKey(memberName))
            return memberIdTable.methodIdTable.get(memberName);
        throw new MatcherError(posn, String.format("Undeclared member %s.%s", className, memberName));
    }

    public ClassDecl getClassDecl(SourcePosition posn, String className) {
        if (!classIdTable.containsKey(className))
            throw new MatcherError(posn, String.format("Undeclared class %s", className));
        return classIdTable.get(className).classDecl;
    }

    public FieldDecl getFieldDecl(SourcePosition posn, String className, String fieldName) {
        if (!classIdTable.containsKey(className))
            throw new MatcherError(posn, String.format("Undeclared class %s", className));
        MemberIdTable memberIdTable = classIdTable.get(className);
        if (!memberIdTable.fieldIdTable.containsKey(fieldName))
            throw new MatcherError(posn, String.format("Undeclared field %s.%s", className, fieldName));
        return memberIdTable.fieldIdTable.get(fieldName);
    }

    public SigGroup getMethodSignatures(SourcePosition posn, String className, String methodName) {
        if (!classIdTable.containsKey(className))
            throw new MatcherError(posn, String.format("Undeclared class %s", className));
        MemberIdTable memberIdTable = classIdTable.get(className);
        if (!memberIdTable.methodIdTable.containsKey(methodName))
            throw new MatcherError(posn, String.format("Undeclared method %s.%s", className, methodName));
        return memberIdTable.methodIdTable.get(methodName);
    }

    public List<SigGroup> getClassSigGroups(SourcePosition posn, String className) {
        if (!classIdTable.containsKey(className))
            throw new MatcherError(posn, String.format("Undeclared class %s", className));
        return classIdTable.get(className).sigGroups;
    }

    // lock/unlock var decl methods assume variable already added to scope
    public void lockVarDecl(VarDecl decl) {
        idTable.get(decl.name).locked = true;
    }

    public void unlockVarDecl(VarDecl decl) {
        idTable.get(decl.name).locked = false;
    }
}
