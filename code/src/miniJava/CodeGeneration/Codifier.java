package miniJava.CodeGeneration;

import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.CodeGeneration.x64.ISA.Mov_rmi;
import miniJava.CodeGeneration.x64.ISA.Syscall;
import miniJava.CodeGeneration.x64.ISA.Xor;
import miniJava.CodeGeneration.x64.InstructionList;
import miniJava.CodeGeneration.x64.ModRMSIB;
import miniJava.CodeGeneration.x64.Reg64;
import miniJava.ErrorReporter;

public class Codifier implements Visitor<Object, Object> {
    private ErrorReporter errors;
    private InstructionList asm;
    public Codifier(ErrorReporter errors) {
        this.errors = errors;
    }

    public void parse(Package prog) {
        asm = new InstructionList();

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

        prog.visit(this,null);

        // Output the file "a.out" if no errors
        if( !errors.hasErrors() )
            makeElf("a.out");
    }

    public void makeElf(String fname) {
        ELFMaker elf = new ELFMaker(errors, asm.getSize(), 8); // bss ignored until PA5, set to 8
        elf.outputELF(fname, asm.getBytes(), ??); // TODO: set the location of the main method
    }

    private int makeMalloc() {
        int idxStart = asm.add( new Mov_rmi(new ModRMSIB(Reg64.RAX,true),0x09) ); // mmap

        asm.add( new Xor(		new ModRMSIB(Reg64.RDI,Reg64.RDI)) 	); // addr=0
        asm.add( new Mov_rmi(	new ModRMSIB(Reg64.RSI,true),0x1000) ); // 4kb alloc
        asm.add( new Mov_rmi(	new ModRMSIB(Reg64.RDX,true),0x03) 	); // prot read|write
        asm.add( new Mov_rmi(	new ModRMSIB(Reg64.R10,true),0x22) 	); // flags= private, anonymous
        asm.add( new Mov_rmi(	new ModRMSIB(Reg64.R8, true),-1) 	); // fd= -1
        asm.add( new Xor(		new ModRMSIB(Reg64.R9,Reg64.R9)) 	); // offset=0
        asm.add( new Syscall() );

        // pointer to newly allocated memory is in RAX
        // return the index of the first instruction in this method, if needed
        return idxStart;
    }

    private int makePrintln() {
        // TODO: how can we generate the assembly to println?
        return -1;
    }

    @Override
    public Object visitPackage(Package prog, Object arg) {
        return null;
    }

    @Override
    public Object visitClassDecl(ClassDecl cd, Object arg) {
        return null;
    }

    @Override
    public Object visitFieldDecl(FieldDecl fd, Object arg) {
        return null;
    }

    @Override
    public Object visitMethodDecl(MethodDecl md, Object arg) {
        return null;
    }

    @Override
    public Object visitParameterDecl(ParameterDecl pd, Object arg) {
        return null;
    }

    @Override
    public Object visitVarDecl(VarDecl decl, Object arg) {
        return null;
    }

    @Override
    public Object visitBaseType(BaseType type, Object arg) {
        return null;
    }

    @Override
    public Object visitClassType(ClassType type, Object arg) {
        return null;
    }

    @Override
    public Object visitArrayType(ArrayType type, Object arg) {
        return null;
    }

    @Override
    public Object visitBlockStmt(BlockStmt stmt, Object arg) {
        return null;
    }

    @Override
    public Object visitVardeclStmt(VarDeclStmt stmt, Object arg) {
        return null;
    }

    @Override
    public Object visitAssignStmt(AssignStmt stmt, Object arg) {
        return null;
    }

    @Override
    public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
        return null;
    }

    @Override
    public Object visitCallStmt(CallStmt stmt, Object arg) {
        return null;
    }

    @Override
    public Object visitReturnStmt(ReturnStmt stmt, Object arg) {
        return null;
    }

    @Override
    public Object visitIfStmt(IfStmt stmt, Object arg) {
        return null;
    }

    @Override
    public Object visitWhileStmt(WhileStmt stmt, Object arg) {
        return null;
    }

    @Override
    public Object visitUnaryExpr(UnaryExpr expr, Object arg) {
        return null;
    }

    @Override
    public Object visitBinaryExpr(BinaryExpr expr, Object arg) {
        return null;
    }

    @Override
    public Object visitRefExpr(RefExpr expr, Object arg) {
        return null;
    }

    @Override
    public Object visitIxExpr(IxExpr expr, Object arg) {
        return null;
    }

    @Override
    public Object visitCallExpr(CallExpr expr, Object arg) {
        return null;
    }

    @Override
    public Object visitLiteralExpr(LiteralExpr expr, Object arg) {
        return null;
    }

    @Override
    public Object visitNewObjectExpr(NewObjectExpr expr, Object arg) {
        return null;
    }

    @Override
    public Object visitNewArrayExpr(NewArrayExpr expr, Object arg) {
        return null;
    }

    @Override
    public Object visitThisRef(ThisRef ref, Object arg) {
        return null;
    }

    @Override
    public Object visitIdRef(IdRef ref, Object arg) {
        return null;
    }

    @Override
    public Object visitQRef(QualRef ref, Object arg) {
        return null;
    }

    @Override
    public Object visitIdentifier(Identifier id, Object arg) {
        return null;
    }

    @Override
    public Object visitOperator(Operator op, Object arg) {
        return null;
    }

    @Override
    public Object visitIntLiteral(IntLiteral num, Object arg) {
        return null;
    }

    @Override
    public Object visitBooleanLiteral(BooleanLiteral bool, Object arg) {
        return null;
    }

    @Override
    public Object visitNullLiteral(NullLiteral nullLiteral, Object o) {
        return null;
    }
}
