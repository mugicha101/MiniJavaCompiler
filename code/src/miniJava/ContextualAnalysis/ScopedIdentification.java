package miniJava.ContextualAnalysis;

import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.ErrorReporter;
import miniJava.SyntacticAnalyzer.SourcePosition;
import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenType;

// TODO: static scope checking

// references return Declaration
// identifiers return Declaration
// expressions return TypeDenoter
// types return TypeDenoter
// return statements return TypeDenoter
public class ScopedIdentification implements Visitor<IdTable, Object> {
    static final TypeDenoter INT_TYPE = new BaseType(TypeKind.INT, null);
    static final TypeDenoter BOOLEAN_TYPE = new BaseType(TypeKind.BOOLEAN, null);
    static final TypeDenoter UNSUPPORTED_TYPE = new BaseType(TypeKind.UNSUPPORTED, null);
    static final TypeDenoter ERROR_TYPE = new BaseType(TypeKind.ERROR, null);
    static final TypeDenoter NULL_TYPE = new ClassType(new Identifier(new Token(TokenType.NullLiteral, "null", -1, -1)), null);
    static final SourcePosition PREDEF_POSN = new SourcePosition(-1, -1);
    public ClassDecl activeClass;
    public MethodDecl activeMethod;
    public final ErrorReporter errors;
    int staticActive;
    public ScopedIdentification(ErrorReporter errors) {
        this.errors = errors;
    }
    String typeStr(TypeDenoter type) {
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
            default:
                return "<error-type>";
        }
    }

    boolean checkTypeMatch(TypeDenoter actual, TypeDenoter... expected) {
        String tsAct = typeStr(actual);
        for (TypeDenoter td : expected) {
            if (
                actual.typeKind == TypeKind.CLASS && td.typeKind == TypeKind.CLASS
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
    void checkTypeMatch(String context, SourcePosition posn, TypeDenoter actual, TypeDenoter... expected) {
        assert(expected.length > 0);
        if (checkTypeMatch(actual, expected)) return;
        StringBuilder expStr = new StringBuilder();
        expStr.append(typeStr(expected[0]));
        for (int i = 1; i < expected.length; ++i) {
            expStr.append(" or ");
            expStr.append(typeStr(expected[i]));
        }
        errors.reportError(posn, String.format("Type mismatch in %s: expected %s, but got %s", context, expStr, typeStr(actual)));
    }

    // takes in decl, returns name of class
    // if decl is class returns its name
    // if decl is object returns its class name
    // otherwise return null
    String getClassName(Declaration decl) {
        if (decl.type == null) return decl.name;
        if (decl.type.typeKind == TypeKind.CLASS)
            return ((ClassType)decl.type).className.spelling;
        return null;
    }

    void addPredefined(IdTable idTable) {
        ClassDecl SystemDecl = new ClassDecl("System", new FieldDeclList(), new MethodDeclList(), PREDEF_POSN);
        SystemDecl.fieldDeclList.add(new FieldDecl(false, true, new ClassType(new Identifier(new Token(TokenType.Identifier, "_PrintStream", PREDEF_POSN.line, PREDEF_POSN.offset)), PREDEF_POSN), "out", PREDEF_POSN));
        ClassDecl PrintStreamDecl = new ClassDecl("_PrintStream", new FieldDeclList(), new MethodDeclList(), PREDEF_POSN);
        PrintStreamDecl.methodDeclList.add(new MethodDecl(new FieldDecl(false, false, new BaseType(TypeKind.VOID, PREDEF_POSN), "println", PREDEF_POSN), new ParameterDeclList(), new StatementList(), PREDEF_POSN));
        PrintStreamDecl.methodDeclList.get(0).parameterDeclList.add(new ParameterDecl(new BaseType(TypeKind.INT, PREDEF_POSN), "n", PREDEF_POSN));
        ClassDecl StringDecl = new ClassDecl("String", new FieldDeclList(), new MethodDeclList(), PREDEF_POSN);
        idTable.addClassDecl(SystemDecl);
        idTable.addScopedDecl(SystemDecl);
        idTable.addClassDecl(PrintStreamDecl);
        idTable.addScopedDecl(PrintStreamDecl);
        idTable.addClassDecl(StringDecl);
        idTable.addScopedDecl(StringDecl);
    }

    @Override
    public Object visitPackage(Package prog, IdTable arg) {
        activeClass = null;
        activeMethod = null;
        staticActive = 0;

        // add predefined classes
        addPredefined(arg);

        // resolve class decls and class member decls
        for (ClassDecl classDecl : prog.classDeclList) {
            arg.addClassDecl(classDecl);
            arg.addScopedDecl(classDecl);
        }

        // identify
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
        for (MethodDecl methodDecl : cd.methodDeclList)
            methodDecl.visit(this, arg);
        arg.closeScope();
        activeClass = null;
        return null;
    }

    @Override
    public Object visitFieldDecl(FieldDecl fd, IdTable arg) {
        throw new RuntimeException("Should not visit");
    }

    @Override
    public Object visitMethodDecl(MethodDecl md, IdTable arg) {
        activeMethod = md;
        arg.openScope();
        for (ParameterDecl pd : md.parameterDeclList)
            pd.visit(this, arg);
        TypeDenoter lastRetType = null;
        for (Statement stmt : md.statementList) {
            lastRetType = (TypeDenoter)stmt.visit(this, arg);
        }
        if ((md.type == null || md.type.typeKind != TypeKind.VOID) && lastRetType == null)
            errors.reportError(md.posn, String.format("Method %s.%s has no last return statement", activeClass.name, md.name));
        arg.closeScope();
        activeMethod = null;
        return null;
    }

    @Override
    public Object visitParameterDecl(ParameterDecl pd, IdTable arg) {
        arg.addScopedDecl(pd);
        return pd.type;
    }

    @Override
    public Object visitVarDecl(VarDecl decl, IdTable arg) {
        arg.addScopedDecl(decl);
        return decl.type;
    }

    @Override
    public Object visitBaseType(BaseType type, IdTable arg) {
        return type;
    }

    @Override
    public Object visitClassType(ClassType type, IdTable arg) {
        return type;
    }

    @Override
    public Object visitArrayType(ArrayType type, IdTable arg) {
        throw new RuntimeException("Should not visit");
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
        TypeDenoter exprType = (TypeDenoter)stmt.initExp.visit(this, arg);
        TypeDenoter declType = (TypeDenoter)stmt.varDecl.visit(this, arg);
        checkTypeMatch("variable declaration", stmt.posn, exprType, declType);
        return null;
    }

    @Override
    public Object visitAssignStmt(AssignStmt stmt, IdTable arg) {
        TypeDenoter exprType = (TypeDenoter)stmt.val.visit(this, arg);
        Declaration refDecl = (Declaration)stmt.ref.visit(this, arg);
        checkTypeMatch("assign statement", stmt.posn, exprType, refDecl.type);
        return null;
    }

    @Override
    public Object visitIxAssignStmt(IxAssignStmt stmt, IdTable arg) {
        TypeDenoter exprType = (TypeDenoter)stmt.exp.visit(this, arg);
        TypeDenoter ixType = (TypeDenoter)stmt.ix.visit(this, arg);
        Declaration refDecl = (Declaration) stmt.ref.visit(this, arg);
        checkTypeMatch("array index", stmt.posn, ixType, INT_TYPE);
        if (refDecl.type == null || refDecl.type.typeKind != TypeKind.ARRAY)
            errors.reportError(stmt.posn, String.format("Type mismatch in array element assignment statement: %s is not an array", typeStr(refDecl.type)));
        else {
            TypeDenoter eltType = ((ArrayType)refDecl.type).eltType;
            checkTypeMatch("array element assignment", stmt.posn, exprType, eltType);
        }
        return null;
    }

    @Override
    public Object visitCallStmt(CallStmt stmt, IdTable arg) {
        MethodDecl methodDecl = (MethodDecl)stmt.methodRef.visit(this, arg);
        boolean sizeMatch = methodDecl.parameterDeclList.size() == stmt.argList.size();
        if (!sizeMatch)
            errors.reportError(stmt.posn, String.format("Called method %s with %d arguments but expected %d arguments", methodDecl.name, stmt.argList.size(), methodDecl.parameterDeclList.size()));
        for (int i = 0; i < stmt.argList.size(); i++) {
            Expression callArg = stmt.argList.get(i);
            TypeDenoter argType = (TypeDenoter)callArg.visit(this, arg);
            if (!sizeMatch) continue;
            ParameterDecl pd = methodDecl.parameterDeclList.get(i);
            if (!checkTypeMatch(argType, pd.type)) {
                errors.reportError(stmt.posn, String.format("Type mismatch in method %s.%s: parameter %s expected %s, but got %s", activeClass.name, methodDecl.name, pd.name, typeStr(pd.type), typeStr(argType)));
            }
        }
        return null;
    }

    @Override
    public Object visitReturnStmt(ReturnStmt stmt, IdTable arg) {
        TypeDenoter retType = (TypeDenoter)stmt.returnExpr.visit(this, arg);
        TypeDenoter mRetType = activeMethod.type;
        checkTypeMatch(String.format("method %s.%s return statement", activeClass.name, activeMethod.name), stmt.posn, retType, mRetType);
        return retType;
    }

    @Override
    public Object visitIfStmt(IfStmt stmt, IdTable arg) {
        TypeDenoter condType = (TypeDenoter)stmt.cond.visit(this, arg);
        checkTypeMatch("if statement condition", stmt.posn, condType, BOOLEAN_TYPE);
        arg.openScope();
        stmt.thenStmt.visit(this, arg);
        arg.closeScope();
        if (stmt.elseStmt != null) {
            arg.openScope();
            stmt.elseStmt.visit(this, arg);
            arg.closeScope();
        }
        return null;
    }

    @Override
    public Object visitWhileStmt(WhileStmt stmt, IdTable arg) {
        TypeDenoter condType = (TypeDenoter)stmt.cond.visit(this, arg);
        checkTypeMatch("while statement condition", stmt.posn, condType, BOOLEAN_TYPE);
        arg.openScope();
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
                checkTypeMatch(ctmContext, expr.posn, operandType, INT_TYPE);
                return INT_TYPE;
            case LogNot:
                checkTypeMatch(ctmContext, expr.posn, operandType, BOOLEAN_TYPE);
                return BOOLEAN_TYPE;
            default:
                checkTypeMatch(ctmContext, expr.posn, operandType, UNSUPPORTED_TYPE);
                return UNSUPPORTED_TYPE;
        }
    }

    @Override
    public Object visitBinaryExpr(BinaryExpr expr, IdTable arg) {
        TypeDenoter leftType = (TypeDenoter)expr.left.visit(this, arg);
        TypeDenoter rightType = (TypeDenoter)expr.right.visit(this, arg);
        String ctmContext = String.format(" side of %s binary expression", expr.operator.kind.toString().toLowerCase());
        String ctmLeftContext = "left" + ctmContext;
        String ctmRightContext = "right" + ctmContext;
        switch (expr.operator.kind) {
            case LogAnd: case LogOr:
                checkTypeMatch(ctmLeftContext, expr.posn, leftType, BOOLEAN_TYPE);
                checkTypeMatch(ctmRightContext, expr.posn, rightType, BOOLEAN_TYPE);
                return BOOLEAN_TYPE;
            case RelLT: case RelGT: case RelLEq: case RelGEq:
                checkTypeMatch(ctmLeftContext, expr.posn, leftType, INT_TYPE);
                checkTypeMatch(ctmRightContext, expr.posn, rightType, INT_TYPE);
                return BOOLEAN_TYPE;
            case Add: case Minus: case Multiply: case Divide:
                checkTypeMatch(ctmLeftContext, expr.posn, leftType, INT_TYPE);
                checkTypeMatch(ctmRightContext, expr.posn, rightType, INT_TYPE);
                return INT_TYPE;
            case RelEq: case RelNEq:
                checkTypeMatch(ctmRightContext, expr.posn, rightType, leftType);
                return BOOLEAN_TYPE;
            default:
                checkTypeMatch(ctmLeftContext, expr.posn, leftType, UNSUPPORTED_TYPE);
                checkTypeMatch(ctmRightContext, expr.posn, rightType, UNSUPPORTED_TYPE);
                return UNSUPPORTED_TYPE;
        }
    }

    @Override
    public Object visitRefExpr(RefExpr expr, IdTable arg) {
        return ((Declaration)expr.ref.visit(this, arg)).type;
    }

    @Override
    public Object visitIxExpr(IxExpr expr, IdTable arg) {
        TypeDenoter ixType = (TypeDenoter)expr.ixExpr.visit(this, arg);
        Declaration refDecl = (Declaration) expr.ref.visit(this, arg);
        checkTypeMatch("array index", expr.posn, ixType, INT_TYPE);
        if (refDecl.type == null || refDecl.type.typeKind != TypeKind.ARRAY) {
            errors.reportError(expr.posn, String.format("Type mismatch in array index reference expression: %s is not an array", typeStr(refDecl.type)));
            return ERROR_TYPE;
        }
        return ((ArrayType)refDecl.type).eltType;
    }

    @Override
    public Object visitCallExpr(CallExpr expr, IdTable arg) {
        MethodDecl methodDecl = (MethodDecl)expr.functionRef.visit(this, arg);
        boolean sizeMatch = methodDecl.parameterDeclList.size() == expr.argList.size();
        if (!sizeMatch)
            errors.reportError(expr.posn, String.format("Called method %s with %d arguments but expected %d arguments", methodDecl.name, expr.argList.size(), methodDecl.parameterDeclList.size()));
        for (int i = 0; i < expr.argList.size(); i++) {
            Expression callArg = expr.argList.get(i);
            TypeDenoter argType = (TypeDenoter)callArg.visit(this, arg);
            if (!sizeMatch) continue;
            ParameterDecl pd = methodDecl.parameterDeclList.get(i);
            if (!checkTypeMatch(argType, pd.type)) {
                errors.reportError(expr.posn, String.format("Type mismatch in method %s: parameter %s expected %s, but got %s", methodDecl.name, pd.name, pd.type, argType));
            }
        }
        return methodDecl.type;
    }

    @Override
    public Object visitLiteralExpr(LiteralExpr expr, IdTable arg) {
        return expr.lit.visit(this, arg);
    }

    @Override
    public Object visitNewObjectExpr(NewObjectExpr expr, IdTable arg) {
        return expr.classtype;
    }

    @Override
    public Object visitNewArrayExpr(NewArrayExpr expr, IdTable arg) {
        TypeDenoter sizeType = (TypeDenoter)expr.sizeExpr.visit(this, arg);
        checkTypeMatch("new array size expression", expr.posn, sizeType, INT_TYPE);
        return new ArrayType(expr.eltType, expr.posn);
    }

    @Override
    public Object visitThisRef(ThisRef ref, IdTable arg) {
        return new VarDecl(new ClassType(new Identifier(new Token(TokenType.Identifier, activeClass.name, PREDEF_POSN.line, PREDEF_POSN.offset)), PREDEF_POSN), "this", PREDEF_POSN);
    }

    @Override
    public Object visitIdRef(IdRef ref, IdTable arg) {
        return ref.id.visit(this, arg);
    }

    @Override
    public Object visitQRef(QualRef ref, IdTable arg) {
        // get ref info
        Declaration refDecl = (Declaration)ref.ref.visit(this, arg);
        String className = getClassName(refDecl);
        if (className == null)
            throw new IdentificationError(ref.posn, String.format("Expected class or object type, but got %s", typeStr(refDecl.type)));
        boolean isClass = refDecl instanceof ClassDecl;
        ClassDecl classDecl = arg.getClassDecl(ref.posn, className);

        // find id in ref
        MemberDecl decl = arg.getClassMember(ref.posn, classDecl.name, ref.id.spelling);
        if (isClass && !decl.isStatic)
            throw new IdentificationError(ref.posn, String.format("%s is not a static member of %s", decl.name, className));
        return decl;
    }

    @Override
    public Object visitIdentifier(Identifier id, IdTable arg) {
        return arg.getScopedDecl(id.posn, id.spelling);
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
    public Object visitNullLiteral(NullLiteral nullLiteral, IdTable o) {
        return NULL_TYPE;
    }
}
