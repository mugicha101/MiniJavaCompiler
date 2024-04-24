package miniJava.ContextualAnalysis;

import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.ErrorReporter;
import miniJava.SyntacticAnalyzer.SourcePosition;
import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenType;

import java.util.*;

// references return Declaration
// identifiers return Declaration
// expressions return TypeDenoter
// types return TypeDenoter
// return statements return TypeDenoter
public class Matcher implements Visitor<IdTable, Object> {
    static final TypeDenoter INT_TYPE = new BaseType(TypeKind.INT, null);
    static final TypeDenoter LONG_TYPE = new BaseType(TypeKind.LONG, null);
    static final TypeDenoter FLOAT_TYPE = new BaseType(TypeKind.FLOAT, null);
    static final TypeDenoter DOUBLE_TYPE = new BaseType(TypeKind.DOUBLE, null);
    static final TypeDenoter CHAR_TYPE = new BaseType(TypeKind.CHAR, null);
    static final TypeDenoter BOOLEAN_TYPE = new BaseType(TypeKind.BOOLEAN, null);
    static final TypeDenoter UNSUPPORTED_TYPE = new BaseType(TypeKind.UNSUPPORTED, null);
    static final TypeDenoter NULL_TYPE = new ClassType(new Identifier(new Token(TokenType.NullLiteral, "null", -1, -1)), null);
    static final TypeDenoter VOID_TYPE = new ClassType(new Identifier(new Token(TokenType.VoidType, "void", -1, -1)), null);
    static final SourcePosition PREDEF_POSN = new SourcePosition(-1, -1);
    static final FieldDecl ARR_LENGTH_DECL = new FieldDecl(false, false, INT_TYPE, "length", new SourcePosition(-1, -1));
    static {
        ARR_LENGTH_DECL.specialTag = "array.length";
        ARR_LENGTH_DECL.memOffset = 0;
    }
    public ClassDecl activeClass;
    public ClassDecl activeClassParent;
    public MethodDecl activeMethod;
    public final ErrorReporter errors;
    boolean staticActive;
    public Matcher(ErrorReporter errors) {
        this.errors = errors;
    }

    void checkTypeMatch(String context, SourcePosition posn, TypeDenoter actual, TypeDenoter... expected) {
        assert(expected.length > 0);
        if (TypeChecker.typeMatches(actual, expected)) return;
        StringBuilder expStr = new StringBuilder();
        expStr.append(TypeChecker.typeStr(expected[0]));
        for (int i = 1; i < expected.length; ++i) {
            expStr.append(" or ");
            expStr.append(TypeChecker.typeStr(expected[i]));
        }
        errors.reportError(posn, String.format("Type mismatch in %s: expected %s, but got %s", context, expStr, TypeChecker.typeStr(actual)));
    }

    // takes in decl, returns name of class
    // if decl is class returns its name
    // if decl is object returns its class name
    // if decl is array return <ARRAY>
    // otherwise return null
    String getClassName(Declaration decl) {
        if (decl.type == null) return decl.name;
        if (decl.type.typeKind == TypeKind.CLASS)
            return ((ClassType)decl.type).className.spelling;
        if (decl.type.typeKind == TypeKind.ARRAY)
            return "<ARRAY>";
        return null;
    }

    private ClassDecl objectClassDecl;

    void addPredefined(Package prog) {
        // Object
        {
            objectClassDecl = new ClassDecl("Object", new FieldDeclList(), new MethodDeclList(), PREDEF_POSN);
            prog.classDeclList.add(objectClassDecl);
        }
        // System.out.println
        {
            ClassDecl SystemDecl = new ClassDecl("System", new FieldDeclList(), new MethodDeclList(), PREDEF_POSN);
            SystemDecl.fieldDeclList.add(new FieldDecl(false, true, new ClassType(new Identifier(new Token(TokenType.Identifier, "_PrintStream", PREDEF_POSN.line, PREDEF_POSN.offset)), PREDEF_POSN), "out", PREDEF_POSN));
            ClassDecl PrintStreamDecl = new ClassDecl("_PrintStream", new FieldDeclList(), new MethodDeclList(), PREDEF_POSN);
            MethodDecl printlnMethod = new MethodDecl(new FieldDecl(false, false, new BaseType(TypeKind.VOID, PREDEF_POSN), "println", PREDEF_POSN), new ParameterDeclList(), new StatementList(), PREDEF_POSN);
            printlnMethod.specialTag = "System.out.println";
            printlnMethod.parameterDeclList.add(new ParameterDecl(new BaseType(TypeKind.INT, PREDEF_POSN), "n", PREDEF_POSN));
            PrintStreamDecl.methodDeclList.add(printlnMethod);
            ClassDecl StringDecl = new ClassDecl("String", new FieldDeclList(), new MethodDeclList(), PREDEF_POSN);
            StringDecl.unsupported = true;
            prog.classDeclList.add(SystemDecl);
            prog.classDeclList.add(PrintStreamDecl);
            prog.classDeclList.add(StringDecl);
        }
    }

