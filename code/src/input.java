class MainClass {
    public static void pass(boolean cond) {
        if (cond) {
            System.out.println('p');
            System.out.println('a');
            System.out.println('s');
            System.out.println('s');
            System.out.println('\n');
        } else {
            System.out.println('f');
            System.out.println('a');
            System.out.println('i');
            System.out.println('l');
            System.out.println('\n');
        }
    }
    public static void main(String[] args) {
        A aa = new A();
        A ab = new B(); // implicit type cast
        pass(ab instanceof A); // is true
        pass(ab instanceof B); // is true
        pass(!(aa instanceof B)); // is false
        B bb = (B)ab; // explicit type cast
        aa.foo(); // prints A - normal method
        ab.foo(); // prints B - virtual method
        bb.foo(); // prints B - overrider method
        aa.a(); // prints a - normal method
        bb.a(); // prints a - inherited method
        A.x = 1;
        NumPrint.printd(B.x); // prints 1 - inherited static field access
        A.st(); // prints S1 - normal static method
        B.st(); // prints S2 - hiding static method
        A.tt(); // prints T - normal static method
        B.tt(); // prints T - inherited static method
    }
}

class A {
    public char val;
    public static int x;
    public static void st() {
        System.out.println('S');
        System.out.println('1');
        System.out.println('\n');
    }


    public static void tt() {
        System.out.println('T');
        System.out.println('\n');
    }

    public void foo() {
        System.out.println('A');
        System.out.println('\n');
    }

    public void a() {
        System.out.println('a');
        System.out.println('\n');
    }
}

class B extends A {
    public static int y;
    public static void st() {
        System.out.println('S');
        System.out.println('2');
        System.out.println('\n');
    }
    public void foo() {
        System.out.println('B');
        System.out.println('\n');
    }

    public void b() {
        System.out.println('b');
        System.out.println('\n');
    }
}

/*
class MainClass {
    static int a;
    static int b;
    private static int returnCast() {
        return 'a';
    }
    public static void main(String[] args) {
        MainClass foo = new MainClass();
        NumPrint.printd(2147483647 + 2147483647);
        if (foo == foo) {
            NumPrint.printd(1000);
        }
        if (2.0 * 5.0 > (double)2.f) {
            NumPrint.printd((int)(1234.5 + 8.0));
        }
        NumPrint.printd(returnCast());
        boolean valBool = true;
        int valInt = 1;
        long valLong = 1l + 2l;
        float valFloat = 1.5f;
        valFloat = valFloat + (float)valInt;
        double valDouble = 1d;
        int castTest = (int)valLong;
        NumPrint.printd(castTest);
        NumPrint.printd((int)valFloat);
        NumPrint.printd((int)(valDouble + 5.));
        char valChar = 'a';
        System.out.println(valChar);
        System.out.println('\n');
        a = 727;
        NumPrint.printd(a);
        b = 1831;
        NumPrint.printx(b);
        int[] arr = new int[4];
        NumPrint.printd(arr.length);
        NumPrint.printd(10/2);
        NumPrint.printd(-10/2);
        for (int j = -5; j <= 5; j = j + 1) {
            NumPrint.printd(j);
        }

        overloaded(0);
        overloaded(0.f);
        amb(0L, 0);
    }

    public static void overloaded(int x) {
        System.out.println('o');
        System.out.println('d');
        System.out.println('\n');
    }

    public static void overloaded(float x) {
        System.out.println('o');
        System.out.println('f');
        System.out.println('\n');
    }

    public static void amb(int x, long y) {
        System.out.println('A');
        System.out.println('1');
        System.out.println('\n');
    }

    public static void amb(long x, int y) {
        System.out.println('A');
        System.out.println('2');
        System.out.println('\n');
    }
}
 */

/*
class Foo {
    int x;
    int y;
}
*/
class NumPrint {
    private static void print(long x, long mod) {
        if (x == 0) {
            System.out.println('0');
            System.out.println('\n');
            return;
        }
        if (x < 0) {
            x = -x;
            System.out.println('-');
        }
        int digs = 0;
        long t = x;
        long m = 1;
        while (t > 0) {
            digs = digs + 1;
            t = t / mod;
            m = m * mod;
        }
        char[] arr = new char[digs];
        long s = 0;
        for (int i = 0; i < digs; i = i + 1) {
            m = m / mod;
            arr[i] = (char)(x / m - s);
            if (arr[i] >= 10) {
                System.out.println(arr[i]-(char)10+'a');
            } else {
                System.out.println(arr[i]+'0');
            }
            s = s + arr[i];
            s = s * mod;
        }
        System.out.println('\n');
    }

    public static void printd(long x) {
        print(x, 10);
    }

    public static void printx(long x) {
        System.out.println('0');
        System.out.println('x');
        print(x, 16);
    }
}
