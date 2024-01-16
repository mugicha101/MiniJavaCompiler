package miniJava;

import java.util.List;
import java.util.ArrayList;

public class ErrorReporter {
	private List<String> errorQueue;
	
	public ErrorReporter() {
		this.errorQueue = new ArrayList<String>();
	}
	
	public boolean hasErrors() {
		return !errorQueue.isEmpty();
	}
	
	public void outputErrors() {
		for (String error : errorQueue) {
			System.err.println(error);
		}
	}
	
	public void reportError(int line, int offset, String... messages) {
		StringBuilder sb = new StringBuilder();
		for (String m : messages)
			sb.append(m);
		errorQueue.add(String.format("%d:%d %s", line, offset, sb.toString()));
	}

	public List<String> getErrors() {
		return new ArrayList<>(errorQueue);
	}
}