    public void match(AST ast) {
        activeClass = null;
        activeMethod = null;
        staticActive = false;
        IdTable idTable = new IdTable();
        try {
            ast.visit(this, idTable);
        } catch (MatcherError idErr) {
            errors.clear();
            errors.reportError(idErr.posn, idErr.getMessage());
            throw idErr;
        }
    }

    @Override
    public Object visitPackage(Package prog, IdTable arg) {
        // add predefined objects
        addPredefined(prog);

        // add to lvl 0 and 1 scopes and determine signatures
        for (ClassDecl classDecl : prog.classDeclList) {
            // add class decl to id table
            arg.addClassDecl(classDecl);
            arg.addScopedDecl(classDecl);

            // assign parents to members
            for (MethodDecl method : classDecl.methodDeclList) {
                method.parent = classDecl;
            }
            for (FieldDecl field : classDecl.fieldDeclList) {
                field.parent = classDecl;
            }

            // assign signatures to methods
            for (MethodDecl method : classDecl.methodDeclList) {
                for (ParameterDecl param : method.parameterDeclList) {
                    method.signature.argTypes.add(param.type);
                }
            }

            // update method name to signature name
            for (MethodDecl method : classDecl.methodDeclList) {
                method.name = method.signature.toString();
            }
        }

        // determine relationships of classes
        for (ClassDecl classDecl : prog.classDeclList) {
            if (classDecl == objectClassDecl) continue;
            classDecl.parentDecl = arg.getClassDecl(classDecl.parent.posn, classDecl.parent.spelling);
            classDecl.parentDecl.subclasses.add(classDecl);
            System.out.printf("%s extends %s\n", classDecl.name, classDecl.parentDecl.name);
        }
        objectClassDecl.parentDecl = objectClassDecl;

        // detect cycles (not traversable from Object)
        int next = 0;
        objectClassDecl.hierarchyDepth = 0;
        prog.topoOrder.add(objectClassDecl);
        while (next < prog.topoOrder.size()) {
            ClassDecl classDecl = prog.topoOrder.get(next++);
            for (ClassDecl subclassDecl : classDecl.subclasses) {
                subclassDecl.hierarchyDepth = classDecl.hierarchyDepth + 1;
                prog.topoOrder.add(subclassDecl);
            }
        }
        for (ClassDecl classDecl : prog.classDeclList) {
            if (classDecl.hierarchyDepth != -1) continue;
            List<String> cycle = new ArrayList<>();
            ClassDecl curr = classDecl;
            cycle.add(classDecl.name);
            while (curr.parentDecl != classDecl) {
                cycle.add(curr.parentDecl.name);
                curr = classDecl.parentDecl;
            }
            cycle.add(classDecl.name);
            throw new MatcherError(classDecl.posn, String.format("Cyclical inheritance: %s", String.join(" extends ", cycle)));
        }

        // update classDecl.fieldDeclList and classDecl.methodDeclList to inherit parent fields
        for (ClassDecl classDecl : prog.topoOrder) {
            if (classDecl == objectClassDecl) continue;
            ClassDecl parentDecl = classDecl.parentDecl;

            // inherit and override methods
            MethodDeclList updatedMethods = new MethodDeclList();
            Map<String, MethodDecl> nonOverriderMethods = new HashMap<>();
            for (int i = 0; i < classDecl.methodDeclList.size(); ++i) {
                MethodDecl methodDecl = classDecl.methodDeclList.get(i);
                nonOverriderMethods.put(methodDecl.name, methodDecl);
            }
            for (int i = 0; i < parentDecl.methodDeclList.size(); ++i) {
                MethodDecl inherited = parentDecl.methodDeclList.get(i);
                if (nonOverriderMethods.containsKey(inherited.name)) {
                    // override parent method
                    MethodDecl overrider = nonOverriderMethods.get(inherited.name);
                    nonOverriderMethods.remove(inherited.name);
                    updatedMethods.add(overrider);
                } else {
                    // inherit parent method
                    updatedMethods.add(inherited);
                }
            }
            for (int i = 0; i < classDecl.methodDeclList.size(); ++i) {
                MethodDecl method = classDecl.methodDeclList.get(i);
                if (!nonOverriderMethods.containsKey(method.name))
                    continue;

                // add method that doesn't override inherited method
                updatedMethods.add(method);
            }

            // inherit fields (collisions checked during classdecl visit)
            FieldDeclList updatedFields = new FieldDeclList();
            for (int i = 0; i < parentDecl.fieldDeclList.size(); ++i) {
                FieldDecl inherited = parentDecl.fieldDeclList.get(i);
                updatedFields.add(inherited);
            }
            for (int i = 0; i < classDecl.fieldDeclList.size(); ++i) {
                FieldDecl fieldDecl = classDecl.fieldDeclList.get(i);
                updatedFields.add(fieldDecl);
            }

            // apply update
            classDecl.methodDeclList = updatedMethods;
            classDecl.fieldDeclList = updatedFields;
        }

        // add class members to id table
        for (ClassDecl classDecl : prog.classDeclList) {
            arg.addClassMembers(classDecl);
        }

        // visit class decls
        for (ClassDecl classDecl : prog.classDeclList)
            classDecl.visit(this, arg);
        return null;
    }

