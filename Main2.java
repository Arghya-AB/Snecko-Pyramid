import soot.PackManager;
import soot.Transform;

public class Main2 {
    public static void main(String[] args) {
          String classPath = "./testcases/" + args[0];
        String[] sootArgs = {
            "-cp", classPath, 
            "-pp",
            "-f", "J",
            "-main-class", "Test",
            "-process-dir", classPath
        };

        // Create transformer for analysis
        // SampleBodyTransform pass1 = new SampleBodyTransform();
        ClassTransformer pass2 = new ClassTransformer();
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.idfa", pass2));
        soot.Main.main(sootArgs);
    }
}
