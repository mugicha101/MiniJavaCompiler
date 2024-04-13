package miniJava.CodeGeneration;

import com.sun.jdi.ArrayReference;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.CodeGeneration.x64.*;
import miniJava.CodeGeneration.x64.ISA.*;
import miniJava.ContextualAnalysis.TypeChecker;
import miniJava.ErrorReporter;
import miniJava.SyntacticAnalyzer.SourcePosition;
import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenType;

import java.util.*;

public class Codifier implements Visitor<Object, Object> {
    private final boolean CALL_REG_DUMP = false; // true if entry time registers pushed on call
    private static class UnresolvedAddress {
        public static Map<String,Integer> labelMap = new HashMap<>();
        enum Type {CALL, JMP, COND_JMP};
        private final InstructionList asm;
        private final Type type;
        private final Instruction instr;
        private final Object target;
        public UnresolvedAddress(InstructionList asm, int asmIdx, Object target) {
            this.asm = asm;
            this.instr = asm.get(asmIdx);
            if (instr instanceof Call)
                type = Type.CALL;
            else if (instr instanceof Jmp)
                type = Type.JMP;
            else if (instr instanceof CondJmp)
                type = Type.COND_JMP;
            else throw new IllegalArgumentException("unsupported instruction type for unresolved address");
            if (!(target instanceof MethodDecl || target instanceof String))
                throw new IllegalArgumentException("unsupported target type for unresolved address (must be MethodDecl or String)");
            this.target = target;
        }
        public void resolve() {
            Instruction newInstr;
            int targetOffset;
            if (target instanceof MethodDecl) {
                targetOffset = (int)((MethodDecl)target).asmOffset;
            } else {
                String label = (String)target;
                if (!labelMap.containsKey(label))
                    throw new CodeGenerationError(String.format("Error when resolving address: Cannot find address with label %s", label));
                targetOffset = labelMap.get(label);
            }
            switch (type) {
                case JMP:
                    newInstr = new Jmp((int)instr.startAddress, targetOffset, false);
                    break;
                case COND_JMP:
                    newInstr = new CondJmp(((CondJmp)instr).cond, (int)instr.startAddress, targetOffset, false);
                    break;
                case CALL:
                    newInstr = new Call((int)instr.startAddress, targetOffset);
                    break;
                default:
                    throw new IllegalArgumentException("unknown unresolved instruction type");
            }
            asm.patch(instr.listIdx, newInstr);
        }
    }
    private ErrorReporter errors;
    private InstructionList asm;
    private MethodDecl mainMethod;
    private List<UnresolvedAddress> unresolvedAddressList;
    private Stack<Integer> blockScopeStackSizes;
    private int rbpOffset;
    public Codifier(ErrorReporter errors) {
        this.errors = errors;
    }
    private int thisMemOffset;
    private ClassDecl currentClass;
    private MethodDecl currentMethod;
    private long nextNonce;
    private void addUnresolved(int idx, Object target) {
        unresolvedAddressList.add(new UnresolvedAddress(asm, idx, target));
    }
    public void parse(Package prog) {
        try {
            // If you haven't refactored the name "ModRMSIB" to something like "R",
            //  go ahead and do that now. You'll be needing that object a lot.
            // Here is some example code.

            // Simple operations:
            // _asm.add( new Push(0) ); // push the value zero onto the stack
            // _asm.add( new Pop(Reg64.RCX) ); // pop the top of the stack into RCX

            // Fancier operations:
            // _asm.add( new Cmp(new ModRMSIB(Reg64.RCX,Reg64.RDI)) ); // cmp rcx,rdi
            // _asm.add( new Cmp(new ModRMSIB(Reg64.RCX,0x10,Reg64.RDI)) ); // cmp [rcx+0x10],rdi
            // _asm.add( new Add(new ModRMSIB(Reg64.RSI,Reg64.RCX,4,0x1000,Reg64.RDX)) ); // add [rsi+rcx*4+0x1000],rdx

            // Thus:
            // new ModRMSIB( ... ) where the "..." can be:
            //  RegRM, RegR						== rm, r
            //  RegRM, int, RegR				== [rm+int], r
            //  RegRD, RegRI, intM, intD, RegR	== [rd+ ri*intM + intD], r
            // Where RegRM/RD/RI are just Reg64 or Reg32 or even Reg8
            //
            // Note there are constructors for ModRMSIB where RegR is skipped.
            // This is usually used by instructions that only need one register operand, and often have an immediate
            //   So they actually will set RegR for us when we create the instruction. An example is:
            // _asm.add( new Mov_rmi(new ModRMSIB(Reg64.RDX,true), 3) ); // mov rdx,3
            //   In that last example, we had to pass in a "true" to indicate whether the passed register
            //    is the operand RM or R, in this case, true means RM
            //  Similarly:
            // _asm.add( new Push(new ModRMSIB(Reg64.RBP,16)) );
            //   This one doesn't specify RegR because it is: push [rbp+16] and there is no second operand register needed

            // Patching example:
            // Instruction someJump = new Jmp((int)0); // 32-bit offset jump to nowhere
            // _asm.add( someJump ); // populate listIdx and startAddress for the instruction
            // ...
            // ... visit some code that probably uses _asm.add
            // ...
            // patch method 1: calculate the offset yourself
            //     _asm.patch( someJump.listIdx, new Jmp(asm.size() - someJump.startAddress - 5) );
            // -=-=-=-
            // patch method 2: let the jmp calculate the offset
            //  Note the false means that it is a 32-bit immediate for jumping (an int)
            //     _asm.patch( someJump.listIdx, new Jmp(asm.size(), someJump.startAddress, false) );
            unresolvedAddressList = new ArrayList<>();

            // find public static void main
            TypeDenoter strArrType = new ArrayType(new ClassType(new Identifier(new Token(TokenType.Identifier, "String", -1, -1)), new SourcePosition(-1, -1)), new SourcePosition(-1, -1));
            List<ClassDecl> mainClasses = new ArrayList<>();
            for (ClassDecl classDecl : prog.classDeclList) {
                for (MethodDecl methodDecl : classDecl.methodDeclList) {
                    // skip non main methods
                    if (!(methodDecl.name.equals("main")
                            && methodDecl.isStatic
                            && !methodDecl.isPrivate
                            && methodDecl.type.typeKind == TypeKind.VOID
                            && methodDecl.parameterDeclList.size() == 1
                            && TypeChecker.typeMatches(methodDecl.parameterDeclList.get(0).type, strArrType)
                    )) continue;

                    // account for main method
                    mainMethod = methodDecl;
                    mainClasses.add(classDecl);
                    System.out.printf("main class found: %s\n", classDecl.name);
                    break;
                }
            }

            // main method count error reporting
            if (mainClasses.isEmpty()) {
                throw new CodeGenerationError("No instances of public static void main(String[]) found");
            }
            if (mainClasses.size() >= 2) {
                StringBuilder sb = new StringBuilder("Multiple instances of public static void main(String[]) found: ");
                for (ClassDecl classDecl : mainClasses) {
                    sb.append(classDecl.name + ", ");
                }
                sb.setLength(sb.length() - 2);
                throw new CodeGenerationError(sb.toString());
            }

            // create bookkeeping objects
            asm = new InstructionList();
            blockScopeStackSizes = new Stack<>();
            UnresolvedAddress.labelMap.clear();
            nextNonce = 0;

            // store stack base at r15
            instr(new Mov_rmr(new ModRMSIB(Reg64.R15, Reg64.RSP)));

            // resolve fields (add statics below main stackframe)
            long stackBase = 0;
            instr(new Xor(new ModRMSIB(Reg64.RAX, Reg64.RAX)));
            for (ClassDecl classDecl : prog.classDeclList) {
                classDecl.memOffset = stackBase;
                long classMemOffset = 0;
                for (FieldDecl fieldDecl : classDecl.fieldDeclList) {
                    if (fieldDecl.isStatic) {
                        fieldDecl.memOffset = stackBase;
                        stackBase -= 8;
                        instr(new Push(Reg64.RAX));
                    } else {
                        fieldDecl.memOffset = classMemOffset;
                        classMemOffset += 8;
                    }
                    System.out.printf("field %s.%s mem offset: %d\n", classDecl.name, fieldDecl.name, fieldDecl.memOffset);
                }
                classDecl.memSize = classMemOffset;
            }
            System.out.printf("stack base: %d\n", stackBase);

            // add call to main (copy arg passed into main to top of stack)
            instr(new Mov_rrm(new ModRMSIB(Reg64.R15, -8, Reg64.RAX)));
            instr(new Push(Reg64.RAX));
            addUnresolved(instr(new Call(0,0)), mainMethod);

            // exit
            addExit();

            // main code generation
            prog.visit(this, null);

            // resolve unresolved jumps and calls
            for (UnresolvedAddress unresolvedAddress : unresolvedAddressList) {
                unresolvedAddress.resolve();
            }

            // Output the file "a.out" if no errors
            if (!errors.hasErrors())
                makeElf("a.out");
        } catch(CodeGenerationError e) {
            errors.reportError(e.getMessage());
        }
    }

