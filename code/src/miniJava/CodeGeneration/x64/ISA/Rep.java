package miniJava.CodeGeneration.x64.ISA;

import miniJava.CodeGeneration.x64.Instruction;

public class Rep extends Instruction {
    // rep stosq
    // for rcx repititions stores contents of rax into where rdi points
    // increments/decrements rdi (depends on direction flag) by 8 each iteration
    // similar to memset
    public Rep() {
        opcodeBytes.write(0xf3);
        opcodeBytes.write(0x48);
        opcodeBytes.write(0xab);
    }
}
