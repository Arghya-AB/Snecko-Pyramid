import java.util.*;
import java.io.*;
import soot.*;
import soot.jimple.*;
import soot.options.Options;

public class ClassTransformer extends SceneTransformer {
    // wip
    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        SootClass sClass = Scene.v().getMainClass();
        List<SootField> deadFields = new ArrayList<>();
        if (!sClass.getFields().isEmpty()) {
            deadFields.add(sClass.getFields().getFirst());
        }
        createNewClassWithoutDeadFields(sClass, deadFields);
    }

    /**
     * Returns true if the method body references any field in deadFields,
     * either as a read (r = obj.field) or a write (obj.field = v).
     *
     * Every Jimple Unit exposes all Value operands through getUseAndDefBoxes().
     * FieldRef (InstanceFieldRef or StaticFieldRef) is the Jimple node for
     * any field access, so checking ValueBoxes covers both sides of every
     * assignment automatically.
     */
    private boolean referencesDeadField(SootMethod method, List<SootField> deadFields) {
        if (!method.hasActiveBody()) {
            return false;
        }

        for (Unit unit : method.getActiveBody().getUnits()) {
            for (ValueBox box : unit.getUseAndDefBoxes()) {
                Value val = box.getValue();

                // InstanceFieldRef  → obj.field
                // StaticFieldRef    → ClassName.field
                if (val instanceof FieldRef) {
                    SootField referenced = ((FieldRef) val).getField();
                    if (deadFields.contains(referenced)) {
                        return true;
                    }
                }
            }
        }
        return false;
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
                SootField newField = new SootField(
                    field.getName(), field.getType(), field.getModifiers()
                );
                newClass.addField(newField);
            }
        }

        // 3. Clone Methods — skip any that touch a dead field
        for (SootMethod method : originalClass.getMethods()) {

            if (referencesDeadField(method, deadFields)) {
                System.out.println("Skipping method (references dead field): "
                    + method.getSignature());
                continue;                          // ← drop the method entirely
            }

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

        // 5. Manually write to file
        String outDir = SourceLocator.v().getOutputDir();
        String fileName = outDir + File.separator + newClassName + ".jimple";
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(fileName)))) {
            Printer.v().printTo(newClass, writer);
            System.out.println("Successfully wrote: " + fileName);
        } catch (Exception e) {
            System.err.println("Failed to write new class: " + e.getMessage());
        }
    }
}