    String genNonce() {
        String nonce = Long.toString(nextNonce++);
        return nonce;
    };

    public void makeElf(String fname) {
        ELFMaker elf = new ELFMaker(errors, asm.getSize(), 8); // bss ignored until PA5, set to 8
        elf.outputELF(fname, asm.getBytes(), 0);
    }

    private int instr(Instruction instr) {
        return asm.add(instr);
    }

    private int addMalloc() {
        int idxStart = instr( new Mov_rmi(new ModRMSIB(Reg64.RAX,true),0x09) ); // mmap

        instr(new Xor(		new ModRMSIB(Reg64.RDI,Reg64.RDI)) 	); // addr=0
        instr(new Mov_rmi(	new ModRMSIB(Reg64.RSI,true),0x1000) ); // 4kb alloc
        instr(new Mov_rmi(	new ModRMSIB(Reg64.RDX,true),0x03) 	); // prot read|write
        instr(new Mov_rmi(	new ModRMSIB(Reg64.R10,true),0x22) 	); // flags= private, anonymous
        instr(new Mov_rmi(	new ModRMSIB(Reg64.R8, true),-1) 	); // fd= -1
        instr(new Xor(		new ModRMSIB(Reg64.R9,Reg64.R9)) 	); // offset=0
        instr(new Syscall() );

        // pointer to newly allocated memory is in RAX
        // return the index of the first instruction in this method, if needed
        return idxStart;
    }

