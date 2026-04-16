import java.util.*;
import java.io.*;
import soot.*;
import soot.jimple.*;
import soot.options.Options;
import soot.util.Chain;

public class ClassTransformer extends SceneTransformer {
    // wip
    private static HashMap<ClassFieldGroup, SootClass> mapping = new HashMap<>();

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        // registering java.lang.Object
        SootClass objectClass = Scene.v().getObjectType().getSootClass();
        mapping.put(new ClassFieldGroup(objectClass, new HashSet<>()), objectClass);
        
        HashMap<AllocObject, Set<SootField>> liveFields = new HashMap<>(SampleSceneTransform.liveFields);
        System.out.println("testing classtransformer");
        for (Map.Entry<AllocObject, Set<SootField>> entry : liveFields.entrySet()) {
            System.out.println("Object " + entry.getKey() + " has live fields: " + entry.getValue());
        }

        // creating classes for all AllocObjects
        // create SootClass, Set<SootField> pairs to prevent redundancy
        Set<ClassFieldGroup> workSet = new HashSet<>();
        for (Map.Entry<AllocObject, Set<SootField>> entry : liveFields.entrySet()) {
            workSet.add(new ClassFieldGroup(entry.getKey().getStClass(), entry.getValue()));
        }

        // process workSet
        for (ClassFieldGroup cg : workSet) {
            // call function to create class for cg if not in mapping
            classCreator(cg);
        }
    }

    private static void classCreator(ClassFieldGroup input) {
        // find class and field
        SootClass currClass = input.getSootClass();
        Set<SootField> liveFields = new HashSet<>(input.getFields());
        Set<SootField> allFields = getAllFields(currClass);
        // only keeping liveFields which are part of allFields i.e. intersection
        liveFields.retainAll(allFields);
        // find all dead fields
        Set<SootField> deadFields = getAllFields(currClass);
        // removing static fields from dead fields
        for (SootField field: deadFields) {
            if (field.isStatic()) {
                deadFields.remove(field);
            }
        }
        deadFields.removeAll(liveFields);

        // create class
        String newClassName = currClass.getName();
        for (SootField field : liveFields) {
            newClassName = newClassName + "_" + field.getName();
        }

        SootClass newClass = new SootClass(newClassName, currClass.getModifiers());

        // setting superclass
        ClassFieldGroup superClassFieldGroup = new ClassFieldGroup(currClass.getSuperclass(), input.getFields());
        SootClass currSuperClass = mapping.get(superClassFieldGroup);
        if (currSuperClass == null) {
            classCreator(superClassFieldGroup);
            currSuperClass = mapping.get(superClassFieldGroup);
        }
        newClass.setSuperclass(currSuperClass);

        // adding interfaces
                
        for (SootClass interfaceClass : currClass.getInterfaces()) {
            newClass.addInterface(interfaceClass);
        }

        // add live fields

        for (SootField field : currClass.getFields()) {
            if (!deadFields.contains(field)) {
                SootField newField = new SootField(
                    field.getName(), field.getType(), field.getModifiers()
                );
                newClass.addField(newField);
            }
        }

        // clone method
        for (SootMethod method : currClass.getMethods()) {

            // check if method is a constructor, if a constructor then, change super call.
            
            if (!method.isConstructor() && referencesDeadField(method, deadFields)) {
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

            if (method.isConstructor()) {
                transformConstructor(newMethod, currSuperClass, currClass.getSuperclass());
            }
            newClass.addMethod(newMethod);
        }

        // registering in scene
        
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

        // add to mapping
        mapping.put(new ClassFieldGroup(currClass, liveFields), newClass);

    }

    public static void transformConstructor(SootMethod constructor, SootClass newSuperClass, SootClass oldSuperClass) {
        if (!constructor.hasActiveBody()) return;

        Body body = constructor.getActiveBody();
        Chain<Unit> units = body.getUnits();
        
        Unit previousUnit = null;

        // Find the super() call
        for (Unit u : units) {
            Stmt s = (Stmt) u;
            if (s.containsInvokeExpr() && s.getInvokeExpr() instanceof SpecialInvokeExpr) {
                SpecialInvokeExpr sie = (SpecialInvokeExpr) s.getInvokeExpr();
                SootMethodRef targetMethod = sie.getMethodRef();

                // Check if this is indeed an <init> call (the super call)
                if (targetMethod.getName().equals("<init>") && targetMethod.getDeclaringClass().equals(oldSuperClass)) {
                    
                    boolean isNewObjectAllocation = false;
                    if (previousUnit instanceof AssignStmt) {
                        AssignStmt assign = (AssignStmt) previousUnit;
                        if (assign.getRightOp() instanceof NewExpr) {
                            isNewObjectAllocation = true;
                        }
                    }


                    if (!isNewObjectAllocation) {
                        SootMethod newConstructor = newSuperClass.getMethod("<init>", new ArrayList<Type>());

                        // 2. Get the MethodRef from that actual method
                        SootMethodRef newMethodRef = newConstructor.makeRef();

                        // 2. Update the InvokeExpr to point to the new method
                        sie.setMethodRef(newMethodRef);
                    }
                    // We found the super call and swapped it, we can stop now
                }
            }
            previousUnit = u;
        }
    }

    public static Set<SootField> getAllFields(SootClass sClass) {
        Set<SootField> allFields = new HashSet<>();
        SootClass currentClass = sClass;

        while (currentClass != null) {
            allFields.addAll(currentClass.getFields());
            
            // Check if there is a superclass and if it's not java.lang.Object (optional stop)
            if (currentClass.hasSuperclass()) {
                currentClass = currentClass.getSuperclass();
            } else {
                currentClass = null;
            }
        }
        return allFields;
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


    private static boolean referencesDeadField(SootMethod method, Set<SootField> deadFields) {
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

    /*
    public void createNewClassWithoutDeadFields(SootClass originalClass, Set<SootField> liveFields) {


        // 1. Create New Class Name
        String newClassName = originalClass.getName();
        for (SootField field : deadFields) {
            newClassName = newClassName + "_" + field.getName();
        }

        SootClass newClass = new SootClass(newClassName, originalClass.getModifiers());
        
        
        
        // needs fixing superClass may be different !!!!!!!!

        
        
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

    */
}
class ClassFieldGroup {
    private final SootClass sc;
    private final Set<SootField> fields;

    public ClassFieldGroup(SootClass sc, Set<SootField> fields) {
        this.sc = sc;
        Set<SootField> liveFields = new HashSet<>(fields);
        Set<SootField> allFields = ClassTransformer.getAllFields(sc);
        // only keeping liveFields which are part of allFields i.e. intersection
        liveFields.retainAll(allFields);
        this.fields = liveFields;
    }

    public SootClass getSootClass() {
        return sc;
    }

    public Set<SootField> getFields() {
        return fields;
    }

    // Equals implementation
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassFieldGroup that = (ClassFieldGroup) o;
        return Objects.equals(sc, that.sc) && 
               Objects.equals(fields, that.fields);
    }

    // HashCode implementation
    @Override
    public int hashCode() {
        return Objects.hash(sc, fields);
    }

    @Override
    public String toString() {
        return "ClassFieldGroup{" +
                "class=" + (sc != null ? sc.getName() : "null") +
                ", fieldsCount=" + (fields != null ? fields.size() : 0) +
                '}';
    }
}