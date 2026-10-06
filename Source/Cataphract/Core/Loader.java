package Cataphract.Core;

public final class Loader
{
    private Loader()
    {
    }

    public static void main(String[] args)
    {
        if (args == null || args.length == 0)
        {
            System.out.println("[ TEST KERNEL ] No boot mode supplied.");
            System.exit(3);
        }

        String bootMode = args[0];

        switch (bootMode.toLowerCase())
        {
            case "probe":
                probe();
                break;

            case "normal":
                bootNormal();
                break;

            case "repair":
                bootRepair();
                break;

            case "restart":
                System.out.println(
                        "[ TEST KERNEL ] Requesting normal restart."
                );

                System.exit(211);
                break;

            case "fatal":
                System.out.println(
                        "[ TEST KERNEL ] Simulating fatal exit."
                );

                System.exit(4);
                break;

            case "fatal-restart":
                System.out.println(
                        "[ TEST KERNEL ] Simulating fatal restart."
                );

                System.exit(5);
                break;

            default:
                System.out.println(
                        "[ TEST KERNEL ] Unknown boot mode: "
                                + bootMode
                );

                System.exit(3);
        }
    }


    private static void probe()
    {
        System.out.println(
                "[ TEST KERNEL ] Probe successful."
        );

        System.out.println(
                "[ TEST KERNEL ] NION test kernel detected."
        );

        System.exit(7);
    }


    private static void bootNormal()
    {
        System.out.println(
                "[ TEST KERNEL ] Normal boot successful."
        );

        System.out.println(
                "[ TEST KERNEL ] Kernel is running."
        );

        System.exit(0);
    }


    private static void bootRepair()
    {
        System.out.println(
                "[ TEST KERNEL ] Repair mode entered."
        );

        System.out.println(
                "[ TEST KERNEL ] Repair completed."
        );

        System.exit(0);
    }
}
