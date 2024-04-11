package miniJava.CodeGeneration.x64.ISA;

import miniJava.CodeGeneration.x64.Instruction;
import miniJava.CodeGeneration.x64.ModRMSIB;
import miniJava.CodeGeneration.x64.x64;

public class LoadFlags extends Instruction {
    // load flags into AH
    public LoadFlags() {
        opcodeBytes.write(0x9f);
    }
}
