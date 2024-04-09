package miniJava.CodeGeneration.x64.ISA;

import miniJava.CodeGeneration.x64.Instruction;

public class Syscall extends Instruction {
	public Syscall() {
		opcodeBytes.write(0x0f);
		immBytes.write(0x05);
	}
}
