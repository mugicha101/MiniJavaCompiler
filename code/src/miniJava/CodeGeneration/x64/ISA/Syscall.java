package miniJava.CodeGeneration.x64.ISA;

import miniJava.CodeGeneration.x64.Instruction;

public class Syscall extends Instruction {
	public Syscall() {
		// int $0x30
		opcodeBytes.write(0xcd);
		immBytes.write(0x30);
	}
}
