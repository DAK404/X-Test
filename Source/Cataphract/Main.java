/*
 * ================================================================
 * Cataphract / NION Program Launcher
 * ================================================================
 *
 * A launcher for kernels conforming to the NION Directory and
 * Kernel specifications.
 *
 * This class is intentionally kept independent from the kernel.
 *
 * The launcher:
 *
 *  1. Parses launcher arguments.
 *  2. Resolves the requested kernel.
 *  3. Starts the kernel in a separate JVM.
 *  4. Inherits the current console I/O.
 *  5. Waits for the kernel to terminate.
 *  6. Interprets the kernel exit code.
 *  7. Restarts the kernel when requested.
 *
 * ================================================================
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Main
{
    /*
     * ============================================================
     * VERSION INFORMATION
     * ============================================================
     */

    /*
     * [ CHANGE NEEDED ]
     *
     * Replace this with the actual launcher version for the
     * rewritten Cataphract implementation.
     */
    private static final String VERSION = "8.0.0.1";


    /*
     * [ CHANGE NEEDED ]
     *
     * Replace this with the actual documentation URL/version
     * location when the rewritten documentation is available.
     */
    private static final String DOCUMENTATION_URL =
            "https://dak404.github.io/Cataphract/Docs";


    /*
     * ============================================================
     * KERNEL EXIT CODES
     * ============================================================
     *
     * These values are carried over from the existing launcher.
     *
     * They should eventually be defined by the NION Kernel
     * Specification rather than being duplicated here.
     */

    private static final int EXIT_NORMAL = 0;

    private static final int EXIT_LEGACY_RESTART = 100;

    private static final int EXIT_RESTART = 211;

    private static final int EXIT_REPAIR = 212;

    private static final int EXIT_INVALID_BOOT_MODE = 3;

    private static final int EXIT_FATAL = 4;

    private static final int EXIT_FATAL_RESTART = 5;

    private static final int EXIT_UPDATE_RESTART = 6;

    private static final int EXIT_PROBE_SUCCESS = 7;

    private static final int EXIT_KERNEL_NOT_FOUND = 1024;


    /*
     * ============================================================
     * MESSAGE STRINGS
     * ============================================================
     */

    private static final String HEADER = """
            *********************************
            NION PROGRAM LOADER
            *********************************
            Version : %s

            """.formatted(VERSION);


    private static final String FOOTER = """
            *********************************
            """;


    private static final String HELP = """
            This program launches kernels that follow the
            NION Directory and Kernel specifications.

            Usage:

                java Main <Kernel_Name> <Boot_Mode>

            Probe a kernel:

                java Main <Kernel_Name> probe

            Help:

                java Main help
                java Main --help
                java Main -h

            Documentation:

                %s
            """.formatted(DOCUMENTATION_URL);


    private static final String INVALID_SYNTAX = """
            [ ERROR ] : INVALID LAUNCHER SYNTAX.

            Usage:

                java Main <Kernel_Name> <Boot_Mode>

            Use:

                java Main help

            for additional information.
            """;


    private static final String KERNEL_NOT_FOUND = """
            [ ERROR ] : KERNEL NOT FOUND.

            The specified kernel could not be found or could not
            be resolved as a valid NION kernel.
            """;


    private static final String KERNEL_INVALID = """
            [ ERROR ] : INVALID KERNEL.

            The specified kernel exists, but does not appear to
            satisfy the expected NION kernel directory structure.
            """;


    private static final String UNDEFINED_BOOTMODE = """
            [ ERROR ] : UNDEFINED BOOT MODE.

            The kernel returned an exit code indicating that the
            requested boot mode is not supported.
            """;


    private static final String FATAL_ERROR_EXIT = """
            [ CRITICAL ] : FATAL ERROR EXIT.

            The kernel exited fatally.
            """;


    private static final String FATAL_ERROR_RESTART = """
            [ CRITICAL ] : FATAL ERROR RESTART.

            The kernel exited fatally and requested a restart.
            """;


    private static final String RESTART_UPDATE = """
            [ INFORMATION ] : SYSTEM UPDATE.

            The kernel requested a restart following an update.
            """;


    private static final String UNDEFINED_EXIT_CODE = """
            [ WARNING ] : UNDEFINED EXIT CODE.

            The kernel exited with a code that this launcher
            does not recognize.
            """;


    /*
     * ============================================================
     * ENTRY POINT
     * ============================================================
     */

    public static void main(String[] args)
    {
        int exitCode;

        try
        {
            exitCode = run(args);
        }
        catch (Exception exception)
        {
            /*
             * This is the final launcher-level safety boundary.
             *
             * An unexpected launcher failure should not result in
             * an uncontrolled Java stack trace being the only
             * indication of what happened.
             */

            System.err.println(
                    "[ CRITICAL ] : LAUNCHER FAILURE."
            );

            exception.printStackTrace(System.err);

            /*
             * [ CHANGE NEEDED ]
             *
             * Define a dedicated launcher exit code in the
             * NION specification for launcher-level failures.
             */
            exitCode = 255;
        }

        System.exit(exitCode);
    }


    /*
     * ============================================================
     * LAUNCHER
     * ============================================================
     */

    private static int run(String[] args)
            throws Exception
    {
        /*
         * Validate arguments before accessing args[0].
         *
         * This specifically prevents the class of bug present
         * in the previous Main.java.
         */

        if (args == null || args.length == 0)
        {
            display(INVALID_SYNTAX);
            return 1;
        }


        /*
         * Launcher help is independent of kernel arguments.
         */

        if (isHelpArgument(args[0]))
        {
            display(HELP);
            return EXIT_NORMAL;
        }


        /*
         * A kernel name and boot mode are currently required by
         * the existing launcher contract.
         */

        if (args.length < 2)
        {
            display(INVALID_SYNTAX);
            return 1;
        }


        LaunchRequest request = LaunchRequest.from(args);


        /*
         * Kernel supervision loop.
         *
         * The kernel may request that it be restarted with a
         * different boot mode.
         */

        while (true)
        {
            int kernelExitCode = launchKernel(request);

            LaunchDecision decision =
                    processKernelExit(kernelExitCode, request);


            if (decision.exitLauncher())
            {
                return decision.exitCode();
            }


            request = decision.nextRequest();
        }
    }


    /*
     * ============================================================
     * ARGUMENT HANDLING
     * ============================================================
     */

    private static boolean isHelpArgument(String argument)
    {
        return argument.equalsIgnoreCase("help")
                || argument.equalsIgnoreCase("--help")
                || argument.equalsIgnoreCase("-h");
    }


    /*
     * ============================================================
     * KERNEL LAUNCH
     * ============================================================
     */

    private static int launchKernel(LaunchRequest request)
            throws IOException, InterruptedException
    {
        Path kernelPath = resolveKernel(request.kernelName());


        if (!Files.exists(kernelPath))
        {
            display(KERNEL_NOT_FOUND);
            return EXIT_KERNEL_NOT_FOUND;
        }


        if (!Files.isDirectory(kernelPath))
        {
            display(KERNEL_INVALID);
            return EXIT_KERNEL_NOT_FOUND;
        }


        /*
         * Construct the kernel class name.
         *
         * This preserves the architecture used by the existing
         * Cataphract implementation:
         *
         *     <Kernel>.Core.Loader
         */
        String loaderClass =
                request.kernelName() + ".Core.Loader";


        /*
         * Determine the JVM used to launch the child kernel.
         *
         * Using the current JVM installation is preferable to
         * assuming that "java" is available through PATH.
         */

        String javaExecutable =
                getJavaExecutable();


        /*
         * Build the child JVM command.
         */

        List<String> command = new ArrayList<>();

        command.add(javaExecutable);

        /*
         * [ CHANGE NEEDED ]
         *
         * Add JVM options here if Cataphract requires a specific
         * heap size, module configuration, encoding, assertions,
         * security properties, etc.
         */


        /*
         * The launcher is currently expected to be executed from
         * the directory containing the kernel packages.
         *
         * Therefore "." remains the classpath.
         *
         * [ CHANGE NEEDED ]
         *
         * Replace this with the definitive NION classpath/module
         * construction once the rewritten directory specification
         * is finalized.
         */
        command.add("-cp");
        command.add(".");


        command.add(loaderClass);


        /*
         * Pass the boot mode and any additional kernel arguments.
         *
         * The kernel name itself is consumed by the launcher and
         * is NOT passed to Loader.
         */

        command.addAll(request.kernelArguments());


        ProcessBuilder processBuilder =
                new ProcessBuilder(command);


        /*
         * Preserve the existing Cataphract behaviour:
         *
         * stdin
         * stdout
         * stderr
         *
         * all belong to the child kernel.
         */

        processBuilder.inheritIO();


        /*
         * Establish the kernel's working directory.
         *
         * [ CHANGE NEEDED ]
         *
         * Confirm whether NION specifies that the kernel should
         * execute from:
         *
         *     A) launcher working directory
         *     B) kernel directory
         *     C) NION system root
         *
         * The current implementation uses the launcher working
         * directory, so this implementation preserves that
         * behaviour by default.
         */


        Process kernelProcess =
                processBuilder.start();


        try
        {
            return kernelProcess.waitFor();
        }
        catch (InterruptedException exception)
        {
            /*
             * Restore the interrupted status before propagating.
             */

            Thread.currentThread().interrupt();

            /*
             * Ask the kernel process to terminate.
             *
             * [ CHANGE NEEDED ]
             *
             * If NION defines a formal shutdown protocol, replace
             * this with that protocol.
             */

            kernelProcess.destroy();

            throw exception;
        }
    }


    /*
     * ============================================================
     * KERNEL RESOLUTION
     * ============================================================
     */

    private static Path resolveKernel(String kernelName)
    {
        /*
         * The old launcher treated the first argument as the
         * kernel directory/package name.
         */

        return Path.of(kernelName);
    }


    /*
     * ============================================================
     * JAVA EXECUTABLE
     * ============================================================
     */

    private static String getJavaExecutable()
    {
        String javaHome =
                System.getProperty("java.home");


        String executableName =
                isWindows()
                        ? "java.exe"
                        : "java";


        return Path.of(
                javaHome,
                "bin",
                executableName
        ).toString();
    }


    private static boolean isWindows()
    {
        return System.getProperty("os.name")
                .toLowerCase()
                .contains("win");
    }


    /*
     * ============================================================
     * EXIT CODE PROCESSING
     * ============================================================
     */

    private static LaunchDecision processKernelExit(
            int exitCode,
            LaunchRequest request)
    {
        switch (exitCode)
        {
            /*
             * ----------------------------------------------------
             * Normal exit
             * ----------------------------------------------------
             */

            case EXIT_NORMAL:
                return LaunchDecision.exit(EXIT_NORMAL);


            /*
             * ----------------------------------------------------
             * Legacy restart
             * ----------------------------------------------------
             */

            case EXIT_LEGACY_RESTART:

                /*
                 * Preserve legacy behaviour from the old launcher.
                 *
                 * The old implementation allowed exit code 100
                 * to fall through into the repair case.
                 *
                 * [ CHANGE NEEDED ]
                 *
                 * Confirm whether this is an intentional legacy
                 * compatibility behaviour or an old bug.
                 */

                return LaunchDecision.restart(
                        request.withBootMode("repair")
                );


            /*
             * ----------------------------------------------------
             * Normal restart
             * ----------------------------------------------------
             */

            case EXIT_RESTART:

                return LaunchDecision.restart(
                        request.withBootMode("normal")
                );


            /*
             * ----------------------------------------------------
             * Repair
             * ----------------------------------------------------
             */

            case EXIT_REPAIR:

                return LaunchDecision.restart(
                        request.withBootMode("repair")
                );


            /*
             * ----------------------------------------------------
             * Invalid boot mode
             * ----------------------------------------------------
             */

            case EXIT_INVALID_BOOT_MODE:

                display(UNDEFINED_BOOTMODE);

                return LaunchDecision.exit(
                        EXIT_INVALID_BOOT_MODE
                );


            /*
             * ----------------------------------------------------
             * Fatal exit
             * ----------------------------------------------------
             */

            case EXIT_FATAL:

                display(FATAL_ERROR_EXIT);

                return LaunchDecision.exit(EXIT_FATAL);


            /*
             * ----------------------------------------------------
             * Fatal restart
             * ----------------------------------------------------
             */

            case EXIT_FATAL_RESTART:

                display(FATAL_ERROR_RESTART);

                return LaunchDecision.restart(
                        request.withBootMode(
                                request.bootMode()
                        )
                );


            /*
             * ----------------------------------------------------
             * Update restart
             * ----------------------------------------------------
             */

            case EXIT_UPDATE_RESTART:

                display(RESTART_UPDATE);

                return LaunchDecision.exit(
                        EXIT_UPDATE_RESTART
                );


            /*
             * ----------------------------------------------------
             * Probe success
             * ----------------------------------------------------
             */

            case EXIT_PROBE_SUCCESS:

                display("""
                        [ INFORMATION ] : KERNEL FOUND

                        The kernel responded successfully to the
                        requested probe operation.
                        """);

                return LaunchDecision.exit(EXIT_NORMAL);


            /*
             * ----------------------------------------------------
             * Kernel not found
             * ----------------------------------------------------
             */

            case EXIT_KERNEL_NOT_FOUND:

                display(KERNEL_NOT_FOUND);

                return LaunchDecision.exit(
                        EXIT_KERNEL_NOT_FOUND
                );


            /*
             * ----------------------------------------------------
             * Unknown exit code
             * ----------------------------------------------------
             */

            default:

                System.err.println(
                        UNDEFINED_EXIT_CODE
                );

                System.err.println(
                        "Kernel exit code: " + exitCode
                );

                return LaunchDecision.exit(exitCode);
        }
    }


    /*
     * ============================================================
     * OUTPUT
     * ============================================================
     */

    private static void display(String message)
    {
        System.out.println(
                HEADER
                        + message
                        + "\n"
                        + FOOTER
        );
    }


    /*
     * ============================================================
     * LAUNCH REQUEST
     * ============================================================
     *
     * Immutable representation of a request to launch a kernel.
     */

    private record LaunchRequest(
            String kernelName,
            String bootMode,
            List<String> additionalArguments)
    {
        private static LaunchRequest from(String[] args)
        {
            String kernelName = args[0];
            String bootMode = args[1];


            List<String> additionalArguments =
                    new ArrayList<>();


            /*
             * Preserve arguments after the boot mode.
             */

            for (int i = 2; i < args.length; i++)
            {
                additionalArguments.add(args[i]);
            }


            return new LaunchRequest(
                    kernelName,
                    bootMode,
                    List.copyOf(additionalArguments)
            );
        }


        private List<String> kernelArguments()
        {
            List<String> arguments =
                    new ArrayList<>();


            arguments.add(bootMode);

            arguments.addAll(additionalArguments);

            return List.copyOf(arguments);
        }


        private LaunchRequest withBootMode(String newBootMode)
        {
            return new LaunchRequest(
                    kernelName,
                    newBootMode,
                    additionalArguments
            );
        }
    }


    /*
     * ============================================================
     * LAUNCH DECISION
     * ============================================================
     */

    private record LaunchDecision(
            boolean exitLauncher,
            int exitCode,
            LaunchRequest nextRequest)
    {
        private static LaunchDecision exit(int exitCode)
        {
            return new LaunchDecision(
                    true,
                    exitCode,
                    null
            );
        }


        private static LaunchDecision restart(
                LaunchRequest request)
        {
            return new LaunchDecision(
                    false,
                    0,
                    request
            );
        }
    }
}
