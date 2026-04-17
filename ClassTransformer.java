import java.util.*;

import java.io.*;
import soot.*;
import soot.jimple.*;
import soot.util.Chain;

public class ClassTransformer extends SceneTransformer {
    // wip
    public static HashMap<ClassFieldGroup, SootClass> mapping = new HashMap<>();
    // base class -> interface class mapping
    public static HashMap<SootClass, SootClass> classInterfaceMapping = new HashMap<>();

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {

        // registering java.lang.Object
        SootClass objectClass = Scene.v().getObjectType().getSootClass();
        mapping.put(new ClassFieldGroup(objectClass, new HashSet<>()), objectClass);
        classInterfaceMapping.put(objectClass, null);

        HashMap<AllocObject, Set<SootField>> liveFields = new HashMap<>(SampleSceneTransform.liveFields);
        System.out.println("classtransformer working");
        // for (Map.Entry<AllocObject, Set<SootField>> entry : liveFields.entrySet()) {
        //     System.out.println("Object " + entry.getKey() + " has live fields: " + entry.getValue());
        // }


        List<SootClass> applicationClasses = new ArrayList<>(Scene.v().getApplicationClasses());

        // add default getters and setters for all classes to all classes 
        for (SootClass sc: applicationClasses) {
            addGettersToClass(sc);
        }

        // create interfaces
        for (SootClass sc: applicationClasses) {
            createInterfaceFromClass(sc);
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


        applicationClasses = new ArrayList<>(Scene.v().getApplicationClasses());

        // printing all classes for debugging
        System.out.println("Printing jimple to for all classes:");
        for (SootClass sc: applicationClasses) {
            String outDir = SourceLocator.v().getOutputDir();
            String fileName = outDir + File.separator + sc + ".jimple";
            try (PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(fileName)))) {
                Printer.v().printTo(sc, writer);
                System.out.println("Successfully wrote: " + fileName);
            } catch (Exception e) {
                System.err.println("Failed to write new class: " + e.getMessage());
            }
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
        Set<SootField> deadFields = new HashSet<>(getAllFields(currClass));
        // removing static fields from dead fields
        deadFields.removeIf(field -> field.isStatic());
        deadFields.removeAll(liveFields);

        // create class
        String newClassName = currClass.getName() + "_SAB";
        if (liveFields.size() == 0) {
            newClassName = newClassName + "_empty" ;
        }
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
            // if method is a getter / setter then skip
            String methodName = method.getName();
            // if (methodName.startsWith("get_SAB") || methodName.startsWith("set_SAB")) {
            //     continue;
            // }

            // check if method is a constructor, if a constructor then, change super call.
            
            if (!method.isConstructor() && referencesDeadField(method, deadFields)) {
                System.out.println("Creating default method (references dead field): "
                    + method.getSignature());

                // adding empty method instead
                SootMethod newMethod = createDefaultMethod(method);
                if (methodName.startsWith("get_SAB") || methodName.startsWith("set_SAB")) {
                    if (!method.isStatic())
                    replaceClassInMethod(newMethod, currClass, newClass);                
                }
                newMethod.setSource(null);
                newClass.addMethod(newMethod);
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
            } else if (method.isConcrete()) {
                // This is the CRITICAL part for the error you're seeing
                // If it's a concrete method but doesn't have an active body yet, retrieve it
                newMethod.setActiveBody((Body) method.retrieveActiveBody().clone());
            }

            if (method.isConstructor()) {
                transformConstructor(newMethod, currSuperClass, currClass.getSuperclass());
            }

            if (methodName.startsWith("get_SAB") || methodName.startsWith("set_SAB")) {
                if (!method.isStatic())
                replaceClassInMethod(newMethod, currClass, newClass);                
            }

            newMethod.setSource(null);
            newClass.addMethod(newMethod);
        }

        // addGettersToClass(newClass);

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

    public static void addGettersToDerivedClass(SootClass currentClass, SootClass baseClass, Set<SootField> liveFields) {
        // 
        Set<SootField> allFields = getAllFields(baseClass);
        Set<SootField> deadFields = new HashSet<>(allFields);
        deadFields.removeAll(liveFields);
        // find deadField getters which are part of baseClass

        
        // for all deadFields create a getter which returns default value
        for (SootField field : deadFields) {
            
        }
    }