    @Override
    public Object visitClassDecl(ClassDecl cd, IdTable arg) {
        activeClass = cd;
        arg.openScope();

        // add members to scope
        for (FieldDecl fieldDecl : cd.fieldDeclList) {
            if (fieldDecl.isPrivate && fieldDecl.parent != cd)
                continue; // skip private parent fields
            arg.addScopedDecl(fieldDecl); // field name --> method decl
        }
        for (MethodDecl methodDecl : cd.methodDeclList) {
            if (methodDecl.isPrivate && methodDecl.parent != cd)
                continue; // skip private parent methods
            arg.addScopedDecl(methodDecl); // method signature string --> method decl
        }
        for (SigGroup sigGroup : arg.getClassSigGroups(cd.posn, cd.name)) {
            arg.addScopedDecl(sigGroup); // method name --> signature group
        }

        // visit
        for (FieldDecl fieldDecl : cd.fieldDeclList) {
            fieldDecl.visit(this, arg);
        }
        for (MethodDecl methodDecl : cd.methodDeclList) {
            methodDecl.visit(this, arg);
        }

        arg.closeScope();
        activeClass = null;
        return null;
    }

    @Override
    public Object visitFieldDecl(FieldDecl fd, IdTable arg) {
        fd.type.visit(this, arg);
        return null;
    }

    @Override
    public Object visitMethodDecl(MethodDecl md, IdTable arg) {
        activeMethod = md;
        md.type.visit(this, arg);
        staticActive = md.isStatic;
        arg.openScope();
        for (ParameterDecl pd : md.parameterDeclList)
            pd.visit(this, arg);
        TypeDenoter lastRetType = null;
        for (Statement stmt : md.statementList) {
            lastRetType = (TypeDenoter)stmt.visit(this, arg);
        }
        if (md.type.typeKind != TypeKind.VOID && lastRetType == null)
            errors.reportError(md.posn, String.format("Method %s.%s has no last return statement", activeClass.name, md.name));
        arg.closeScope();
        activeMethod = null;
        staticActive = false;
        return null;
    }

    @Override
    public Object visitParameterDecl(ParameterDecl pd, IdTable arg) {
        arg.addScopedDecl(pd);
        return pd.type.visit(this, arg);
    }

    @Override
    public Object visitVarDecl(VarDecl decl, IdTable arg) {
        arg.addScopedDecl(decl);
        return decl.type.visit(this, arg);
    }

    @Override
    public Object visitBaseType(BaseType type, IdTable arg) {
        return type;
    }

    @Override
    public Object visitClassType(ClassType type, IdTable arg) {
        ClassDecl decl = arg.getClassDecl(type.posn, type.className.spelling);
        return decl.unsupported ? UNSUPPORTED_TYPE : type;
    }

    @Override
    public Object visitArrayType(ArrayType type, IdTable arg) {
        type.eltType.visit(this, arg);
        return type;
    }

    @Override
    public Object visitBlockStmt(BlockStmt stmt, IdTable arg) {
        arg.openScope();
        for (Statement nestedStmt : stmt.sl)
            nestedStmt.visit(this, arg);
        arg.closeScope();
        return null;
    }

