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
    private long offset;
    private InstructionList asm;
    private MethodDecl mainMethod;
    private List<UnresolvedAddress> unresolvedAddresseList;
    private StackAllocTable stackAllocTable;
    public Codifier(ErrorReporter errors) {
        this.errors = errors;
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
            unresolvedAddresseList = new ArrayList<>();
            stackAllocTable = new StackAllocTable();

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
            offset = 0;
            prog.visit(this, null);

            // resolve unresolved addresses
            for (UnresolvedAddress unresolvedAddress : unresolvedAddresseList) {
                unresolvedAddress.resolve();
            }

            // Output the file "a.out" if no errors
            if (!errors.hasErrors())
                makeElf("a.out");
        } catch(CodeGenerationError e) {
            errors.reportError(e.getMessage());
        }
    }

    public void makeElf(String fname) {
        ELFMaker elf = new ELFMaker(errors, asm.getSize(), 8); // bss ignored until PA5, set to 8
        elf.outputELF(fname, asm.getBytes(), mainMethod.asmOffset); // TODO: set the location of the main method
    }

    private int instr(Instruction instr) {
        if (instr.startAddress != -1)
            throw new RuntimeException("addInstruct attempted to add a previously added instruction");
        instr.startAddress = offset;
        offset += instr.size();
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

    private int addPrintln() {
        // TODO: how can we generate the assembly to println?
        int idxStart = asm.getSize();
        instr(new Xor(new ModRMSIB(Reg64.RAX, Reg64.RAX)));
        instr(new Add(new ModRMSIB(Reg64.RAX, true), 1));
        instr(new Syscall());
        return idxStart;
    }

    // TODO: maybe skip storing registers on stack

    /*  stackframe structure (on entry)
        ENTRY TIME REGISTERS
        +0x70 R15
        +0x68 R14
        +0x60 R13
        +0x58 R12
        +0x50 R11
        +0x48 R10
        +0x40 R9
        +0x38 R8
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

    private void addFuncCall(Call call) {
        // store entry time register values onto stack
        instr(new Push(Reg64.R15));
        instr(new Push(Reg64.R14));
        instr(new Push(Reg64.R13));
        instr(new Push(Reg64.R12));
        instr(new Push(Reg64.R11));
        instr(new Push(Reg64.R10));
        instr(new Push(Reg64.R9));
        instr(new Push(Reg64.R8));
        instr(new Push(Reg64.RDI));
        instr(new Push(Reg64.RSI));
        instr(new Push(Reg64.RBX));
        instr(new Push(Reg64.RDX));
        instr(new Push(Reg64.RCX));
        instr(new Push(Reg64.RAX));

        // call
        instr(call);

        // restore registers
        instr(new Pop(Reg64.RAX));
        instr(new Pop(Reg64.RCX));
        instr(new Pop(Reg64.RDX));
        instr(new Pop(Reg64.RBX));
        instr(new Pop(Reg64.RSI));
        instr(new Pop(Reg64.RDI));
        instr(new Pop(Reg64.R8));
        instr(new Pop(Reg64.R8));
        instr(new Pop(Reg64.R9));
        instr(new Pop(Reg64.R10));
        instr(new Pop(Reg64.R11));
        instr(new Pop(Reg64.R12));
        instr(new Pop(Reg64.R13));
        instr(new Pop(Reg64.R14));
        instr(new Pop(Reg64.R15));
    }

    @Override
    public Object visitPackage(Package prog, Object arg) {
        prog.asmOffset = offset;
        for (ClassDecl classDecl : prog.classDeclList) {
            classDecl.visit(this, arg);
        }
        return null;
    }

    @Override
    public Object visitClassDecl(ClassDecl cd, Object arg) {
        cd.asmOffset = offset;
        for (FieldDecl fieldDecl : cd.fieldDeclList) {
            fieldDecl.visit(this, arg);
        }
        for (MethodDecl methodDecl : cd.methodDeclList) {
            methodDecl.visit(this, arg);
        }
        return null;
    }

    @Override
    public Object visitFieldDecl(FieldDecl fd, Object arg) {
        fd.asmOffset = offset;
        fd.type.visit(this, arg);
        return null;
    }

    @Override
    public Object visitMethodDecl(MethodDecl md, Object arg) {
        md.asmOffset = offset;

        // PROLOGUE
        // update rbp and rsp
        instr(new Push(Reg64.RBP)); // push rbp
        instr(new Mov_rmr(new ModRMSIB(Reg64.RBP, Reg64.RSP))); // mov rbp,rsp

        // BODY
        md.type.visit(this, arg);
        for (ParameterDecl pd : md.parameterDeclList) {
            pd.visit(this, arg);
        }
        for (Statement stmt : md.statementList) {
            stmt.visit(this, arg);
        }

        // EPILOGUE
        // update rbp and rsp
        instr(new Mov_rmr(new ModRMSIB(Reg64.RBP, Reg64.RSP))); // mov rbp,rsp (pop all local variables)
        instr(new Pop(Reg64.RBP)); // pop rbp
        instr(new Ret()); // TODO: handle return values
        return null;
    }

    @Override
    public Object visitParameterDecl(ParameterDecl pd, Object arg) {
        pd.asmOffset = offset;
        pd.type.visit(this, arg);
        return null;
    }

    @Override
    public Object visitVarDecl(VarDecl decl, Object arg) {
        decl.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitBaseType(BaseType type, Object arg) {
        type.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitClassType(ClassType type, Object arg) {
        type.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitArrayType(ArrayType type, Object arg) {
        type.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitBlockStmt(BlockStmt stmt, Object arg) {
        stmt.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitVardeclStmt(VarDeclStmt stmt, Object arg) {
        stmt.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitAssignStmt(AssignStmt stmt, Object arg) {
        stmt.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
        stmt.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitCallStmt(CallStmt stmt, Object arg) {
        stmt.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitReturnStmt(ReturnStmt stmt, Object arg) {
        stmt.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitIfStmt(IfStmt stmt, Object arg) {
        stmt.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitWhileStmt(WhileStmt stmt, Object arg) {
        stmt.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitUnaryExpr(UnaryExpr expr, Object arg) {
        expr.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitBinaryExpr(BinaryExpr expr, Object arg) {
        expr.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitRefExpr(RefExpr expr, Object arg) {
        expr.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitIxExpr(IxExpr expr, Object arg) {
        expr.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitCallExpr(CallExpr expr, Object arg) {
        expr.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitLiteralExpr(LiteralExpr expr, Object arg) {
        expr.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitNewObjectExpr(NewObjectExpr expr, Object arg) {
        expr.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitNewArrayExpr(NewArrayExpr expr, Object arg) {
        expr.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitThisRef(ThisRef ref, Object arg) {
        ref.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitIdRef(IdRef ref, Object arg) {
        ref.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitQRef(QualRef ref, Object arg) {
        ref.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitIdentifier(Identifier id, Object arg) {
        id.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitOperator(Operator op, Object arg) {
        op.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitIntLiteral(IntLiteral num, Object arg) {
        num.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitBooleanLiteral(BooleanLiteral bool, Object arg) {
        bool.asmOffset = offset;
        return null;
    }

    @Override
    public Object visitNullLiteral(NullLiteral nullLiteral, Object arg) {
        nullLiteral.asmOffset = offset;
        return null;
    }
}
