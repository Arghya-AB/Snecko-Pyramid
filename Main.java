import soot.*;

public class Main {

    public static void main(String[] args) {
        String mainClass = args.length > 0 ? args[0] : "Test";
        int additionalArgs = args.length > 0 ? args.length : 3;
        String[] sootArgs = new String[9 + additionalArgs];
        sootArgs[0] = "-cp";
        sootArgs[1] = ".";
        sootArgs[2] = "-pp";
        sootArgs[3] = "-f";
        sootArgs[4] = "J";
        sootArgs[5] = "-keep-line-number";
        sootArgs[6] = "-w";
        sootArgs[7] = "-main-class";
        sootArgs[8] = mainClass;

        if (args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                sootArgs[9 + i] = args[i];
            }
        }

        // Create transformer for analysis
        // SampleBodyTransform pass1 = new SampleBodyTransform();
        SampleSceneTransform pass1 = new SampleSceneTransform();
        ClassTransformer pass2 = new ClassTransformer();
        SampleSceneTransform pass3 = new SampleSceneTransform();
        ReplacementTransformer pass4 = new ReplacementTransformer();
        
        // initial analysis
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.idfa", pass1));
        // create classes for replacement
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.sample0", pass2));
        // analyze again to account for circular situations
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.sample1", pass3));
        // replace all relevant allocation sites with new classes
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.sample2", pass4));
        soot.Main.main(sootArgs);

    }
}
