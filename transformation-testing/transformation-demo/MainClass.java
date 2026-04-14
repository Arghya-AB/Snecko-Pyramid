import soot.PackManager;
import soot.Transform;

public class MainClass {
    public static void main(String[] args) {
        String classPath = ".";

        String[] sootArgs = {
            "-cp", classPath, "-pp", 
            "-w",
            "-f", "J",
            "-keep-line-number",
            "-no-bodies-for-excluded",
            "-p", "jb", "use-original-names",
            "-keep-bytecode-offset", "-keep-offset",
            "-main-class", "Test", "Test"
        };

        PackManager.v().getPack("wjtp").add(new Transform("wjtp.sample", new ClassTransformer()));
        soot.Main.main(sootArgs);
    }
}
