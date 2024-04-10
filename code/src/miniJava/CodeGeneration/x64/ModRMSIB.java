package miniJava.CodeGeneration.x64;

import java.io.ByteArrayOutputStream;

public class ModRMSIB {
	private ByteArrayOutputStream _b;
	private boolean rexW = false;
	private boolean rexR = false;
	private boolean rexX = false;
	private boolean rexB = false;
	
	public boolean getRexW() {
		return rexW;
	}
	
	public boolean getRexR() {
		return rexR;
	}
	
	public boolean getRexX() {
		return rexX;
	}
	
	public boolean getRexB() {
		return rexB;
	}
	
	public byte[] getBytes() {
		_b = new ByteArrayOutputStream();
		// construct
		if( rdisp != null && ridx != null && r != null )
			Make(rdisp,ridx,mult,disp,r);
		else if( ridx != null && r != null )
			Make(ridx,mult,disp,r);
		else if( rdisp != null && r != null )
			Make(rdisp,disp,r);
		else if( rm != null && r != null )
			Make(rm,r);
		else if( r != null )
			Make(disp,r);
		else throw new IllegalArgumentException("Cannot determine ModRMSIB");
		
		return _b.toByteArray();
	}
	
	private Reg64 rdisp = null, ridx = null;
	private Reg rm = null, r = null;
	private int disp = 0, mult = 0;
	
	// [rdisp+ridx*mult+disp],r32/64
	public ModRMSIB(Reg64 rdisp, Reg64 ridx, int mult, int disp, Reg r) {
		SetRegR(r);
		SetRegDisp(rdisp);
		SetRegIdx(ridx);
		SetDisp(disp);
		SetMult(mult);
	}
	
	// r must be set by some mod543 instruction set later
	// [rdisp+ridx*mult+disp]
	public ModRMSIB(Reg64 rdisp, Reg64 ridx, int mult, int disp) {
		SetRegDisp(rdisp);
		SetRegIdx(ridx);
		SetDisp(disp);
		SetMult(mult);
	}
	
	// [rdisp+disp],r
	public ModRMSIB(Reg64 rdisp, int disp, Reg r) {
		SetRegDisp(rdisp);
		SetRegR(r);
		SetDisp(disp);
	}
	
	// r will be set by some instruction to a mod543
	// [rdisp+disp]
	public ModRMSIB(Reg64 rdisp, int disp) {
		SetRegDisp(rdisp);
		SetDisp(disp);
	}
	
	// rm64,r64
	public ModRMSIB(Reg64 rm, Reg r) {
		SetRegRM(rm);
		SetRegR(r);
	}
	
	// rm or r
	public ModRMSIB(Reg64 r_or_rm, boolean isRm) {
		if( isRm )
			SetRegRM(r_or_rm);
		else
			SetRegR(r_or_rm);
	}
	
	public int getRMSize() {
		if( rm == null ) return 0;
		return rm.size();
	}

	public ModRMSIB() {}
	
	public void SetRegRM(Reg rm) {
		if( rm.getIdx() > 7 ) rexB = true;
		rexW = rexW || rm instanceof Reg64;
		this.rm = rm;
	}
	
	public void SetRegR(Reg r) {
		if( r.getIdx() > 7 ) rexR = true;
		rexW = rexW || r instanceof Reg64;
		this.r = r;
	}
	
	public void SetRegDisp(Reg64 rdisp) {
		if( rdisp.getIdx() > 7 ) rexB = true;
		this.rdisp = rdisp;
	}
	
	public void SetRegIdx(Reg64 ridx) {
		if( ridx.getIdx() > 7 ) rexX = true;
		this.ridx = ridx;
	}
	
	public void SetDisp(int disp) {
		this.disp = disp;
	}
	
	public void SetMult(int mult) {
		this.mult = mult;
	}
	
	public boolean IsRegR_R8() {
		return r instanceof Reg8;
	}
	
	public boolean IsRegR_R64() {
		return r instanceof Reg64;
	}
	
	public boolean IsRegRM_R8() {
		return rm instanceof Reg8;
	}
	