    // rdx: # bytes to write
    // rsi: pointer to char buffer
    private int addPrintln() {
        int idxStart = asm.getSize();
        instr(new Mov_rmi(new ModRMSIB(Reg64.RAX, true), 1)); // set syscall to SYS_write
        instr(new Mov_rmi(new ModRMSIB(Reg64.RDI, true), 1)); // set output to fd standard out
        instr(new Syscall());
        return idxStart;
    }

    private int addExit() {
        int idxStart = asm.getSize();
        instr(new Mov_rmi(new ModRMSIB(Reg64.RAX, true), 60));
        instr(new Xor(new ModRMSIB(Reg64.RDI, Reg64.RDI)));
        instr(new Syscall());
        return idxStart;
    }

    /*  stackframe structure (on entry)
        METHOD ARGS (first arg = lowest addr)
        +0x40+ (if CALL_REG_DUMP false, +0x10+)
        ENTRY TIME REGISTERS (if CALL_REG_DUMP)
        +0x38 RDI
        +0x30 RSI
        +0x28 RBX
        +0x20 RDX
        +0x18 RCX
        +0x10 RAX
        +0x08 RIP (Return Addr)
        +0x00 RBP (Last Frame RBP) <- RBP
        LOCAL VARIABLES
        -0x16 --- <- RSP
     */

