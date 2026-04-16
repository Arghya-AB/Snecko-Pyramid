import java.util.*;
import java.io.*;
import soot.*;
import soot.jimple.*;
import soot.util.Chain;

public class ReplacementTransformer extends SceneTransformer {

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        System.out.println("starting replacement");
        HashMap<ClassFieldGroup, SootClass> mapping = new HashMap<>(ClassTransformer.mapping);
        
    }

   
}