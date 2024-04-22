package miniJava.CodeGeneration;

import miniJava.CodeGeneration.x64.ISA.CustomInstruction;
import miniJava.CodeGeneration.x64.Instruction;
import miniJava.CodeGeneration.x64.InstructionList;

// does floating point operations on xmm0 and xmm1
// all operation results stored in xmm0 and clear xmm1 if used
// unary operations performed solely on xmm0
// binary operations performed on both xmm0 and xmm1
// store instructions always store in rax
// load instructions always load rax into xmm0 and rcx into xmm1
public class FPUHandler {
    private boolean dblPrecision = false;
    private boolean xmm0Loaded = true; // assume initially loaded with garbage
    private boolean xmm1Loaded = true; // assume initially loaded with garbage
    public final InstructionList asm;
    public FPUHandler(InstructionList asm) {
        this.asm = asm;
    }

    private int instr(Instruction instr) {
        return asm.add(instr);
    }

    private byte precByte() {
        return (byte)(dblPrecision ? 0xf2 : 0xf3);
    }

    public void clearXmm0() {
        if (xmm0Loaded) {
            // pxor xmm0, xmm0
            instr(new CustomInstruction(new byte[]{(byte) 0x66, (byte) 0x0f, (byte) 0xef, (byte) 0xc0}));
            xmm0Loaded = false;
        }
    }

    public void clearXmm1() {
        if (xmm1Loaded) {
            // pxor xmm1, xmm1
            instr(new CustomInstruction(new byte[]{(byte) 0x66, (byte) 0x0f, (byte) 0xef, (byte) 0xc9}));
            xmm1Loaded = false;
        }
    }

    public void clear() {
        clearXmm0();
        clearXmm1();
    }

    public void setDblPrecision(boolean val) {
        if (dblPrecision == val) return;
        dblPrecision = val;
        // convert between float and double
        if (xmm0Loaded) {
            // cvtss2sd/cvtsd2ss xmm0,xmm0
            instr(new CustomInstruction(new byte[]{precByte(), (byte)0x0f, (byte)0x5a, (byte)0xc0}));
        }
        if (xmm1Loaded) {
            // cvtss2sd/cvtsd2ss xmm1,xmm1
            instr(new CustomInstruction(new byte[]{precByte(), (byte)0x0f, (byte)0x5a, (byte)0xc9}));
        }
        dblPrecision = true;
    }

    // load value in RAX to xmm0
    // if both, load value in RCX to xmm1
    // if isInt, convert int to float
    public void load(boolean isInt, boolean both) {
        if (isInt) {
            // cvtsi2ss/cvtsi2sd xmm0,rax
            instr(new CustomInstruction(new byte[]{precByte(), (byte)0x48, (byte)0x0f, (byte)0x2a, (byte)0xc0}));
        } else {
            // movq xmm0,rax
            instr(new CustomInstruction(new byte[]{(byte)0x66, (byte)0x48, (byte)0x0f, (byte)0x6e, (byte)0xc0}));
        }
        xmm0Loaded = true;
        if (!both) return;
        if (isInt) {
            // cvtsi2ss/cvtsi2sd xmm1,rcx
            instr(new CustomInstruction(new byte[]{precByte(), (byte)0x48, (byte)0x0f, (byte)0x2a, (byte)0xc9}));
        } else {
            // movq xmm1,rcx
            instr(new CustomInstruction(new byte[]{(byte)0x66, (byte)0x48, (byte)0x0f, (byte)0x6e, (byte)0xc9}));
        }
        xmm1Loaded = true;
    }

    // if xmm0Loaded, store xmm0 in rax
    // if xmm1Loaded, store xmm1 in rcx
    // if toInt, truncate to int when storing
    public void store(boolean toInt) {
        if (xmm0Loaded) {
            if (toInt) {
                // cvttss2si/cvttsd2si rax,xmm0
                instr(new CustomInstruction(new byte[]{precByte(), (byte)0x48, (byte)0x0f, (byte)0x2c, (byte)0xc0}));
            } else {
                // movq rax,xmm0
                instr(new CustomInstruction(new byte[]{(byte)0x66, (byte)0x48, (byte)0x0f, (byte)0x7e, (byte)0xc0}));
            }
        }
        if (xmm1Loaded) {
            if (toInt) {
                // cvttss2si/cvttsd2si rcx,xmm1
                instr(new CustomInstruction(new byte[]{precByte(), (byte)0x48, (byte)0x0f, (byte)0x2c, (byte)0xc9}));
            } else {
                // movq rcx,xmm1
                instr(new CustomInstruction(new byte[]{(byte)0x66, (byte)0x48, (byte)0x0f, (byte)0x7e, (byte)0xc9}));
            }
        }
        clear();
    }

    private void bothLoadedCheck() {
        if (!xmm0Loaded) throw new RuntimeException("expected xmm0 to be loaded");
        if (!xmm1Loaded) throw new RuntimeException("expected xmm1 to be loaded");
    }

    private void op(byte opcode) {
        bothLoadedCheck();
        // opss/opsd xmm0,xmm1
        instr(new CustomInstruction(new byte[]{precByte(), (byte)0x0f, opcode, (byte)0xc1}));
        clearXmm1();
    }

    public void add() {
        op((byte)0x58);
    }

    public void sub() {
        op((byte)0x5c);
    }

    public void mul() {
        op((byte)0x59);
    }

    public void div() {
        op((byte)0x5e);
    }

    // special case: clears neither
    public void cmp() {
        bothLoadedCheck();
        // ucomiss/ucomisd xmm0,xmm1
        if (dblPrecision)
            instr(new CustomInstruction(new byte[]{(byte)0x66, (byte)0x0f, (byte)0x2e, (byte)0xc1}));
        else
            instr(new CustomInstruction(new byte[]{(byte)0x0f, (byte)0x2e, (byte)0xc1}));
    }
}