    final int ARG_OFFSET = CALL_REG_DUMP ? 0x40 : 0x10; // RBP - ARG_OFFSET = address of first argument

    @Override
    public Object visitPackage(Package prog, Object arg) {
        prog.asmOffset = asm.getSize();
        for (ClassDecl classDecl : prog.classDeclList) {
            classDecl.visit(this, arg);
        }
        return null;
    }

    @Override
    public Object visitClassDecl(ClassDecl cd, Object arg) {
        currentClass = cd;
        cd.asmOffset = asm.getSize();
        for (MethodDecl methodDecl : cd.methodDeclList) {
            methodDecl.visit(this, arg);
        }
        return null;
    }

    @Override
    public Object visitFieldDecl(FieldDecl fd, Object arg) {
        throw new CodeGenerationError("visitFieldDecl should not be called");
    }

    // adds label at current instruction address
    private void addLabel(String label) {
        UnresolvedAddress.labelMap.put(label, asm.getSize());
        System.out.printf("label %s: 0x%x\n", label, asm.getSize() + 0x1b0);
    }

    private void checkThisMemOffset() {
        if (thisMemOffset == Integer.MIN_VALUE) {
            throw new IllegalArgumentException("thisMemOffset not defined in context");
        }
    }

    @Override
    public Object visitMethodDecl(MethodDecl md, Object arg) {
        currentMethod = md;
        md.asmOffset = asm.getSize();
        System.out.printf("method %s.%s address: 0x%x\n", md.parent.name, md.name, md.asmOffset+0x1b0);

        // PROLOGUE
        // store entry time register values onto stack
        if (CALL_REG_DUMP) {
            instr(new Push(Reg64.RDI));
            instr(new Push(Reg64.RSI));
            instr(new Push(Reg64.RBX));
            instr(new Push(Reg64.RDX));
            instr(new Push(Reg64.RCX));
            instr(new Push(Reg64.RAX));
        }

        // update rbp and rsp
        instr(new Push(Reg64.RBP)); // push rbp
        instr(new Mov_rmr(new ModRMSIB(Reg64.RBP, Reg64.RSP))); // mov rbp,rsp

        // map parameters
        int paramOffset = ARG_OFFSET;
        for (ParameterDecl param : md.parameterDeclList) {
            param.memOffset = paramOffset;
            System.out.printf("param %s: 0x%x\n", param.name, paramOffset);
            paramOffset += 8;
        }
        if (md.isStatic) {
            thisMemOffset = Integer.MIN_VALUE;
        } else {
            System.out.printf("param this: 0x%x\n", paramOffset);
            thisMemOffset = paramOffset;
        }
        rbpOffset = 0;
        blockScopeStackSizes.clear();
        blockScopeStackSizes.push(0);

        // BODY
        if (md == MethodDecl.printlnMethod) {
            // special println segment
            int memOffset = (int)md.parameterDeclList.get(0).memOffset;
            instr(new Lea(new ModRMSIB(Reg64.RBP, memOffset, Reg64.RSI)));
            instr(new Mov_rmi(new ModRMSIB(Reg64.RDX, true), 1));
            addPrintln();
        } else {
            for (Statement stmt : md.statementList) {
                stmt.visit(this, arg);
            }
        }

        // EPILOGUE
        addLabel(String.format("%s.%s.epilogue", md.parent.name, md.name));
        // update rbp and rsp
        instr(new Mov_rmr(new ModRMSIB(Reg64.RSP, Reg64.RBP))); // mov rsp,rbp (pop all local variables)
        instr(new Pop(Reg64.RBP)); // pop rbp

        // restore entry time registers
        if (CALL_REG_DUMP) {
            instr(new Pop(Reg64.RAX));
            instr(new Pop(Reg64.RCX));
            instr(new Pop(Reg64.RDX));
            instr(new Pop(Reg64.RBX));
            instr(new Pop(Reg64.RSI));
            instr(new Pop(Reg64.RDI));
        }

        instr(new Ret());
        return null;
    }