    @Override
    public Object visitVardeclStmt(VarDeclStmt stmt, IdTable arg) {
        TypeDenoter declType = (TypeDenoter)stmt.varDecl.visit(this, arg);
        arg.lockVarDecl(stmt.varDecl);
        TypeDenoter exprType = (TypeDenoter)stmt.initExp.visit(this, arg);
        arg.unlockVarDecl(stmt.varDecl);
        if (!TypeChecker.typeMatches(exprType, declType) && TypeChecker.validCast(arg, exprType, declType, false)) {
            stmt.initExp = new CastExpr(declType, stmt.initExp, stmt.posn);
            stmt.initExp.resultType = declType;
            exprType = declType;
        }
        checkTypeMatch("variable declaration", stmt.posn, exprType, declType);
        return null;
    }

    void checkIsTyped(SourcePosition posn, Declaration decl) {
        if (decl instanceof ClassDecl)
            throw new MatcherError(posn, String.format("Class %s has no type", decl.name));
        if (decl instanceof MethodDecl)
            throw new MatcherError(posn, String.format("Method %s has no type", decl.name));
    }

    void checkIsCallable(SourcePosition posn, Declaration decl) {
        if (!(decl instanceof SigGroup))
            throw new MatcherError(posn, String.format("%s is not callable", decl.name));
    }

    @Override
    public Object visitAssignStmt(AssignStmt stmt, IdTable arg) {
        TypeDenoter exprType = (TypeDenoter)stmt.val.visit(this, arg);
        Declaration refDecl = (Declaration)stmt.ref.visit(this, arg);
        checkIsTyped(refDecl.posn, refDecl);
        if (!TypeChecker.typeMatches(exprType, refDecl.type) && TypeChecker.validCast(arg, exprType, refDecl.type, false)) {
            stmt.val = new CastExpr(refDecl.type, stmt.val, stmt.posn);
            stmt.val.resultType = refDecl.type;
            exprType = refDecl.type;
        }
        checkTypeMatch("assign statement", stmt.posn, exprType, refDecl.type);
        if (refDecl.specialTag != null && refDecl.specialTag.equals("array.length")) {
            throw new MatcherError(stmt.posn, "Array length cannot be assigned to");
        }
        return null;
    }

    @Override
    public Object visitIxAssignStmt(IxAssignStmt stmt, IdTable arg) {
        TypeDenoter exprType = (TypeDenoter) stmt.exp.visit(this, arg);
        TypeDenoter ixType = (TypeDenoter) stmt.ix.visit(this, arg);
        Declaration refDecl = (Declaration) stmt.ref.visit(this, arg);
        checkTypeMatch("array index", stmt.posn, ixType, INT_TYPE);
        if (refDecl.type == null || refDecl.type.typeKind != TypeKind.ARRAY) {
            errors.reportError(stmt.posn, String.format("Type mismatch in array element assignment statement: %s is not an array", TypeChecker.typeStr(refDecl.type)));
            return null;
        }
        TypeDenoter eltType = ((ArrayType) refDecl.type).eltType;
        if (!TypeChecker.typeMatches(exprType, eltType) && TypeChecker.validCast(arg, exprType, eltType, false)) {
            stmt.exp = new CastExpr(eltType, stmt.exp, stmt.posn);
            stmt.exp.resultType = eltType;
            exprType = eltType;
        }
        checkTypeMatch("array element assignment", stmt.posn, exprType, eltType);
        return null;
    }

    @Override
    public Object visitCallStmt(CallStmt stmt, IdTable arg) {
        Declaration decl = (Declaration)stmt.methodRef.visit(this, arg);
        checkIsCallable(stmt.methodRef.posn, decl);
        SigGroup sigGroup = (SigGroup)decl;
        visitCallArgs(stmt.methodRef, stmt.argList, sigGroup, arg, stmt.posn);
        return null;
    }

    @Override
    public Object visitReturnStmt(ReturnStmt stmt, IdTable arg) {
        TypeDenoter retType = stmt.returnExpr == null ? VOID_TYPE : (TypeDenoter)stmt.returnExpr.visit(this, arg);
        TypeDenoter mRetType = activeMethod.type;
        if (!TypeChecker.typeMatches(retType, mRetType) && TypeChecker.validCast(arg, retType, mRetType, false)) {
            stmt.returnExpr = new CastExpr(mRetType, stmt.returnExpr, stmt.returnExpr.posn);
            stmt.returnExpr.resultType = mRetType;
            retType = mRetType;
        }
        checkTypeMatch(String.format("method %s.%s return statement", activeClass.name, activeMethod.name), stmt.posn, retType, mRetType);
        return retType;
    }

