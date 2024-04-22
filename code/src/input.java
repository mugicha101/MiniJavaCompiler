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
        a = 727;
        NumPrint.printd(a);
        b = 1831;
        NumPrint.printx(b);
        int[] arr = new int[4];
        NumPrint.printd(arr.length);
        NumPrint.printd(10/2);
        NumPrint.printd(-10/2);
        for (int j = -15; j <= 15; j = j + 1) {
            NumPrint.printd(j);
        }
    }
}
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