	public boolean IsRegRM_R64() {
		return rm instanceof Reg64;
	}

	private byte constructModRMByte(int mod, Reg rReg, Reg rmReg) {
		if (mod < 0 || mod > 3)
			throw new IllegalArgumentException("Invalid mod value: " + mod);
		// following data from http://www.c-jump.com/CIS77/CPU/x86/X77_0060_mod_reg_r_m_byte.htm
		// mod 0x00: reg indirect addressing mode
		//           or SIB with no displacement (when RM == 100)
		//           or displacement only addressing mode (when RM == 101)
		// mod 0x01: 1 byte signed displacement 	[base+(index*s)+disp8]
		// mod 0x10: 4 byte signed displacement 	[base+(index*s)+disp32]
		// mod 0x11: register addressing mode   	[base+(index*s)]
		int reg = getIdx(rReg);
		int rm = getIdx(rmReg);
		return (byte)((mod << 6) | (reg << 3) | rm);
	}
	// construct ModRM byte with SIB byte enabled
	private byte constructModRMByte(int mod, Reg rReg) {
		return constructModRMByte(mod, rReg, Reg64.RSP);
	}
	private byte constructSIBByte(Reg baseReg, Reg indexReg, int mult) {
		// NOTE: SIB used only when ModRM.RM == 4
		int scale = mult == 1 ? 0 : mult == 2 ? 1 : mult == 4 ? 2 : mult == 8 ? 3 : -1;
		if (scale == -1)
			throw new IllegalArgumentException("Invalid multiplier value: " + mult);
		int index = getIdx(indexReg);
		int base = getIdx(baseReg);
		return (byte)((scale << 6) | (index << 3) | base);
	}
	
	// rm,r
	private void Make(Reg rm, Reg r) {
		// int mod = 3;
		// int regByte = ( mod << 6 ) | ( getIdx(r) << 3 ) | getIdx(rm);
		_b.write(constructModRMByte(3, r, rm));
	}
	
	// [rdisp+disp],r
	private void Make(Reg64 rdisp, int disp, Reg r) {
		// TODO: construct the byte and write to _b
		// Operands: [rdisp+disp],r
		_b.write(constructModRMByte(2, r, rdisp));
		if (rdisp == Reg64.RSP) {
			// 0010 1100
			// 0010 0100
			_b.write(constructSIBByte(rdisp, rdisp, 1));
		}
		x64.writeInt(_b, disp);
	}
	
	// [ridx*mult+disp],r
	private void Make( Reg64 ridx, int mult, int disp, Reg r ) {
		if( !(mult == 1 || mult == 2 || mult == 4 || mult == 8) )
			throw new IllegalArgumentException("Invalid multiplier value: " + mult);
		if( ridx == Reg64.RSP )
			throw new IllegalArgumentException("Index cannot be rsp");
		
		// TODO: construct the modrm byte and SIB byte
		// Operands: [ridx*mult + disp], r
		_b.write(constructModRMByte(0, r));
		_b.write(constructSIBByte(Reg64.RBP, ridx, mult));
		x64.writeInt(_b, disp);
	}
	
	// [rdisp+ridx*mult+disp],r
	private void Make( Reg64 rdisp, Reg64 ridx, int mult, int disp, Reg r ) {
		if( !(mult == 1 || mult == 2 || mult == 4 || mult == 8) )
			throw new IllegalArgumentException("Invalid multiplier value: " + mult);
		if( ridx == Reg64.RSP )
			throw new IllegalArgumentException("Index cannot be rsp");
		
		// TODO: construct the modrm byte and SIB byte
		// Operands: [rdisp + ridx*mult + disp], r
		_b.write(constructModRMByte(2, r));
		_b.write(constructSIBByte(rdisp, ridx, mult));
		x64.writeInt(_b, disp);
	}
	
	// [disp],r
	private void Make( int disp, Reg r ) {
		_b.write( ( getIdx(r) << 3 ) | 4 );
		_b.write( ( 4 << 3 ) | 5 ); // ss doesn't matter
		x64.writeInt(_b,disp);
	}
	
	private int getIdx(Reg r) {
		return x64.getIdx(r);
	}
}
