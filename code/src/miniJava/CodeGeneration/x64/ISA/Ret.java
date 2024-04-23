package miniJava.CodeGeneration.x64.ISA;

import miniJava.CodeGeneration.x64.Instruction;
import miniJava.CodeGeneration.x64.x64;

public class Ret extends Instruction {
	public Ret() {
		// ret
		opcodeBytes.write(0xc3);
	}
	
	public Ret(short imm16, short mult) {
		// ret imm16*mult
		opcodeBytes.write(0xc2);
		x64.writeShort(immBytes,imm16*mult);
	}
	
	public Ret(short imm16) {
		// ret imm16*8
		this(imm16,(short)8);
	}
}
