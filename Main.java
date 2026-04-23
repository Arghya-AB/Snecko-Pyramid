import soot.*;

public class Main {

    public static void main(String[] args) {
        String classPath = "./testcases/" + args[0]; // do not change this, as evaluation would have testcases under
                                                     // this directory

        // Set up arguments for Soot
        String[] sootArgs = {
                "-cp", classPath,
                "-pp", // sets the class path for Soot
                "-w",
                "-f", "class",
                "-t", "1",
                "-main-class", "Test", // specify the main class
                "-process-dir", classPath
        };

        // Create transformer for analysis
        SampleSceneTransform pass1 = new SampleSceneTransform();
        ClassTransformer pass2 = new ClassTransformer();
        ReplacementTransformer pass4 = new ReplacementTransformer();

        // initial analysis
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.idfa", pass1));
        // create classes for replacement
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.sample0", pass2));
        // replace all relevant allocation sites with new classes
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.sample2", pass4));
        soot.Main.main(sootArgs);

    }
}
