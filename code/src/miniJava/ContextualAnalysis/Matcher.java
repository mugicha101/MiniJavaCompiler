package miniJava.ContextualAnalysis;

import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.ErrorReporter;
import miniJava.SyntacticAnalyzer.SourcePosition;
import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenType;

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

    void addPredefined(Package prog) {
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
        }
    }

    @Override
    public Object visitPackage(Package prog, IdTable arg) {
        addPredefined(prog);
        for (ClassDecl classDecl : prog.classDeclList) {
            arg.addClassDecl(classDecl);
            arg.addScopedDecl(classDecl);
        }
        for (ClassDecl classDecl : prog.classDeclList)
            classDecl.visit(this, arg);
        return null;
    }

    @Override
    public Object visitClassDecl(ClassDecl cd, IdTable arg) {
        activeClass = cd;
        arg.openScope();

        // add members to scope
        for (FieldDecl fieldDecl : cd.fieldDeclList)
            arg.addScopedDecl(fieldDecl);
        for (MethodDecl methodDecl : cd.methodDeclList)
            arg.addScopedDecl(methodDecl);

        // identify
        for (FieldDecl fieldDecl : cd.fieldDeclList) {
            fieldDecl.parent = cd;
            fieldDecl.visit(this, arg);
        }
        for (MethodDecl methodDecl : cd.methodDeclList) {
            methodDecl.parent = cd;
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
        if (!TypeChecker.typeMatches(exprType, declType) && TypeChecker.validCast(exprType, declType, false)) {
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
        if (!(decl instanceof MethodDecl))
            throw new MatcherError(posn, String.format("%s is not callable", decl.name));
    }

    @Override
    public Object visitAssignStmt(AssignStmt stmt, IdTable arg) {
        TypeDenoter exprType = (TypeDenoter)stmt.val.visit(this, arg);
        Declaration refDecl = (Declaration)stmt.ref.visit(this, arg);
        checkIsTyped(refDecl.posn, refDecl);
        if (!TypeChecker.typeMatches(exprType, refDecl.type) && TypeChecker.validCast(exprType, refDecl.type, false)) {
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
        if (!TypeChecker.typeMatches(exprType, eltType) && TypeChecker.validCast(exprType, eltType, false)) {
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
        MethodDecl methodDecl = (MethodDecl)decl;
        visitCallArgs(stmt.argList, methodDecl, arg, stmt.posn);
        return null;
    }

    @Override
    public Object visitReturnStmt(ReturnStmt stmt, IdTable arg) {
        TypeDenoter retType = stmt.returnExpr == null ? VOID_TYPE : (TypeDenoter)stmt.returnExpr.visit(this, arg);
        TypeDenoter mRetType = activeMethod.type;
        if (!TypeChecker.typeMatches(retType, mRetType) && TypeChecker.validCast(retType, mRetType, false)) {
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
        stmt.init.visit(this, arg);
        TypeDenoter condType = (TypeDenoter)stmt.cond.visit(this, arg);
        checkTypeMatch("for statement condition", stmt.posn, condType, BOOLEAN_TYPE);
        stmt.incr.visit(this, arg);
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
            if (TypeChecker.validCast(leftType, rightType, false)) {
                expr.left = new CastExpr(rightType, expr.left, expr.posn);
                expr.left.resultType = rightType;
                leftType = rightType;
            } else if (TypeChecker.validCast(rightType, leftType, false)) {
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

    private void visitCallArgs(ExprList argList, MethodDecl methodDecl, IdTable arg, SourcePosition posn) {
        boolean sizeMatch = methodDecl.parameterDeclList.size() == argList.size();
        if (!sizeMatch)
            errors.reportError(posn, String.format("Called method %s with %d arguments but expected %d arguments", methodDecl.name, argList.size(), methodDecl.parameterDeclList.size()));
        for (int i = 0; i < argList.size(); i++) {
            Expression callArg = argList.get(i);
            TypeDenoter argType = (TypeDenoter)callArg.visit(this, arg);
            if (!sizeMatch) continue;
            ParameterDecl pd = methodDecl.parameterDeclList.get(i);
            if (!TypeChecker.typeMatches(argType, pd.type)) {
                if (TypeChecker.validCast(argType, pd.type, false)) {
                    argList.set(i, new CastExpr(pd.type, callArg, callArg.posn));
                    callArg = argList.get(i);
                    callArg.resultType = pd.type;
                } else {
                    errors.reportError(posn, String.format("Type mismatch in method %s: parameter %s expected %s, but got %s", methodDecl.name, pd.name, TypeChecker.typeStr(pd.type), TypeChecker.typeStr(argType)));
                }
            }
        }
    }

    @Override
    public Object visitCallExpr(CallExpr expr, IdTable arg) {
        Declaration decl = (Declaration) expr.functionRef.visit(this, arg);
        checkIsCallable(expr.functionRef.posn, decl);
        MethodDecl methodDecl = (MethodDecl)decl;
        visitCallArgs(expr.argList, methodDecl, arg, expr.posn);
        return expr.resultType = methodDecl.type;
    }

    @Override
    public Object visitLiteralExpr(LiteralExpr expr, IdTable arg) {
        return expr.resultType = (TypeDenoter)expr.lit.visit(this, arg);
    }

    @Override
    public Object visitNewObjectExpr(NewObjectExpr expr, IdTable arg) {
        ClassDecl decl = arg.getClassDecl(expr.posn, expr.classtype.className.spelling);
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
        if (!TypeChecker.validCast(expr.type, srcType, true))
            errors.reportError(expr.posn, String.format("Cannot cast type %s to %s", TypeChecker.typeStr(srcType), TypeChecker.typeStr(expr.type)));
        return expr.resultType = expr.type;
    }

    @Override
    public Object visitThisRef(ThisRef ref, IdTable arg) {
        if (staticActive)
            throw new MatcherError(ref.posn, "Cannot reference this in a static context");
        return ref.decl = new VarDecl(new ClassType(new Identifier(new Token(TokenType.Identifier, activeClass.name, PREDEF_POSN.line, PREDEF_POSN.offset)), PREDEF_POSN), "this", PREDEF_POSN);
    }

    @Override
    public Object visitIdRef(IdRef ref, IdTable arg) {
        Declaration decl = arg.getScopedDecl(ref.posn, ref.id.spelling);
        if (staticActive) {
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
            throw new MatcherError(ref.ref.posn, String.format("Cannot access members of method %s", refDecl.name));
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
        if (isClass && !decl.isStatic)
            throw new MatcherError(ref.id.posn, String.format("Cannot non-static member %s from class %s", decl.name, className));
        if (decl.isPrivate && !isActiveClass)
            throw new MatcherError(ref.id.posn, String.format("%s.%s is private", className, decl.name));
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