    private int typeSize(TypeDenoter type) {
        if (type instanceof ClassType) {
            return 8;
        }
        switch (type.typeKind) {
            case INT: return 4;
            case BOOLEAN: return 1;
            case ARRAY: return 8;
            default: throw new CodeGenerationError(String.format("typeSize called for %s type", type.typeKind));
        }
    }

    @Override
    public Object visitParameterDecl(ParameterDecl pd, Object arg) {
        throw new CodeGenerationError("visitParameterDecl should not be called");
    }

    @Override
    public Object visitVarDecl(VarDecl decl, Object arg) {
        throw new CodeGenerationError("visitVarDecl should not be called");
    }

    @Override
    public Object visitBaseType(BaseType type, Object arg) {
        throw new CodeGenerationError("visitBaseType should not be called");
    }

    @Override
    public Object visitClassType(ClassType type, Object arg) {
        throw new CodeGenerationError("visitClassType should not be called");
    }

    @Override
    public Object visitArrayType(ArrayType type, Object arg) {
        throw new CodeGenerationError("visitArrayType should not be called");
    }

    @Override
    public Object visitBlockStmt(BlockStmt stmt, Object arg) {
        stmt.asmOffset = asm.getSize();
        blockScopeStackSizes.push(0);
        for (Statement nestedStmt : stmt.sl) {
            nestedStmt.visit(this, arg);
        }
        int popSize = blockScopeStackSizes.pop();
        rbpOffset += popSize;
        instr(new Lea(new ModRMSIB(Reg64.RSP, popSize, Reg64.RSP)));
        return null;
    }

    // push var onto stack in bookkeeping
    private void stackAlloc(VarDecl var) {
        rbpOffset -= 8;
        var.memOffset = rbpOffset;
        blockScopeStackSizes.push(blockScopeStackSizes.pop() + 8);
    }

    @Override
    public Object visitVardeclStmt(VarDeclStmt stmt, Object arg) {
        stmt.asmOffset = asm.getSize();
        stmt.initExp.visit(this, arg);
        stackAlloc(stmt.varDecl);
        return null;
    }

    @Override
    public Object visitAssignStmt(AssignStmt stmt, Object arg) {
        stmt.asmOffset = asm.getSize();
        stmt.ref.visit(this, arg);
        stmt.val.visit(this, arg);
        instr(new Pop(Reg64.RAX));
        instr(new Pop(Reg64.RDI));
        instr(new Mov_rmr(new ModRMSIB(Reg64.RDI, 0, Reg64.RAX)));
        return null;
    }

    // load address of array element into register reg (can't be RCX)
    // clobbers RCX
    private void loadArrayElement(Reference arrRef, Expression ixExpr, Reg64 reg) {
        if (reg == Reg64.RCX) throw new IllegalArgumentException("loadArrayElement reg cannot be RCX");
        arrRef.visit(this, null);
        ixExpr.visit(this, null);
        instr(new Pop(Reg64.RCX));
        instr(new Pop(reg));
        instr(new Mov_rrm(new ModRMSIB(reg, 0, reg)));
        instr(new Lea(new ModRMSIB(reg, Reg64.RCX, 8, 8, reg)));
    }

