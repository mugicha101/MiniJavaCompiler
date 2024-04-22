package miniJava.CodeGeneration;

import miniJava.AbstractSyntaxTrees.TypeKind;
import miniJava.CodeGeneration.x64.*;
import miniJava.CodeGeneration.x64.ISA.*;

public class ALUHandler {
    public final InstructionList asm;
    public final FPUHandler fpu;
    public ALUHandler(InstructionList asm, FPUHandler fpu) {
        this.asm = asm;
        this.fpu = fpu;
    }

    private int instr(Instruction instr) {
        return asm.add(instr);
    }

    // sign extend int types
    private void intSignExtend(TypeKind type, boolean both) {
        if (type == null) return;
        switch (type) {
            case CHAR:
                // movsx eax,al
                instr(new CustomInstruction(new byte[]{(byte)0x0f, (byte)0xbe, (byte)0xc0}));
                if (both)
                    instr(new CustomInstruction(new byte[]{(byte)0x0f, (byte)0xbe, (byte)0xc9}));
            case INT:
                // movsxd rax,eax
                instr(new CustomInstruction(new byte[]{(byte)0x48, (byte)0x63, (byte)0xc0}));
                if (both)
                    instr(new CustomInstruction(new byte[]{(byte)0x48, (byte)0x63, (byte)0xc9}));
                break;
        }
    }

    // truncate int types
    private void intTruncate(TypeKind type) {
        if (type == null) return;
        // trick: moving smaller variant of rax to itself clears excess bits in rax
        switch (type) {
            case CHAR:
                // mov al,al
                instr(new CustomInstruction(new byte[]{(byte)0x88, (byte)0xc0}));
                break;
            case INT:
                // move eax,eax
                instr(new CustomInstruction(new byte[]{(byte)0x89, (byte)0xc0}));
                break;
        }
    }

    private boolean notFloat(TypeKind type) {
        return type != TypeKind.FLOAT && type != TypeKind.DOUBLE;
    }

    // BINARY EPXRESSIONS
    // operands in RAX and RCX
    // stores result into RAX
    // may clobber FPU registers
    // idiv clobbers RDX
    public void add(TypeKind type) {
        if (notFloat(type)) {
            intSignExtend(type, true);
            instr(new Add(new ModRMSIB(Reg64.RAX, Reg64.RCX)));
            intTruncate(type);
        } else {
            fpu.clear();
            fpu.setDblPrecision(type == TypeKind.DOUBLE);
            fpu.load(false, true);
            fpu.add();
            fpu.store(false);
        }
    }

    public void sub(TypeKind type) {
        if (notFloat(type)) {
            intSignExtend(type, true);
            instr(new Sub(new ModRMSIB(Reg64.RAX, Reg64.RCX)));
            intTruncate(type);
        } else {
            fpu.clear();
            fpu.setDblPrecision(type == TypeKind.DOUBLE);
            fpu.load(false, true);
            fpu.sub();
            fpu.store(false);
        }
    }
    public void mul(TypeKind type) {
        if (notFloat(type)) {
            intSignExtend(type, true);
            instr(new Imul(Reg64.RAX, new ModRMSIB(Reg64.RCX, true)));
            intTruncate(type);
        } else {
            fpu.clear();
            fpu.setDblPrecision(type == TypeKind.DOUBLE);
            fpu.load(false, true);
            fpu.mul();
            fpu.store(false);
        }
    }

    public void div(TypeKind type) {
        if (notFloat(type)) {
            intSignExtend(type, true);
            instr(new CustomInstruction(new byte[]{(byte) 0x48, (byte) 0x99}, new byte[]{})); // sign extend RAX to RDX:RAX
            instr(new Idiv(new ModRMSIB(Reg64.RCX, true)));
            intTruncate(type);
        } else {
            fpu.clear();
            fpu.setDblPrecision(type == TypeKind.DOUBLE);
            fpu.load(false, true);
            fpu.div();
            fpu.store(false);
        }
    }

    public void cmp(TypeKind type, Condition cond) {
        if (notFloat(type)) {
            intSignExtend(type, true);
            instr(new Cmp(new ModRMSIB(Reg64.RAX, Reg64.RCX)));
        } else {
            fpu.clear();
            fpu.setDblPrecision(type == TypeKind.DOUBLE);
            fpu.load(false,true);
            fpu.cmp();
        }
        instr(new SetCond(cond, Reg8.AL));
        instr(new And(new ModRMSIB(Reg64.RAX, true), 0x1));
    }

    public void neg(TypeKind type) {
        if (notFloat(type)) {
            intSignExtend(type, true);
            instr(new Xor(new ModRMSIB(Reg64.RCX, Reg64.RCX)));
            instr(new Sub(new ModRMSIB(Reg64.RCX, Reg64.RAX)));
            instr(new Mov_rmr(new ModRMSIB(Reg64.RAX, Reg64.RCX)));
            intTruncate(type);
        } else {
            fpu.clear();
            fpu.setDblPrecision(type == TypeKind.DOUBLE);
            instr(new Mov_rmi(new ModRMSIB(Reg64.RCX, true), -1));
            fpu.load(false, true);
            fpu.mul();
            fpu.store(false);
        }
    }
}