    void checkIsolatedVarDeclStmt(Statement stmt) {
        if (stmt instanceof VarDeclStmt)
            throw new MatcherError(stmt.posn, String.format("Variable %s defined in isolation", ((VarDeclStmt)stmt).varDecl.name));
    }

    @Override
    public Object visitIfStmt(IfStmt stmt, IdTable arg) {
        TypeDenoter condType = (TypeDenoter)stmt.cond.visit(this, arg);
        checkTypeMatch("if statement condition", stmt.posn, condType, BOOLEAN_TYPE);
        checkIsolatedVarDeclStmt(stmt.thenStmt);
        stmt.thenStmt.visit(this, arg);
        if (stmt.elseStmt != null) {
            checkIsolatedVarDeclStmt(stmt.elseStmt);
            stmt.elseStmt.visit(this, arg);
        }
        return null;
    }

    @Override
    public Object visitWhileStmt(WhileStmt stmt, IdTable arg) {
        TypeDenoter condType = (TypeDenoter)stmt.cond.visit(this, arg);
        checkTypeMatch("while statement condition", stmt.posn, condType, BOOLEAN_TYPE);
        checkIsolatedVarDeclStmt(stmt.body);
        stmt.body.visit(this, arg);
        return null;
    }

    @Override
    public Object visitForStmt(ForStmt stmt, IdTable arg) {
        arg.openScope();
        if (stmt.init != null) stmt.init.visit(this, arg);
        if (stmt.cond != null) {
            TypeDenoter condType = (TypeDenoter)stmt.cond.visit(this, arg);
            checkTypeMatch("for statement condition", stmt.posn, condType, BOOLEAN_TYPE);
        }
        if (stmt.incr != null) stmt.incr.visit(this, arg);
        checkIsolatedVarDeclStmt(stmt.body);
        stmt.body.visit(this, arg);
        arg.closeScope();
        return null;
    }

    @Override
    public Object visitUnaryExpr(UnaryExpr expr, IdTable arg) {
        String ctmContext = String.format("%s unary expression", expr.operator.kind.toString().toLowerCase());
        TypeDenoter operandType = (TypeDenoter)expr.expr.visit(this, arg);
        switch (expr.operator.kind) {
            case Minus:
                checkTypeMatch(ctmContext, expr.posn, operandType, CHAR_TYPE, INT_TYPE, LONG_TYPE, FLOAT_TYPE, DOUBLE_TYPE, BOOLEAN_TYPE);
                return expr.resultType = operandType;
            case LogNot:
                checkTypeMatch(ctmContext, expr.posn, operandType, BOOLEAN_TYPE);
                return expr.resultType = BOOLEAN_TYPE;
            default:
                checkTypeMatch(ctmContext, expr.posn, operandType, UNSUPPORTED_TYPE);
                return expr.resultType = UNSUPPORTED_TYPE;
        }
    }

    @Override
    public Object visitBinaryExpr(BinaryExpr expr, IdTable arg) {
        TypeDenoter leftType = (TypeDenoter)expr.left.visit(this, arg);
        TypeDenoter rightType = (TypeDenoter)expr.right.visit(this, arg);
        if (!TypeChecker.typeMatches(leftType, rightType)) {
            if (TypeChecker.validCast(arg, leftType, rightType, false)) {
                expr.left = new CastExpr(rightType, expr.left, expr.posn);
                expr.left.resultType = rightType;
                leftType = rightType;
            } else if (TypeChecker.validCast(arg, rightType, leftType, false)) {
                expr.right = new CastExpr(leftType, expr.right, expr.posn);
                expr.right.resultType = leftType;
                rightType = leftType;
            }
        }
        String ctmContext = String.format(" side of %s binary expression", expr.operator.kind.toString().toLowerCase());
        String ctmLeftContext = "left" + ctmContext;
        String ctmRightContext = "right" + ctmContext;
        switch (expr.operator.kind) {
            case LogAnd: case LogOr:
                checkTypeMatch(ctmLeftContext, expr.posn, leftType, BOOLEAN_TYPE);
                checkTypeMatch(ctmRightContext, expr.posn, rightType, BOOLEAN_TYPE);
                return expr.resultType = BOOLEAN_TYPE;
            case RelLT: case RelGT: case RelLEq: case RelGEq:
                checkTypeMatch(ctmLeftContext, expr.posn, leftType, INT_TYPE, FLOAT_TYPE, LONG_TYPE, DOUBLE_TYPE, CHAR_TYPE);
                checkTypeMatch(ctmRightContext, expr.posn, rightType, leftType);
                return expr.resultType = BOOLEAN_TYPE;
            case Add: case Minus: case Multiply: case Divide:
                checkTypeMatch(ctmLeftContext, expr.posn, leftType, INT_TYPE, FLOAT_TYPE, LONG_TYPE, DOUBLE_TYPE, CHAR_TYPE);
                checkTypeMatch(ctmRightContext, expr.posn, rightType, leftType);
                return expr.resultType = leftType;
            case RelEq: case RelNEq:
                checkTypeMatch(ctmRightContext, expr.posn, rightType, leftType);
                return expr.resultType = BOOLEAN_TYPE;
            default:
                checkTypeMatch(ctmLeftContext, expr.posn, leftType, UNSUPPORTED_TYPE);
                checkTypeMatch(ctmRightContext, expr.posn, rightType, UNSUPPORTED_TYPE);
                return expr.resultType = UNSUPPORTED_TYPE;
        }
    }

