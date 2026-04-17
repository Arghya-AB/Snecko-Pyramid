import soot.*;

public class Main {

    public static void main(String[] args) {
        String classPath = "./testcases/" + args[0]; //do not change this, as evaluation would have testcases under this directory

        //Set up arguments for Soot
        String[] sootArgs = {
            "-cp", classPath, 
            "-pp", // sets the class path for Soot
            "-w",
            "-f", "J",
            "-t", "1",
            "-main-class", "Test",	// specify the main class
            "-process-dir", classPath
        };

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
