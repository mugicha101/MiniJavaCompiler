package miniJava;

import miniJava.SyntacticAnalyzer.Parser;
import miniJava.SyntacticAnalyzer.Scanner;

import java.io.*;

public class Compiler {
	// Main function, the file to compile will be an argument.
	public static void main(String[] args) {
		ErrorReporter errors = new ErrorReporter();
		if (args.length == 0) throw new IllegalArgumentException("missing file path");
		InputStream in;
		try {
			File file = new File(args[0]);
			in = new FileInputStream(file);
		} catch (IOException e) {
			throw new RuntimeException("file open error");
		}
		Scanner scanner = new Scanner(in, errors);
		Parser parser = new Parser(scanner, errors);
		parser.parse();
		if (errors.hasErrors()) {
			System.err.println("Error");
			errors.outputErrors();
		} else {
			System.out.println("Success");
		}
		try {
			in.close();
		} catch (IOException e) {
			throw new RuntimeException("file close error");
		}
	}
}