    @Override
    public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
        stmt.asmOffset = asm.getSize();
        stmt.exp.visit(this, arg);
        loadArrayElement(stmt.ref, stmt.ix, Reg64.RDI);
        instr(new Pop(Reg64.RAX));
        instr(new Mov_rmr(new ModRMSIB(Reg64.RDI, 0, Reg64.RAX)));
        return null;
    }

    // assume current object reference at top of stack
    private void handleCall(ExprList argList, Reference methodRef) {
        methodRef.visit(this, null);
        int argBytes = (((MethodDecl)methodRef.decl).isStatic ? 0 : 8) + argList.size() * 8;
        for (int i = argList.size()-1; i >= 0; i--) {
            argList.get(i).visit(this, null);
        }
        addUnresolved(instr(new Call(0, 0)), (MethodDecl)methodRef.decl);
        if (argBytes > 0) instr(new Add(new ModRMSIB(Reg64.RSP, true), argBytes));
    }

    @Override
    public Object visitCallStmt(CallStmt stmt, Object arg) {
        stmt.asmOffset = asm.getSize();
        handleCall(stmt.argList, stmt.methodRef);
        return null;
    }

    @Override
    public Object visitReturnStmt(ReturnStmt stmt, Object arg) {
        stmt.asmOffset = asm.getSize();
        if (stmt.returnExpr != null) {
            stmt.returnExpr.visit(this, arg);
            instr(new Pop(Reg64.R8));
        }
        addUnresolved(instr(new Jmp(0,0,false)), String.format("%s.%s.epilogue", currentClass.name, currentMethod.name));
        return null;
    }

    @Override
    public Object visitIfStmt(IfStmt stmt, Object arg) {
        stmt.asmOffset = asm.getSize();

        // condition
        stmt.cond.visit(this, arg);
        instr(new Pop(Reg64.RAX));
        instr(new Cmp(new ModRMSIB(Reg64.RAX, true), 0)); // check if false
        String ifSkipLabel = genNonce();
        addUnresolved(instr(new CondJmp(Condition.E, 0, 0, false)), ifSkipLabel); // jump if false

        // then
        stmt.thenStmt.visit(this, arg);

        // else
        if (stmt.elseStmt != null) {
            String elseEndLabel = genNonce();
            addUnresolved(instr(new Jmp(0, 0, false)), elseEndLabel);
            addLabel(ifSkipLabel);
            stmt.elseStmt.visit(this, arg);
            addLabel(elseEndLabel);
        } else {
            addLabel(ifSkipLabel);
        }
        return null;
    }

    @Override
    public Object visitWhileStmt(WhileStmt stmt, Object arg) {
        stmt.asmOffset = asm.getSize();

        // initial jump
        String condJmpLabel = "condJmpLabel " + genNonce();
        addUnresolved(instr(new Jmp(0, 0, false)), condJmpLabel);

        // body
        int loopTopAddress = asm.getSize();
        stmt.body.visit(this, arg);

        // condition
        addLabel(condJmpLabel);
        stmt.cond.visit(this, arg);
        instr(new Pop(Reg64.RAX));
        instr(new Cmp(new ModRMSIB(Reg64.RAX, true), 1)); // check if true
        instr(new CondJmp(Condition.E, asm.getSize(), loopTopAddress, false)); // jump if true
        return null;
    }

    @Override
    public Object visitUnaryExpr(UnaryExpr expr, Object arg) {
        expr.asmOffset = asm.getSize();
        expr.expr.visit(this, arg);
        instr(new Pop(Reg64.RAX));
        switch (expr.operator.kind) {
            case Minus:
                instr(new Xor(new ModRMSIB(Reg64.RCX, Reg64.RCX)));
                instr(new Sub(new ModRMSIB(Reg64.RCX, Reg64.RAX)));
                instr(new Mov_rmr(new ModRMSIB(Reg64.RAX, Reg64.RCX)));
                break;
            case LogNot:
                instr(new Xor(new ModRMSIB(Reg64.RAX, true), 1));
                break;
            default:
                throw new CodeGenerationError(String.format("unary operator %s not supported\n", expr.operator));
        }
        instr(new Push(Reg64.RAX));
        return null;
    }

    @Override
    public Object visitBinaryExpr(BinaryExpr expr, Object arg) {
        expr.asmOffset = asm.getSize();
        expr.left.visit(this, arg);
        expr.right.visit(this, arg);
        instr(new Pop(Reg64.RCX));
        instr(new Pop(Reg64.RAX));
        Condition cond = null;
        switch (expr.operator.kind) {
            case Add:
                instr(new Add(new ModRMSIB(Reg64.RAX, Reg64.RCX)));
                break;
            case Minus:
                instr(new Sub(new ModRMSIB(Reg64.RAX, Reg64.RCX)));
                break;
            case Multiply:
                instr(new Imul(Reg64.RAX, new ModRMSIB(Reg64.RCX, true)));
                break;
            case Divide:
                instr(new Idiv(new ModRMSIB(Reg64.RCX, true)));
                break;
            case RelEq:
                cond = Condition.E;
                break;
            case RelNEq:
                cond = Condition.NE;
                break;
            case RelLT:
                cond = Condition.LT;
                break;
            case RelGT:
                cond = Condition.GT;
                break;
            case RelLEq:
                cond = Condition.LTE;
                break;
            case RelGEq:
                cond = Condition.GTE;
                break;
            default:
                throw new CodeGenerationError(String.format("binary operator %s not supported\n", expr.operator));
        }
        if (cond != null) {
            instr(new Cmp(new ModRMSIB(Reg64.RAX, Reg64.RCX)));
            instr(new SetCond(cond, Reg8.AL));
            instr(new And(new ModRMSIB(Reg64.RAX, true), 0x1));
        }
        instr(new Push(Reg64.RAX));
        return null;
    }

    @Override
    public Object visitRefExpr(RefExpr expr, Object arg) {
        expr.asmOffset = asm.getSize();
        expr.ref.visit(this, arg);
        instr(new Pop(Reg64.RAX));
        instr(new Mov_rrm(new ModRMSIB(Reg64.RAX, 0, Reg64.RAX)));
        instr(new Push(Reg64.RAX));
        return null;
    }

    @Override
    public Object visitIxExpr(IxExpr expr, Object arg) {
        expr.asmOffset = asm.getSize();
        loadArrayElement(expr.ref, expr.ixExpr, Reg64.RAX);
        instr(new Mov_rrm(new ModRMSIB(Reg64.RAX, 0, Reg64.RAX)));
        instr(new Push(Reg64.RAX));
        return null;
    }

    @Override
    public Object visitCallExpr(CallExpr expr, Object arg) {
        expr.asmOffset = asm.getSize();
        handleCall(expr.argList, expr.functionRef);
        instr(new Push(Reg64.R8));
        return null;
    }

    @Override
    public Object visitLiteralExpr(LiteralExpr expr, Object arg) {
        expr.asmOffset = asm.getSize();
        int val = (Integer)expr.lit.visit(this, arg);
        instr(new Mov_rmi(new ModRMSIB(Reg64.RAX, true), val));
        instr(new Push(Reg64.RAX));
        return null;
    }

    @Override
    public Object visitNewObjectExpr(NewObjectExpr expr, Object arg) {
        expr.asmOffset = asm.getSize();
        addMalloc();
        instr(new Push(Reg64.RAX));
        return null;
    }

    @Override
    public Object visitNewArrayExpr(NewArrayExpr expr, Object arg) {
        expr.asmOffset = asm.getSize();
        expr.sizeExpr.visit(this, arg);
        addMalloc();
        instr(new Pop(Reg64.RCX));
        instr(new Push(Reg64.RAX));

        // set size
        instr(new Mov_rmr(new ModRMSIB(Reg64.RDI, Reg64.RAX)));
        instr(new Mov_rmr(new ModRMSIB(Reg64.RDI, 0, Reg64.RCX)));
        instr(new Add(new ModRMSIB(Reg64.RDI, true), 8));

        // set default values
        instr(new Xor(new ModRMSIB(Reg64.RAX, Reg64.RAX)));
        instr(new ClearDirFlag());
        instr(new Rep());

        return null;
    }

    @Override
    public Object visitThisRef(ThisRef ref, Object arg) {
        ref.asmOffset = asm.getSize();
        checkThisMemOffset();
        ref.decl.memOffset = thisMemOffset;
        instr(new Lea(new ModRMSIB(Reg64.RBP, thisMemOffset, Reg64.RAX)));
        instr(new Push(Reg64.RAX));
        return null;
    }

    // push ref address onto stack
    // for case of method decl, pushes context object if nonstatic and nothing otherwise
    // clobbers RAX and RDI
    void pushRefAddress(Reference ref) {
        // load ref address into rax
        if (ref.decl instanceof ClassDecl) {
            instr(new Lea(new ModRMSIB(Reg64.R15, (int) ref.decl.memOffset, Reg64.RAX)));
        } else if (ref.decl instanceof FieldDecl) {
            if (((FieldDecl) ref.decl).isStatic) {
                instr(new Lea(new ModRMSIB(Reg64.R15, (int) ref.decl.memOffset, Reg64.RAX)));
            } else {
                if (ref instanceof QualRef) {
                    ((QualRef)ref).ref.visit(this, null);
                    instr(new Pop(Reg64.RSI));
                    instr(new Mov_rrm(new ModRMSIB(Reg64.RSI, 0, Reg64.RSI)));
                } else {
                    checkThisMemOffset();
                    instr(new Mov_rrm(new ModRMSIB(Reg64.RBP, thisMemOffset, Reg64.RSI)));
                }
                instr(new Lea(new ModRMSIB(Reg64.RSI, (int) ref.decl.memOffset, Reg64.RAX)));
            }
        } else if (ref.decl instanceof LocalDecl) {
            instr(new Lea(new ModRMSIB(Reg64.RBP, (int) ref.decl.memOffset, Reg64.RAX)));
        } else if (ref.decl instanceof MethodDecl) {
            MethodDecl method = (MethodDecl)ref.decl;
            if (method.isStatic) return;
            if (ref instanceof QualRef) {
                ((QualRef)ref).ref.visit(this, null);
                return;
            } else if (ref instanceof IdRef) {
                instr(new Lea(new ModRMSIB(Reg64.RBP, thisMemOffset, Reg64.RAX)));
            }
        } else {
            throw new CodeGenerationError(String.format("unknown declaration subclass for ref %s", ref.decl.name));
        }

        // push ref address onto stack
        instr(new Push(Reg64.RAX));
    }

    @Override
    public Object visitIdRef(IdRef ref, Object arg) {
        ref.asmOffset = asm.getSize();
        pushRefAddress(ref);
        return null;
    }

    @Override
    public Object visitQRef(QualRef ref, Object arg) {
        ref.asmOffset = asm.getSize();
        pushRefAddress(ref);
        return null;
    }

    @Override
    public Object visitIdentifier(Identifier id, Object arg) {
        throw new CodeGenerationError("visitIdentifier should not be called");
    }

    @Override
    public Object visitOperator(Operator op, Object arg) {
        throw new CodeGenerationError("visitOperator should not be called");
    }

    @Override
    public Object visitIntLiteral(IntLiteral num, Object arg) {
        num.asmOffset = asm.getSize();
        return Integer.valueOf(num.spelling);
    }

    @Override
    public Object visitBooleanLiteral(BooleanLiteral bool, Object arg) {
        bool.asmOffset = asm.getSize();
        return bool.spelling.equals("true") ? 1 : 0;
    }

    @Override
    public Object visitNullLiteral(NullLiteral nullLiteral, Object arg) {
        nullLiteral.asmOffset = asm.getSize();
        return 0;
    }
}
