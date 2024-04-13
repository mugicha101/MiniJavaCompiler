package miniJava.CodeGeneration.x64.ISA;

import miniJava.CodeGeneration.x64.Instruction;
import miniJava.CodeGeneration.x64.x64;

public class CustomInstruction extends Instruction {
    public CustomInstruction(byte[] opcodeBytes, byte[] immBytes) {
        x64.writeBytes(this.opcodeBytes, opcodeBytes);
        x64.writeBytes(this.immBytes, immBytes);
    }

    public CustomInstruction(byte[] opcodeBytes, long imm64) {
        x64.writeBytes(this.opcodeBytes, opcodeBytes);
        x64.writeLong(this.immBytes, imm64);
    }

    public CustomInstruction(byte[] opcodeBytes, int imm32) {
        x64.writeBytes(this.opcodeBytes, opcodeBytes);
        x64.writeShort(this.immBytes, imm32);
    }

    public CustomInstruction(byte[] opcodeBytes, short imm16) {
        x64.writeBytes(this.opcodeBytes, opcodeBytes);
        x64.writeShort(this.immBytes, imm16);
    }
}