    @Override
    public Object visitRefExpr(RefExpr expr, IdTable arg) {
        Declaration decl = (Declaration)expr.ref.visit(this, arg);
        checkIsTyped(expr.ref.posn, decl);
        return expr.resultType = decl.type;
    }

    @Override
    public Object visitIxExpr(IxExpr expr, IdTable arg) {
        TypeDenoter ixType = (TypeDenoter) expr.ixExpr.visit(this, arg);
        Declaration refDecl = (Declaration) expr.ref.visit(this, arg);
        checkIsTyped(expr.ref.posn, refDecl);
        checkTypeMatch("array index", expr.posn, ixType, INT_TYPE);
        if (refDecl.type == null || refDecl.type.typeKind != TypeKind.ARRAY) {
            throw new MatcherError(expr.posn, String.format("%s is not an array", TypeChecker.typeStr(refDecl.type)));
        }
        return expr.resultType = ((ArrayType) refDecl.type).eltType;
    }

    private TypeDenoter visitCallArgs(Reference methodRef, ExprList argList, SigGroup sigGroup, IdTable arg, SourcePosition posn) {
        // determine call signature based on arg types and sig group name
        Signature callSig = new Signature(null);
        callSig.name = sigGroup.name;
        for (int i = 0; i < argList.size(); i++) {
            Expression callArg = argList.get(i);
            TypeDenoter argType = (TypeDenoter)callArg.visit(this, arg);
            callSig.argTypes.add(argType);
        }

        // derive matching sig from sig group based on call signature
        Signature methodSig = null;

        // if exact match, pick that
        // otherwise, determine if any signatures can be implicitly cast to
        // if exactly one signature can be implicitly cast to, pick it
        // otherwise either no match or ambiguous
        List<Signature> castSigs = new ArrayList<>();
        for (Signature sig : sigGroup.sigs) {
            if (callSig.equals(sig)) {
                methodSig = sig;
                break;
            } else if (TypeChecker.validCast(arg, callSig, sig))
                castSigs.add(sig);
        }
        if (methodSig == null) {
            if (castSigs.size() > 1) {
                StringBuilder errorMsg = new StringBuilder(String.format("Method call %s.%s with signature %s is ambiguous as it matches %d implicit cast signatures: ", sigGroup.parent.name, sigGroup.name, callSig.toString(), castSigs.size()));
                boolean first = true;
                for (Signature sig : castSigs) {
                    if (first) first = false;
                    else errorMsg.append(", ");
                    errorMsg.append(sig.toString());
                }
                throw new MatcherError(posn, errorMsg.toString());
            } else if (castSigs.isEmpty()) {
                StringBuilder errorMsg = new StringBuilder(String.format("Method call %s.%s with signature %s does not match any of the following signatures: ", sigGroup.parent.name, sigGroup.name, callSig.toString()));
                boolean first = true;
                for (Signature sig : sigGroup.sigs) {
                    if (first) first = false;
                    else errorMsg.append(", ");
                    errorMsg.append(sig.toString());
                }
                throw new MatcherError(posn, errorMsg.toString());
            }
            methodSig = castSigs.get(0);
        }

        // do static check
        if (sigGroup.lastRefStatic && !methodSig.decl.isStatic) {
            throw new MatcherError(posn, String.format("Cannot access private method %s.%s from static context", methodSig.decl.parent.name, methodSig));
        }
        // do private check
        boolean allowPrivate = activeClass == sigGroup.parent;
        if (!allowPrivate && methodSig.decl.isPrivate) {
            throw new MatcherError(posn, String.format("Cannot access private method %s.%s from external context", methodSig.decl.parent.name, methodSig));
        }

        // add implicit type casts
        for (int i = 0; i < argList.size(); i++) {
            ParameterDecl pd = methodSig.decl.parameterDeclList.get(i);
            TypeDenoter argType = callSig.argTypes.get(i);
            Expression argExpr = argList.get(i);
            if (!TypeChecker.typeMatches(argType, pd.type)) {
                argList.set(i, new CastExpr(pd.type, argExpr, argExpr.posn));
                argExpr = argList.get(i);
                argExpr.resultType = pd.type;
            }
        }

        // reassign decl of reference to match method decl
        methodRef.decl = methodSig.decl;

        // return ret type of method
        return methodSig.decl.type;
    }

