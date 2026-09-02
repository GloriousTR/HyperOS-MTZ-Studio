package dev.glorioustr.mtzstudio.tester;

public final class OutputProcessFixture {
    public static void main(String[] args) throws Exception {
        if ("timeout".equals(args[0])) {
            System.out.print("partial output");
            System.out.flush();
            Thread.sleep(20_000);
            return;
        }
        for (int i = 0; i < 1024; i++) System.out.print("x".repeat(1024));
        System.err.print("final diagnostic");
        System.exit(7);
    }
}