    public static void addGettersToClass(SootClass sc) {
    // Get all fields from the hierarchy
    List<SootField> fields = new ArrayList<>(getAllFields(sc));

    for (SootField field : fields) {

        // find appropriate class containing the given SootField
        SootClass owner = field.getDeclaringClass();

        // 1. Define Method Metadata
        String methodName = "get_SAB_" + owner.getShortName() + field.getName();
        Type returnType = field.getType();
        int modifiers = Modifier.PUBLIC;
        if (field.isStatic()) {
            modifiers |= Modifier.STATIC;
        }

        SootMethod getter = new SootMethod(methodName, new ArrayList<>(), returnType, modifiers);
        JimpleBody body = Jimple.v().newBody(getter);
        getter.setActiveBody(body);
        getter.setSource(null);

        // 2. Setup 'this' (only for instance fields)
        Local thisLocal = null;
        if (!field.isStatic()) {
            thisLocal = Jimple.v().newLocal("thisLocal", sc.getType());
            body.getLocals().add(thisLocal);
            body.getUnits().add(Jimple.v().newIdentityStmt(thisLocal, 
                Jimple.v().newThisRef(sc.getType())));
        }

        // 3. Create Local for the return value
        Local retLocal = Jimple.v().newLocal("retVal", returnType);
        body.getLocals().add(retLocal);

        // 4. Generate Field Access
        Value fieldRef;
        if (field.isStatic()) {
            fieldRef = Jimple.v().newStaticFieldRef(field.makeRef());
        } else {
            // Logic for inheritance: Check if field is from a parent
            
            if (owner.equals(sc)) {
                // Field is part of the class itself
                fieldRef = Jimple.v().newInstanceFieldRef(thisLocal, field.makeRef());
            } else {
                // Field is derived from a parent - apply type cast
                // Create a local variable of the parent's type

                Local parentLocal = Jimple.v().newLocal("parentThis", owner.getType());
                body.getLocals().add(parentLocal);
                
                // parentThis = (ParentClass) this
                CastExpr cast = Jimple.v().newCastExpr(thisLocal, owner.getType());
                body.getUnits().add(Jimple.v().newAssignStmt(parentLocal, cast));
                
                // fieldRef = parentThis.field
                fieldRef = Jimple.v().newInstanceFieldRef(parentLocal, field.makeRef());
            }
        }

        // 5. Assign and Return
        body.getUnits().add(Jimple.v().newAssignStmt(retLocal, fieldRef));
        body.getUnits().add(Jimple.v().newReturnStmt(retLocal));

        // 6. Add to class
        sc.addMethod(getter);
    }
}
    
    public SootClass createInterfaceFromClass(SootClass originalClass) {
        // check if already exists
        if (classInterfaceMapping.get(originalClass) != null) {
            return classInterfaceMapping.get(originalClass);
        }
        else if (originalClass == Scene.v().getObjectType().getSootClass()) {
            return null;
        }
        // 1. Define the Interface Name and Modifiers
        String interfaceName = originalClass.getName() + "Interface";
        SootClass newInterface = new SootClass(interfaceName, Modifier.PUBLIC | Modifier.INTERFACE | Modifier.ABSTRACT);
        
        // 2. Interfaces must have java.lang.Object as superclass in Soot
        newInterface.setSuperclass(Scene.v().getSootClass("java.lang.Object"));

        // add parent interface
        SootClass parentClass = originalClass.getSuperclass();
        SootClass parentInterface = classInterfaceMapping.get(parentClass);
        if (parentInterface == null) {
            createInterfaceFromClass(parentClass);
            parentInterface = classInterfaceMapping.get(parentClass);
        }
        if (parentInterface != null) {
            newInterface.addInterface(parentInterface);
        }

        // 3. Clone Method Signatures
        for (SootMethod originalMethod : originalClass.getMethods()) {
            // Skip constructors (<init>) and static initializers (<clinit>)
            if (originalMethod.isConstructor() || originalMethod.isStaticInitializer()) {
                continue;
            }

            // Interfaces generally shouldn't contain private/protected method declarations
            if (!originalMethod.isPublic()) {
                continue;
            }

            // 4. Create the abstract method for the interface
            // Note: Interface methods are implicitly PUBLIC and ABSTRACT
            SootMethod interfaceMethod = new SootMethod(
                originalMethod.getName(),
                originalMethod.getParameterTypes(),
                originalMethod.getReturnType(),
                Modifier.PUBLIC | Modifier.ABSTRACT,
                originalMethod.getExceptions()
            );

            // 5. Add to the interface
            newInterface.addMethod(interfaceMethod);
        }

        // 6. Register the interface in the Scene
        Scene.v().addClass(newInterface);
        newInterface.setApplicationClass();
        
        classInterfaceMapping.put(originalClass, newInterface);
        originalClass.addInterface(newInterface);

        return newInterface;
    }

