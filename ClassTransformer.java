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
            addSettersToClass(sc);
        }

        // create interfaces
        for (SootClass sc: applicationClasses) {
            createInterfaceFromClass(sc);
        } 

        // change all interface getter and setter return type and params
        for (SootClass sc: applicationClasses) {
            interfaceTypeFixer(classInterfaceMapping.get(sc));
        }

        // change all instances of classes to class interface in application classes

        // change all instances of classes to class interface in interfaces


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
            newClassName = newClassName + "_" + field.getDeclaringClass().getName() + field.getName();
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
                if (methodName.startsWith("get_SAB")) {
                    // if (!method.isStatic())
                    replaceClassInMethodForGet(newMethod, currClass, newClass, liveFields);                
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

            if (methodName.startsWith("get_SAB")) {
                // if (!method.isStatic())
                replaceClassInMethodForGet(newMethod, currClass, newClass, liveFields);                
            }

            newMethod.setSource(null);
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

    public void interfaceTypeFixer(SootClass interfaceClass) {
        // Only process if it's actually an interface
        if (!interfaceClass.isInterface()) return;

        List<SootMethod> methodSnapshot = new ArrayList<>(interfaceClass.getMethods());

        for (SootMethod method : methodSnapshot) {
            
            // 1. Transform Return Type
            Type oldRetType = method.getReturnType();
            Type newRetType = getMappedType(oldRetType);
            if (!oldRetType.equals(newRetType)) {
                method.setReturnType(newRetType);
            }

            // 2. Transform Parameter Types
            List<Type> oldParams = method.getParameterTypes();
            List<Type> newParams = new ArrayList<>();
            boolean paramChanged = false;

            for (Type paramType : oldParams) {
                Type transformed = getMappedType(paramType);
                newParams.add(transformed);
                if (!transformed.equals(paramType)) {
                    paramChanged = true;
                }
            }

            if (paramChanged) {
                method.setParameterTypes(newParams);
            }
        }
    }

    /**
     * Helper to decide if a type needs mapping to an interface or a new array base.
     */
    private Type getMappedType(Type t) {
        if (t instanceof RefType) {
            SootClass originalClass = ((RefType) t).getSootClass();
            SootClass interfaceMapped = classInterfaceMapping.get(originalClass);
            // Return the interface type if found, otherwise the original (e.g., Object, String)
            return (interfaceMapped != null) ? interfaceMapped.getType() : t;
        } 
        else if (t instanceof ArrayType) {
            // Uses your existing logic to swap the base class inside the array
            return replaceBaseClassInArray(t);
        }
        return t;
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
    
    public static void addSettersToClass(SootClass sc) {
        List<SootField> fields = new ArrayList<>(getAllFields(sc));

        for (SootField field : fields) {
            SootClass owner = field.getDeclaringClass();
            Type fieldType = field.getType();
            String methodName = "set_SAB_" + owner.getShortName() + field.getName();

            List<Type> parameterTypes = new ArrayList<>();
            boolean isArray = fieldType instanceof ArrayType;

            // 1. Determine Parameters
            if (isArray) {
                // For arrays: set_SAB_Field(int index, BaseType value)
                parameterTypes.add(IntType.v());
                parameterTypes.add(((ArrayType) fieldType).getElementType());
            } else {
                // For standard: set_SAB_Field(FieldType value)
                parameterTypes.add(fieldType);
            }

            SootMethod setter = new SootMethod(methodName, parameterTypes, VoidType.v(), Modifier.PUBLIC);
            if (field.isStatic()) setter.setModifiers(setter.getModifiers() | Modifier.STATIC);
            
            JimpleBody body = Jimple.v().newBody(setter);
            setter.setActiveBody(body);

            // 2. Identity: 'this'
            Local thisLocal = null;
            if (!field.isStatic()) {
                thisLocal = Jimple.v().newLocal("thisLocal", sc.getType());
                body.getLocals().add(thisLocal);
                body.getUnits().add(Jimple.v().newIdentityStmt(thisLocal, Jimple.v().newThisRef(sc.getType())));
            }

            // 3. Identity: Parameters
            Local indexLocal = null;
            Local valueLocal = null;

            if (isArray) {
                // param0 = index
                indexLocal = Jimple.v().newLocal("idx", IntType.v());
                body.getLocals().add(indexLocal);
                body.getUnits().add(Jimple.v().newIdentityStmt(indexLocal, Jimple.v().newParameterRef(IntType.v(), 0)));

                // param1 = value to insert
                Type elementType = ((ArrayType) fieldType).getElementType();
                valueLocal = Jimple.v().newLocal("val", elementType);
                body.getLocals().add(valueLocal);
                body.getUnits().add(Jimple.v().newIdentityStmt(valueLocal, Jimple.v().newParameterRef(elementType, 1)));
            } else {
                // param0 = value to set
                valueLocal = Jimple.v().newLocal("val", fieldType);
                body.getLocals().add(valueLocal);
                body.getUnits().add(Jimple.v().newIdentityStmt(valueLocal, Jimple.v().newParameterRef(fieldType, 0)));
            }

            // 4. Field Access Logic (same inheritance/cast logic as your getter)
            Value fieldRef;
            if (field.isStatic()) {
                fieldRef = Jimple.v().newStaticFieldRef(field.makeRef());
            } else {
                Local baseLocal = thisLocal;
                if (!owner.equals(sc)) {
                    baseLocal = Jimple.v().newLocal("parentThis", owner.getType());
                    body.getLocals().add(baseLocal);
                    body.getUnits().add(Jimple.v().newAssignStmt(baseLocal, Jimple.v().newCastExpr(thisLocal, owner.getType())));
                }
                fieldRef = Jimple.v().newInstanceFieldRef(baseLocal, field.makeRef());
            }

            // 5. Assignment Logic
            if (isArray) {
                // We need a local to hold the array reference before accessing the index
                Local arrayLocal = Jimple.v().newLocal("tempArray", fieldType);
                body.getLocals().add(arrayLocal);
                body.getUnits().add(Jimple.v().newAssignStmt(arrayLocal, fieldRef));

                // Generate: tempArray[idx] = val
                ArrayRef arrayRef = Jimple.v().newArrayRef(arrayLocal, indexLocal);
                body.getUnits().add(Jimple.v().newAssignStmt(arrayRef, valueLocal));
            } else {
                // Generate: field = val
                body.getUnits().add(Jimple.v().newAssignStmt(fieldRef, valueLocal));
            }

            body.getUnits().add(Jimple.v().newReturnVoidStmt());
            sc.addMethod(setter);
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


    public static void replaceClassInMethodForGet(SootMethod method, SootClass baseClass, SootClass currClass, Set<SootField> liveFields) {
        if (!method.hasActiveBody()) return;

        Body body = method.getActiveBody();

        Type returnType = method.getReturnType();

        // changing return type of method to interface if needed
        if (!(returnType instanceof PrimType)) {
            if (returnType instanceof RefType){
                SootClass returnClass = ((RefType) returnType).getSootClass();
                returnClass = classInterfaceMapping.get(returnClass);
                // setting returntype of method
                returnType = returnClass.getType();
            }
            else if (returnType instanceof ArrayType) {
                returnType = replaceBaseClassInArray((ArrayType) returnType);
            }
            method.setReturnType(returnType);
        }
        
        // Type currClassType = mapping.get(new ClassFieldGroup(currClass, liveFields)).getType();
        Type currClassType = currClass.getType();

        // find appropriate parent class for currClass
        SootClass parentClass = null;
        for (Local local : body.getLocals()) {
            if (local.getName().equals("parentThis")) {
                System.out.println("found parentThis");
                parentClass = ((RefType) local.getType()).getSootClass();
                parentClass = mapping.get(new ClassFieldGroup(parentClass, liveFields));
                if (parentClass == null) {
                    System.out.println("CLASS " + ((RefType) local.getType()).getSootClass().getName() + "MISSING");
                }
            }
        }
        
        Type parentType;
        if (parentClass != null)
            parentType = parentClass.getType();
        else
            parentType = null;

        // 1. Update Local Variable Types (Handles the "B this;" declaration)
        for (Local local : body.getLocals()) {
            if (local.getName().equals("retVal")) {
               if (!(returnType instanceof PrimType)) {
                    local.setType(returnType);
                }
            }
            else if (local.getName().equals("parentThis")) {
                local.setType(parentType);
            }
            else if (local.getName().equals("thisLocal")) {
                local.setType(currClassType);
            }
        }

        // update parameter types
        List<Type> params = new ArrayList<>(method.getParameterTypes());
        for (int i = 0; i < params.size(); i++) {
            Type param = params.get(i);
            if (param instanceof RefType) {
                SootClass paramClass = ((RefType) param).getSootClass();
                paramClass = classInterfaceMapping.get(paramClass);
                params.set(i, paramClass.getType());
            }
            else if (param instanceof ArrayType) {
                params.set(i, replaceBaseClassInArray(param));
            }
        }

        method.setParameterTypes(params);

        // 2. Update the Units (Instructions)
        for (Unit unit : body.getUnits()) {
            
            // Handle Identity Statements: this := @this: B -> this := @this: NewClass
            if (unit instanceof IdentityStmt) {
                IdentityStmt is = (IdentityStmt) unit;
                if (is.getRightOp() instanceof ThisRef) {
                    is.setRightOp(Jimple.v().newThisRef((RefType) currClassType));
                } else if (is.getRightOp() instanceof ParameterRef) {
                    ParameterRef pr = (ParameterRef) is.getRightOp();
                    Type param = pr.getType();
                    if (param instanceof RefType) {
                        SootClass paramClass = ((RefType) param).getSootClass();
                        paramClass = classInterfaceMapping.get(paramClass);
                        param = paramClass.getType();
                    }
                    else if (param instanceof ArrayType) {
                        param = replaceBaseClassInArray(param);
                    }
                    is.setRightOp(Jimple.v().newParameterRef(param, pr.getIndex()));
                }
            }

            // Iterate through all ValueBoxes (Casts, FieldRefs, etc.)
            for (ValueBox box : unit.getUseAndDefBoxes()) {
                Value val = box.getValue();

                // Handle Type Casts: (A) this -> (A) this (no change if casting TO parent)
                // But if the cast itself is TO the old class: (B) x -> (NewClass) x
                if (val instanceof CastExpr) {
                    CastExpr cast = (CastExpr) val;
                    cast.setCastType(parentType);
                }

                // Handle Field References: <B: int f2> -> <NewClass: int f2>
                if (val instanceof FieldRef) {
                    FieldRef fr = (FieldRef) val;
                    Type fieldRefType = fr.getFieldRef().type();
                    SootClass fieldRefClass = currClass;
                    if (parentClass != null)
                        fieldRefClass = parentClass;
                    if (fr.getFieldRef().isStatic()) {
                        fieldRefClass = fr.getField().getDeclaringClass();
                    }
                    if (fieldRefType instanceof RefType) {
                        SootClass fieldType = ((RefType) fieldRefType).getSootClass();
                        fieldType = classInterfaceMapping.get(fieldType);
                        fieldRefType = fieldType.getType();
                    }
                    else if (fieldRefType instanceof ArrayType) {
                        fieldRefType = replaceBaseClassInArray(fieldRefType);
                    }
                    fr.setFieldRef(Scene.v().makeFieldRef(
                        fieldRefClass,
                        fr.getFieldRef().name(),
                        fieldRefType,
                        fr.getFieldRef().isStatic()
                    ));
                }
                
            }
        }
    }
    
    public static Type replaceBaseClassInArray(Type type) {
    // 1. Check if it is an ArrayType
    if (type instanceof ArrayType) {
        ArrayType at = (ArrayType) type;

        // 2. Check if the base of the array is a RefType (an object class)
        if (at.baseType instanceof RefType) {
            SootClass currentBaseClass = ((RefType) at.baseType).getSootClass();

            // 3. If the base class matches the one we want to replace
            SootClass interfaceClass = classInterfaceMapping.get(currentBaseClass);
            // if (interfaceClass == null) {
            //     System.out.println("currentBaseClass went wrong replaceBaseClassInArray: " + currentBaseClass);
            // }
            if (interfaceClass != null)
            return ArrayType.v(interfaceClass.getType(), at.numDimensions);
        }
    }
    
    // Return original type if it wasn't an array of oldClass
    return type;
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
            Local thisLocal = Jimple.v().newLocal("thisLocal", sourceMethod.getDeclaringClass().getType());
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