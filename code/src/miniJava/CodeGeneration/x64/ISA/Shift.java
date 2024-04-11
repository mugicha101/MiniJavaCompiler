package miniJava.CodeGeneration.x64.ISA;

import miniJava.CodeGeneration.x64.Instruction;
import miniJava.CodeGeneration.x64.ModRMSIB;
import miniJava.CodeGeneration.x64.Reg64;
import miniJava.CodeGeneration.x64.x64;

public class Shift extends Instruction {
    // shl/shr reg,imm8
    public Shift(Reg64 reg, byte imm8, boolean right) {
        opcodeBytes.write(reg.getIdx() < 8 ? 0x48 : 0x49);
        opcodeBytes.write(0xc1);
        opcodeBytes.write((right ? 0xe8 : 0xe0) + (reg.getIdx() & 0x111));
        immBytes.write(imm8);
    }
}