    public static void replaceClassInMethod(SootMethod method, SootClass oldClass, SootClass newClass) {
        if (!method.hasActiveBody()) return;

        Body body = method.getActiveBody();
        RefType oldType = oldClass.getType();
        RefType newType = newClass.getType();

        // 1. Update Local Variable Types (Handles the "B this;" declaration)
        for (Local local : body.getLocals()) {
            if (local.getType().equals(oldType)) {
                local.setType(newType);
            }
        }

        // 2. Update the Units (Instructions)
        for (Unit unit : body.getUnits()) {
            
            // Handle Identity Statements: this := @this: B -> this := @this: NewClass
            if (unit instanceof IdentityStmt) {
                IdentityStmt is = (IdentityStmt) unit;
                if (is.getRightOp() instanceof ThisRef) {
                    ThisRef tr = (ThisRef) is.getRightOp();
                    if (tr.getType().equals(oldType)) {
                        is.setRightOp(Jimple.v().newThisRef(newType));
                    }
                } else if (is.getRightOp() instanceof ParameterRef) {
                    ParameterRef pr = (ParameterRef) is.getRightOp();
                    if (pr.getType().equals(oldType)) {
                        is.setRightOp(Jimple.v().newParameterRef(newType, pr.getIndex()));
                    }
                }
            }

            // Iterate through all ValueBoxes (Casts, FieldRefs, etc.)
            for (ValueBox box : unit.getUseAndDefBoxes()) {
                Value val = box.getValue();

                // Handle Type Casts: (A) this -> (A) this (no change if casting TO parent)
                // But if the cast itself is TO the old class: (B) x -> (NewClass) x
                if (val instanceof CastExpr) {
                    CastExpr cast = (CastExpr) val;
                    if (cast.getCastType().equals(oldType)) {
                        cast.setCastType(newType);
                    }
                }

                // Handle Field References: <B: int f2> -> <NewClass: int f2>
                if (val instanceof FieldRef) {
                    FieldRef fr = (FieldRef) val;
                    if (fr.getFieldRef().declaringClass().equals(oldClass)) {
                        fr.setFieldRef(Scene.v().makeFieldRef(
                            newClass,
                            fr.getFieldRef().name(),
                            fr.getFieldRef().type(),
                            fr.getFieldRef().isStatic()
                        ));
                    }
                }
                
            }
        }
    }
    // public static void addGettersToClass(SootClass sc) {
    //     // We use a copy of the field list to avoid ConcurrentModificationException 
    //     // if we were adding fields (though here we are only reading them).
    //     List<SootField> fields = new ArrayList<>(getAllFields(sc));

    //     for (SootField field : fields) {
    //         // 1. Define Method Metadata
    //         String methodName = "get_SAB_" + sc.getShortName() + field.getName();
    //         Type returnType = field.getType();
    //         List<Type> parameterTypes = Collections.emptyList();
    //         int modifiers = Modifier.PUBLIC;

    //         // 2. Create the SootMethod
    //         SootMethod getter = new SootMethod(methodName, parameterTypes, returnType, modifiers);
            
    //         // 3. Create the JimpleBody
    //         JimpleBody body = Jimple.v().newBody(getter);
    //         getter.setActiveBody(body);
    //         getter.setSource(null); // Mark as synthetic

    //         // 4. Identity Statement for 'this' (Required for Instance Field Access)
    //         Local thisLocal = Jimple.v().newLocal("this", sc.getType());
    //         body.getLocals().add(thisLocal);
    //         body.getUnits().add(Jimple.v().newIdentityStmt(
    //             thisLocal, 
    //             Jimple.v().newThisRef(sc.getType())
    //         ));

    //         // 5. Create Local for the return value
    //         Local retLocal = Jimple.v().newLocal("retVal", returnType);
    //         body.getLocals().add(retLocal);

    //         // 6. Generate the field access: retVal = this.field
    //         // Note: Use newStaticFieldRef(field.makeRef()) if the field is static
    //         Value fieldRef;
    //         if (field.isStatic()) {
    //             fieldRef = Jimple.v().newStaticFieldRef(field.makeRef());
    //         }
    //         else {
    //             fieldRef = Jimple.v().newInstanceFieldRef(thisLocal, field.makeRef());
    //         }