    @Override
    public Object visitCallExpr(CallExpr expr, IdTable arg) {
        Declaration decl = (Declaration) expr.functionRef.visit(this, arg);
        checkIsCallable(expr.functionRef.posn, decl);
        SigGroup sigGroup = (SigGroup)decl;
        return expr.resultType = visitCallArgs(expr.functionRef, expr.argList, sigGroup, arg, expr.posn);
    }

    @Override
    public Object visitLiteralExpr(LiteralExpr expr, IdTable arg) {
        return expr.resultType = (TypeDenoter)expr.lit.visit(this, arg);
    }

    @Override
    public Object visitNewObjectExpr(NewObjectExpr expr, IdTable arg) {
        ClassDecl decl = arg.getClassDecl(expr.posn, expr.classtype.className.spelling);
        expr.decl = decl;
        return expr.resultType = decl.unsupported ? UNSUPPORTED_TYPE : expr.classtype;
    }

    @Override
    public Object visitNewArrayExpr(NewArrayExpr expr, IdTable arg) {
        TypeDenoter sizeType = (TypeDenoter)expr.sizeExpr.visit(this, arg);
        checkTypeMatch("new array size expression", expr.posn, sizeType, INT_TYPE);
        return expr.resultType = new ArrayType(expr.eltType, expr.posn);
    }

    @Override
    public Object visitCastExpr(CastExpr expr, IdTable arg) {
        TypeDenoter srcType = (TypeDenoter)expr.expr.visit(this, arg);
        if (!TypeChecker.validCast(arg, expr.type, srcType, true))
            errors.reportError(expr.posn, String.format("Cannot cast type %s to %s", TypeChecker.typeStr(srcType), TypeChecker.typeStr(expr.type)));
        return expr.resultType = expr.type;
    }

    @Override
    public Object visitInstanceOfExpr(InstanceOfExpr expr, IdTable arg) {
        TypeDenoter valType = (TypeDenoter)expr.expr.visit(this, arg);
        expr.resultType = BOOLEAN_TYPE;

        // ensure either expr type is descendant of type or vise versa
        if (!(valType instanceof ClassType)) {
            errors.reportError(expr.posn, String.format("Cannot use instanceof on non class type %s", TypeChecker.typeStr(valType)));
            return BOOLEAN_TYPE;
        }
        ClassType valClassType = (ClassType)valType;
        ClassDecl valClassDecl = arg.getClassDecl(valClassType.posn, valClassType.className.spelling);
        ClassDecl typeClassDecl = arg.getClassDecl(valClassType.posn, expr.type.className.spelling);
        if (!TypeChecker.ancestorOf(valClassDecl, typeClassDecl) && !TypeChecker.ancestorOf(typeClassDecl, valClassDecl)) {
            errors.reportError(expr.posn, String.format("Cannot use instanceof on unrelated classes %s and %s", valClassDecl.name, typeClassDecl.name));
        }

        return BOOLEAN_TYPE;
    }

    @Override
    public Object visitThisRef(ThisRef ref, IdTable arg) {
        if (staticActive)
            throw new MatcherError(ref.posn, "Cannot reference this in a static context");
        return ref.decl = new VarDecl(new ClassType(new Identifier(new Token(TokenType.Identifier, activeClass.name, PREDEF_POSN.line, PREDEF_POSN.offset)), PREDEF_POSN), "this", PREDEF_POSN);
    }

