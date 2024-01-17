package miniJava.UnitTests;

import miniJava.ErrorReporter;
import miniJava.SyntacticAnalyzer.Parser;
import miniJava.SyntacticAnalyzer.Scanner;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class UnitTester {
    public static class Test {
        String inputPath;
        String expectedPath;
    }

    public static void main(String[] args) {
        if (args.length == 0) throw new IllegalArgumentException("missing test folder path");
        File testFolder = new File(args[0] + "/tests");
        if (!testFolder.isDirectory())
            throw new RuntimeException(String.format("invalid test folder path %s", args[0]));
        HashMap<String, Test> tests = new HashMap<>();
        for (File file : testFolder.listFiles()) {
            System.out.println(file.getAbsolutePath());
            String name = file.getName();
            int i = name.lastIndexOf('_');
            if (i == -1) throw new RuntimeException(String.format("invalid test file %s", name));
            String suffix = name.substring(i+1);
            String prefix = name.substring(0, i);
            if (!tests.containsKey(prefix)) tests.put(prefix, new Test());
            if (suffix.equals("input.txt"))
                tests.get(prefix).inputPath = file.getAbsolutePath();
            else if (suffix.equals("expected.txt"))
                tests.get(prefix).expectedPath = file.getAbsolutePath();
            else throw new RuntimeException(String.format("invalid test file %s", name));
        }

        for (Map.Entry<String, Test> entry : tests.entrySet()) {
            Test test = entry.getValue();
            try {
                File file = new File(test.inputPath);
                InputStream in = new FileInputStream(file);
                ErrorReporter errors = new ErrorReporter();
                Scanner scanner = new Scanner(in, errors);
                Parser parser = new Parser(scanner, errors);
                parser.enableUnitTest();
                parser.parse();
                String output = trimString(parser.getTestOutput());
                file = new File(test.expectedPath);
                in.close();
                in = new FileInputStream(file);
                StringBuilder expectedSb = new StringBuilder();
                while (in.available() != 0) {
                    expectedSb.append((char)in.read());
                }
                String expected = trimString(expectedSb.toString());
                if (output.equals(expected)) {
                    System.out.println(String.format("test %s passed", entry.getKey()));
                } else {
                    System.err.println(String.format("test %s failed", entry.getKey()));
                    File errFile = new File(args[0] + "/failed_test_outputs/" + entry.getKey() + ".txt");
                    errFile.createNewFile();
                    OutputStream out = new FileOutputStream(errFile);
                    out.write(output.getBytes());
                }
            } catch (Exception e) {
                throw new RuntimeException(String.format("test exception %s %s", entry.getKey(), e.getMessage()));
            }
        }
    }

    private static String trimString(String string) {
        return string.replaceAll("\r", "");
    }
}