    //         body.getUnits().add(Jimple.v().newAssignStmt(retLocal, fieldRef));

    //         // 7. Return the value
    //         body.getUnits().add(Jimple.v().newReturnStmt(retLocal));

    //         // 8. Add the method to the class
    //         sc.addMethod(getter);
    //     }
    // }

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

    public static SootMethod createDefaultMethod(SootMethod sourceMethod) {
        String newName = sourceMethod.getName();
        
        SootMethod newMethod = new SootMethod(
            newName,
            sourceMethod.getParameterTypes(),
            sourceMethod.getReturnType(),
            sourceMethod.getModifiers() & ~Modifier.ABSTRACT
        );

        JimpleBody body = Jimple.v().newBody(newMethod);
        newMethod.setActiveBody(body);

        // 1. Handle 'this' for non-static methods
        if (!newMethod.isStatic()) {
            Local thisLocal = Jimple.v().newLocal("this", sourceMethod.getDeclaringClass().getType());
            body.getLocals().add(thisLocal);
            body.getUnits().add(Jimple.v().newIdentityStmt(thisLocal, Jimple.v().newThisRef(sourceMethod.getDeclaringClass().getType())));
        }

        // 2. Handle Parameters (Required for valid Jimple)
        for (int i = 0; i < sourceMethod.getParameterCount(); i++) {
            Type paramType = sourceMethod.getParameterType(i);
            Local paramLocal = Jimple.v().newLocal("param" + i, paramType);
            body.getLocals().add(paramLocal);
            body.getUnits().add(Jimple.v().newIdentityStmt(paramLocal, Jimple.v().newParameterRef(paramType, i)));
        }

        // 3. Generate correct Return Statement
        Type retType = sourceMethod.getReturnType();
        if (retType instanceof VoidType) {
            body.getUnits().add(Jimple.v().newReturnVoidStmt());
        }
        else {
            body.getUnits().add(Jimple.v().newReturnStmt(getDefaultValue(retType)));
        }
        
        return newMethod;
    }

    private static Value getDefaultValue(Type t) {
        if (t instanceof IntType || t instanceof CharType || t instanceof BooleanType || 
            t instanceof ByteType || t instanceof ShortType) {
            return IntConstant.v(0);
        } else if (t instanceof LongType) {
            return LongConstant.v(0L);
        } else if (t instanceof FloatType) {
            return FloatConstant.v(0.0f);
        } else if (t instanceof DoubleType) {
            return DoubleConstant.v(0.0);
        } else if (t instanceof VoidType) {
            // Technically this helper shouldn't be called for Void, 
            // but we'll handle it just in case.
            return null; 
        } else {
            // Any RefType, ArrayType, etc.
            return NullConstant.v();
        }
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
        if (method.isConcrete() && !method.hasActiveBody()) {
            method.retrieveActiveBody();
        }

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
// class ClassFieldGroup {
//     private final SootClass sc;
//     private final Set<SootField> fields;

//     public ClassFieldGroup(SootClass sc, Set<SootField> fields) {
//         this.sc = sc;
//         Set<SootField> liveFields = new HashSet<>(fields);
//         Set<SootField> allFields = ClassTransformer.getAllFields(sc);
//         // only keeping liveFields which are part of allFields i.e. intersection
//         liveFields.retainAll(allFields);
//         this.fields = liveFields;
//     }

//     public SootClass getSootClass() {
//         return sc;
//     }

//     public Set<SootField> getFields() {
//         return fields;
//     }

//     // Equals implementation
//     @Override
//     public boolean equals(Object o) {
//         if (this == o) return true;
//         if (o == null || getClass() != o.getClass()) return false;
//         ClassFieldGroup that = (ClassFieldGroup) o;
//         return Objects.equals(sc, that.sc) && 
//                Objects.equals(fields, that.fields);
//     }

//     // HashCode implementation
//     @Override
//     public int hashCode() {
//         return Objects.hash(sc, fields);
//     }

//     @Override
//     public String toString() {
//         return "ClassFieldGroup{" +
//                 "class=" + (sc != null ? sc.getName() : "null") +
//                 ", fieldsCount=" + (fields != null ? fields.size() : 0) +
//                 '}';
//     }
// }