    @Override
    public Object visitSuperRef(SuperRef ref, IdTable arg) {
        if (staticActive)
            throw new MatcherError(ref.posn, "Cannot reference super in a static context");
        return ref.decl = new VarDecl(new ClassType(new Identifier(new Token(TokenType.Identifier, activeClass.parentDecl.name, PREDEF_POSN.line, PREDEF_POSN.offset)), PREDEF_POSN), "super", PREDEF_POSN);
    }

    @Override
    public Object visitIdRef(IdRef ref, IdTable arg) {
        Declaration decl = arg.getScopedDecl(ref.posn, ref.id.spelling);
        if (decl instanceof SigGroup) {
            // handle sig group static when method resolved
            SigGroup sigGroup = (SigGroup)decl;
            sigGroup.lastRefStatic = staticActive;
        } else if (staticActive) {
            // handle static check
            if (decl instanceof MemberDecl && !((MemberDecl)decl).isStatic)
                throw new MatcherError(ref.posn, String.format("member %s.%s is not accessible from static context", activeClass.name, ref.id.spelling));
        }
        return ref.decl = (Declaration)ref.id.visit(this, arg);
    }

    @Override
    public Object visitQRef(QualRef ref, IdTable arg) {
        // get ref info
        Declaration refDecl = (Declaration)ref.ref.visit(this, arg);
        if (refDecl instanceof MethodDecl)
            throw new MatcherError(ref.ref.posn, String.format("Cannot access members of method %s", ref.id.spelling));
        String className = getClassName(refDecl);
        if (className == null)
            throw new MatcherError(ref.ref.posn, String.format("Cannot access members of base type %s", TypeChecker.typeStr(refDecl.type)));
        else if (className.equals("<ARRAY>")) {
            // handle array.length
            if (!ref.id.spelling.equals("length"))
                throw new MatcherError(ref.id.posn, String.format("Array object has no member %s", ref.id.spelling));
            ref.id.decl = ARR_LENGTH_DECL;
            ref.decl = ARR_LENGTH_DECL;
            return ARR_LENGTH_DECL;
        }
        boolean isClass = refDecl instanceof ClassDecl;
        ClassDecl classDecl = arg.getClassDecl(ref.posn, className);
        boolean isActiveClass = className.equals(activeClass.name);

        // find id in ref (don't call visit on id to avoid scope check)
        MemberDecl decl = arg.getClassMember(ref.id.posn, classDecl.name, ref.id.spelling);
        ref.id.decl = decl;
        if (decl instanceof SigGroup) {
            // if sig group handle private and static checks after method decl resolved
            SigGroup sigGroup = (SigGroup)decl;
            sigGroup.lastRefStatic = isClass;
        } else {
            // handle private and static checks
            if (isClass && !decl.isStatic)
                throw new MatcherError(ref.id.posn, String.format("Cannot access non-static member %s from class %s", decl.name, className));
            if (decl.isPrivate && !isActiveClass)
                throw new MatcherError(ref.id.posn, String.format("Cannot access private member %s.%s from external class", className, decl.name));
        }
        return ref.decl = decl;
    }

    @Override
    public Object visitIdentifier(Identifier id, IdTable arg) {
        return id.decl = arg.getScopedDecl(id.posn, id.spelling);
    }

    @Override
    public Object visitOperator(Operator op, IdTable arg) {
        throw new RuntimeException("Should not visit");
    }

    @Override
    public Object visitIntLiteral(IntLiteral num, IdTable arg) {
        return INT_TYPE;
    }

    @Override
    public Object visitBooleanLiteral(BooleanLiteral bool, IdTable arg) {
        return BOOLEAN_TYPE;
    }

    @Override
    public Object visitNullLiteral(NullLiteral nullLiteral, IdTable asm) {
        return NULL_TYPE;
    }

    @Override
    public Object visitLongLiteral(LongLiteral longLiteral, IdTable arg) {
        return LONG_TYPE;
    }

    @Override
    public Object visitFloatLiteral(FloatLiteral floatLiteral, IdTable arg) {
        return FLOAT_TYPE;
    }

    @Override
    public Object visitDoubleLiteral(DoubleLiteral doubleLiteral, IdTable arg) {
        return DOUBLE_TYPE;
    }

    @Override
    public Object visitCharLiteral(CharLiteral charLiteral, IdTable arg) {
        return CHAR_TYPE;
    }
}
