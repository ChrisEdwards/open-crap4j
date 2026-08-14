package com.architester.crap4j.cli;

import java.nio.file.Path;

/** Executable entry point for the crap4j command. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        int exitCode = new Crap4jCli().run(
                args, System.in, System.out, System.err, Path.of(""));
        System.exit(exitCode);
    }

    static String toolVersion() {
        String version = Main.class.getPackage().getImplementationVersion();
        return version == null ? "development" : version;
    }
}
