package miniJava.UnitTests;

import miniJava.CodeGeneration.ELFMaker;
import miniJava.CodeGeneration.x64.ISA.*;
import miniJava.CodeGeneration.x64.*;
import miniJava.ErrorReporter;

import java.util.Random;

public class AssemblyTester {
    public static void main(String[] args) {
        Reg64[] regs = {
                Reg64.RAX, Reg64.RCX, Reg64.RDX, Reg64.RBX,
                Reg64.RSP, Reg64.RBP, Reg64.RSI, Reg64.RDI,
                Reg64.R8, Reg64.R9, Reg64.R10, Reg64.R11,
                Reg64.R12, Reg64.R13, Reg64.R14, Reg64.R15
        };
        Random rand = new Random();

        // push
        for (int i = 0; i < 1000; ++i) {
            long x = rand.nextLong() & 0xffffffffL;
            check(new Push((int)x), new byte[]{(byte)0x68, (byte)x, (byte)(x >> 8), (byte)(x >> 16), (byte)(x >> 24)});
        }
        System.out.println("push passed");

        // pop
        for (int i = 0; i < 8; ++i) {
            check(new Pop(regs[i]), new byte[]{(byte)(0x58 + i)});
        }
        for (int i = 0; i < 8; ++i) {
            check(new Pop(regs[i+8]), new byte[]{(byte)0x41, (byte)(0x58 + i)});
        }
        System.out.println("pop passed");

        // ret
        check(new Ret(), new byte[]{(byte)0xc3});
        for (int i = 0; i < 1000; ++i) {
            int imm16 = rand.nextInt(1 << 13);
            check(new Ret((short)imm16), new byte[]{(byte)0xc2, (byte)(imm16 << 3), (byte)(imm16 >> 5)});
        }
        for (int i = 0; i < 1000; ++i) {
            int imm32;
            int mult;
            int prod;
            do {
                imm32 = rand.nextInt(1 << 16);
                mult = rand.nextInt(1 << 16);
                prod = imm32 * mult;
            } while (prod >= (1 << 16));
            check(new Ret((short)imm32, (short)mult), new byte[]{(byte)0xc2, (byte)prod, (byte)(prod >> 8)});
        }
        System.out.println("ret passed");

        // mov_ri64
        for (int i = 0; i < 16; ++i) {
            for (int j = 0; j < 200; ++j) {
                long imm64 = rand.nextLong();
                check(new Mov_ri64(regs[i], imm64), new byte[]{
                        (byte)(0x48 + (i >= 8 ? 1 : 0)), (byte)(0xb8 + (i & 0b111)),
                        (byte)imm64, (byte)(imm64 >> 8),
                        (byte)(imm64 >> 16), (byte)(imm64 >> 24),
                        (byte)(imm64 >> 32), (byte)(imm64 >> 40),
                        (byte)(imm64 >> 48), (byte)(imm64 >> 56)
                });
            }
        }
        System.out.println("mov_ri64 passed");

        // syscall
        check(new Syscall(), new byte[]{(byte)0x0f, (byte)0x05});
        System.out.println("syscall passed");

        // modrmsib
        // mov [rbx+0x123],r8
        check(new Mov_rmr(new ModRMSIB(Reg64.RBX,0x123,Reg64.R8)), new byte[]{(byte)0x4c, (byte)0x89, (byte)0x83, (byte)0x23, (byte)0x01, (byte)0x00, (byte)0x00});

        // mov [rcx*4+0x12345678],rbx
        ModRMSIB arg = new ModRMSIB();
        arg.SetRegIdx(Reg64.RCX);
        arg.SetMult(4);
        arg.SetDisp(0x12345678);
        arg.SetRegR(Reg64.RBX);
        check(new Mov_rmr(arg), new byte[]{(byte)0x48, (byte)0x89, (byte)0x1c, (byte)0x8d, (byte)0x78, (byte)0x56, (byte)0x34, (byte)0x12});

        // mov rsi,[rdi+rcx*2+0x13572468]
        check(new Mov_rrm(new ModRMSIB(Reg64.RDI, Reg64.RCX, 2, 0x13572468, Reg64.RSI)), new byte[]{(byte)0x48, (byte)0x8B, (byte)0xB4, (byte)0x4F, (byte)0x68, (byte)0x24, (byte)0x57, (byte)0x13});

        // lea rax,[rsp+4]
        check(new Lea(new ModRMSIB(Reg64.RSP, 4, Reg64.RAX)), new byte[]{(byte)0x48, (byte)0x8d, (byte)0x84, (byte)0x24, (byte)0x04, (byte)0x00, (byte)0x00, (byte)0x00});
        System.out.println("modrmsib passed");

        // cld
        check(new ClearDirFlag(), new byte[]{(byte)0xfc});
        System.out.println("cld passed");

        // rep stosq
        check(new Rep(), new byte[]{(byte)0xf3, (byte)0x48, (byte)0xab});
        System.out.println("rep stosq passed");

        // shift
        for (int i = 0; i < 16; ++i) {
            for (int j = 0; j < 256; ++j) {
                check(new Shift(regs[i], (byte)j, false), new byte[]{(byte)(i < 8 ? 0x48 : 0x49), (byte)0xc1, (byte)(0xe0 + (i & 0x111)), (byte)j});
                check(new Shift(regs[i], (byte)j, true), new byte[]{(byte)(i < 8 ? 0x48 : 0x49), (byte)0xc1, (byte)(0xe8 + (i & 0x111)), (byte)j});
            }
        }
        System.out.println("shift passed");

        // test elf (produce program that does nothing)
        ErrorReporter errors = new ErrorReporter();
        InstructionList asm = new InstructionList();
        asm.add(new Mov_rmi(new ModRMSIB(Reg64.RAX, true), 60));
        asm.add(new Xor(new ModRMSIB(Reg64.RDI, Reg64.RDI)));
        asm.add(new Syscall());
        asm.add(new Ret());
        ELFMaker elf = new ELFMaker(errors, asm.getSize(), 8);
        elf.outputELF("test.out", asm.getBytes(), 0);
        if (errors.hasErrors())
            throw new RuntimeException("elf creation failed");
        System.out.println("blank test.out elf created");
    }

    static String byteString(byte[] bytes) {
        StringBuilder s = new StringBuilder();
        for (byte b : bytes) {
            s.append(String.format("%02x:", b));
        }
        if (s.length() > 0) s.setLength(s.length()-1);
        return s.toString();
    }

    static void check(Instruction instr, byte[] expected) {
        String got = byteString(instr.getBytes());
        String exp = byteString(expected);
        if (!got.equals(exp))
            throw new RuntimeException(String.format("got %s but expected %s\n", got, exp));
    }
}
