package miniJava.CodeGeneration;

import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.CodeGeneration.x64.*;
import miniJava.CodeGeneration.x64.ISA.*;
import miniJava.ContextualAnalysis.TypeChecker;
import miniJava.ErrorReporter;
import miniJava.SyntacticAnalyzer.SourcePosition;
import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenType;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class Codifier implements Visitor<Object, Object> {
    private static class UnresolvedAddress {
        private final AST target;
        private final Instruction instr;
        private final long relativeOffset;
        private final int byteOffset;
        public UnresolvedAddress(AST target, Instruction instr, int byteOffset, long relativeOffset) {
            this.target = target;
            this.instr = instr;
            this.byteOffset = byteOffset;
            this.relativeOffset = relativeOffset;
        }
        public UnresolvedAddress(AST target, Instruction instr, int byteOffset) {
            this(target, instr, byteOffset, 0);
        }
        public void resolve() {
            byte[] instrBytes = instr.getBytes();
            ByteArrayOutputStream addrBytes = new ByteArrayOutputStream();
            x64.writeLong(addrBytes, target.asmOffset + relativeOffset);
            int i = 0;
            for (byte b : addrBytes.toByteArray()) {
                instrBytes[byteOffset + i++] = b;
            }
        }
    }
    private ErrorReporter errors;
    private InstructionList asm;
    private MethodDecl mainMethod;
    private List<UnresolvedAddress> unresolvedAddressList;
    private StackAllocTable stackAllocTable;
    public Codifier(ErrorReporter errors) {
        this.errors = errors;
    }
    private long stackBase; // base of stack (offset such that statics stored below base)

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
                    break;
                }
            }

            // main method count error reporting
            if (mainClasses.isEmpty()) {
                throw new CodeGenerationError("No instances of public static void main(String[]) found");
            }
            if (mainClasses.size() > 2) {
                StringBuilder sb = new StringBuilder("Multiple instances of public static void main(String[]) found: ");
                for (ClassDecl classDecl : mainClasses) {
                    sb.append(classDecl.name + ", ");
                }
                sb.setLength(sb.length() - 2);
                throw new CodeGenerationError(sb.toString());
            }

            // create asm list
            asm = new InstructionList();

            // resolve fields (make space below stack for statics)
            stackBase = 0;
            instr(new Mov_rmi(new ModRMSIB(Reg64.RAX, true), 0)); // set RAX to 0 to use in push
            for (ClassDecl classDecl : prog.classDeclList) {
                long classMemOffset = 0;
                for (FieldDecl fieldDecl : classDecl.fieldDeclList) {
                    if (fieldDecl.isStatic) {
                        fieldDecl.memOffset = stackBase;
                        stackBase += 8;
                        instr(new Push(Reg64.RAX));
                    } else {
                        fieldDecl.memOffset = classMemOffset;
                        classMemOffset += 8;
                    }
                    System.out.printf("%s.%s mem offset: %d\n", classDecl.name, fieldDecl.name, fieldDecl.memOffset);
                }
            }
            System.out.printf("stack base: %d\n", stackBase);

            // add jump to main
            int mainJmpIdx = instr(new Jmp(0));

            // main code generation
            stackAllocTable = new StackAllocTable();
            prog.visit(this, null);

            // resolve unresolved addresses
            for (UnresolvedAddress unresolvedAddress : unresolvedAddressList) {
                unresolvedAddress.resolve();
            }
            asm.patch(mainJmpIdx, new Jmp((int)asm.get(mainJmpIdx).startAddress, (int)mainMethod.asmOffset, false));

            // Output the file "a.out" if no errors
            if (!errors.hasErrors())
                makeElf("a.out");
        } catch(CodeGenerationError e) {
            errors.reportError(e.getMessage());
        }
    }

    public void makeElf(String fname) {
        ELFMaker elf = new ELFMaker(errors, asm.getSize(), 8); // bss ignored until PA5, set to 8
        elf.outputELF(fname, asm.getBytes(), 0);
    }

    private int instr(Instruction instr) {
        return asm.add(instr);
    }

    private long addMalloc() {
        long idxStart = instr( new Mov_rmi(new ModRMSIB(Reg64.RAX,true),0x09) ); // mmap

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
        // TODO: how can we generate the assembly to println?
        int idxStart = asm.getSize();
        instr(new Mov_rmi(new ModRMSIB(Reg64.RAX, true), 1)); // set syscall to SYS_write
        instr(new Mov_rmi(new ModRMSIB(Reg64.RDI, true), 1)); // set output to fd standard out
        instr(new Syscall());
        return idxStart;
    }

    // TODO: maybe skip storing registers on stack

    /*  stackframe structure (on entry)
        METHOD ARGS (first arg = lowest addr)
        +0x38+
        ENTRY TIME REGISTERS
        +0x30 RDI
        +0x28 RSI
        +0x20 RBX
        +0x18 RDX
        +0x10 RCX
        +0x08 RAX
        +0x00 RBP (Last Frame RBP) <- RBP
        -0x08 RIP (Return Addr) <- RSP
        LOCAL VARIABLES
        -0x16 ---
     */

    final long ARG_OFFSET = -0x38; // RBP + ARG_OFFSET = address of first argument

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

    @Override
    public Object visitMethodDecl(MethodDecl md, Object arg) {
        md.asmOffset = asm.getSize();

        // PROLOGUE
        // store entry time register values onto stack
        instr(new Push(Reg64.RDI));
        instr(new Push(Reg64.RSI));
        instr(new Push(Reg64.RBX));
        instr(new Push(Reg64.RDX));
        instr(new Push(Reg64.RCX));
        instr(new Push(Reg64.RAX));

        // update rbp and rsp
        instr(new Push(Reg64.RBP)); // push rbp
        instr(new Mov_rmr(new ModRMSIB(Reg64.RBP, Reg64.RSP))); // mov rbp,rsp

        // BODY
        // push parameters onto stack

        for (Statement stmt : md.statementList) {
            stmt.visit(this, arg);
        }

        // EPILOGUE
        // update rbp and rsp
        instr(new Mov_rmr(new ModRMSIB(Reg64.RSP, Reg64.RBP))); // mov rsp,rbp (pop all local variables)
        instr(new Pop(Reg64.RBP)); // pop rbp

        // restore entry time registers
        instr(new Pop(Reg64.RAX));
        instr(new Pop(Reg64.RCX));
        instr(new Pop(Reg64.RDX));
        instr(new Pop(Reg64.RBX));
        instr(new Pop(Reg64.RSI));
        instr(new Pop(Reg64.RDI));

        // return
        instr(new Ret()); // TODO: handle return values
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

    private void stackAlloc(TypeDenoter type, String name) {
        stackAllocTable.pushStackVariable(name, typeSize(type));
        instr(new Xor(new ModRMSIB(Reg64.RAX, Reg64.RAX)));
        instr(new Push(Reg64.RAX));
    }

    @Override
    public Object visitParameterDecl(ParameterDecl pd, Object arg) {
        throw new CodeGenerationError("visitParameterDecl should not be visited");
    }

    @Override
    public Object visitVarDecl(VarDecl decl, Object arg) {
        throw new CodeGenerationError("visitVarDecl should not be visited");
    }

    @Override
    public Object visitBaseType(BaseType type, Object arg) {
        throw new CodeGenerationError("visitBaseType should not be visited");
    }

    @Override
    public Object visitClassType(ClassType type, Object arg) {
        throw new CodeGenerationError("visitClassType should not be visited");
    }

    @Override
    public Object visitArrayType(ArrayType type, Object arg) {
        throw new CodeGenerationError("visitArrayType should not be visited");
    }

    @Override
    public Object visitBlockStmt(BlockStmt stmt, Object arg) {
        stmt.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitVardeclStmt(VarDeclStmt stmt, Object arg) {
        stmt.asmOffset = asm.getSize();
        stmt.varDecl.visit(this, arg);
        return null;
    }

    @Override
    public Object visitAssignStmt(AssignStmt stmt, Object arg) {
        stmt.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
        stmt.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitCallStmt(CallStmt stmt, Object arg) {
        stmt.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitReturnStmt(ReturnStmt stmt, Object arg) {
        stmt.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitIfStmt(IfStmt stmt, Object arg) {
        stmt.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitWhileStmt(WhileStmt stmt, Object arg) {
        stmt.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitUnaryExpr(UnaryExpr expr, Object arg) {
        expr.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitBinaryExpr(BinaryExpr expr, Object arg) {
        expr.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitRefExpr(RefExpr expr, Object arg) {
        expr.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitIxExpr(IxExpr expr, Object arg) {
        expr.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitCallExpr(CallExpr expr, Object arg) {
        expr.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitLiteralExpr(LiteralExpr expr, Object arg) {
        expr.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitNewObjectExpr(NewObjectExpr expr, Object arg) {
        expr.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitNewArrayExpr(NewArrayExpr expr, Object arg) {
        expr.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitThisRef(ThisRef ref, Object arg) {
        ref.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitIdRef(IdRef ref, Object arg) {
        ref.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitQRef(QualRef ref, Object arg) {
        ref.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitIdentifier(Identifier id, Object arg) {
        id.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitOperator(Operator op, Object arg) {
        op.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitIntLiteral(IntLiteral num, Object arg) {
        num.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitBooleanLiteral(BooleanLiteral bool, Object arg) {
        bool.asmOffset = asm.getSize();
        return null;
    }

    @Override
    public Object visitNullLiteral(NullLiteral nullLiteral, Object arg) {
        nullLiteral.asmOffset = asm.getSize();
        return null;
    }
}
