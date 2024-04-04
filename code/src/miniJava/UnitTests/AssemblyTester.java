package miniJava.UnitTests;

import miniJava.CodeGeneration.x64.ISA.*;
import miniJava.CodeGeneration.x64.*;

import java.math.BigInteger;
import java.util.Arrays;
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
        check(new Syscall(), new byte[]{(byte)0xcd, (byte)0x30});
        System.out.println("syscall passed");
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
