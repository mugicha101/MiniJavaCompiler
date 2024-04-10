package miniJava.CodeGeneration.x64.ISA;

import miniJava.CodeGeneration.x64.Instruction;

public class ClearDirFlag extends Instruction {
    public ClearDirFlag() {
        opcodeBytes.write(0xfc);
    }
}
