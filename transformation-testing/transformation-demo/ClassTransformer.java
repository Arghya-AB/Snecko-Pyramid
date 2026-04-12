import java.util.*;
import java.io.*; // For File handling
import soot.*;
import soot.options.Options; // For output format constants

public class ClassTransformer extends SceneTransformer {

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        SootClass sClass = Scene.v().getMainClass();
        List<SootField> deadFields = new ArrayList<>();
        
        if (!sClass.getFields().isEmpty()) {
            deadFields.add(sClass.getFields().getFirst());
        }
        
        createNewClassWithoutDeadFields(sClass, deadFields);
    }

    public void createNewClassWithoutDeadFields(SootClass originalClass, List<SootField> deadFields) {
        // 1. Create New Class Name
        String newClassName = originalClass.getName();
        for (SootField field : deadFields) {
            newClassName = newClassName + "_" + field.getName();
        }
        
        SootClass newClass = new SootClass(newClassName, originalClass.getModifiers());
        newClass.setSuperclass(originalClass.getSuperclass());
        
        for (SootClass interfaceClass : originalClass.getInterfaces()) {
            newClass.addInterface(interfaceClass);
        }

        // 2. Add Live Fields
        for (SootField field : originalClass.getFields()) {
            if (!deadFields.contains(field)) {
                SootField newField = new SootField(field.getName(), field.getType(), field.getModifiers());
                newClass.addField(newField);
            }
        }

        // 3. Clone Methods
        for (SootMethod method : originalClass.getMethods()) {
            SootMethod newMethod = new SootMethod(
                method.getName(),
                method.getParameterTypes(),
                method.getReturnType(),
                method.getModifiers(),
                method.getExceptions()
            );

            if (method.hasActiveBody()) {
                newMethod.setActiveBody((Body) method.getActiveBody().clone());
            }
            newClass.addMethod(newMethod);
        }

        // 4. Register in Scene
        Scene.v().addClass(newClass);
        newClass.setApplicationClass();
        newClass.setInScene(true);

        // 5. MANUALLY WRITE TO FILE
        // This ensures the class is written even if Soot's auto-output missed it
        String outDir = SourceLocator.v().getOutputDir();
        String fileName = outDir + File.separator + newClassName + ".jimple";
        
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fileName)))) {
            Printer.v().printTo(newClass, writer);
            System.out.println("Successfully wrote: " + fileName);
        } catch (Exception e) {
            System.err.println("Failed to write new class: " + e.getMessage());
        }
    }
}