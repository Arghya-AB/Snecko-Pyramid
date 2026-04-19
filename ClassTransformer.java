import java.util.*;

import java.io.*;
import soot.*;
import soot.jimple.*;
import soot.util.Chain;

public class ClassTransformer extends SceneTransformer {
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


        // convert all base classes' getters and setters to trivial
        Set<SootClass> baseClassSet = new HashSet<>(classInterfaceMapping.keySet());
        for (SootClass baseClass : baseClassSet) {
            convertBaseClassGettersSetters(baseClass);
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
                if (methodName.startsWith("set_SAB")) {
                    // if (!method.isStatic())
                    replaceClassInMethodForSet(newMethod, currClass, newClass, liveFields);                
                }
                newMethod.setSource(null);
                newClass.addMethod(newMethod);
                continue;
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
                newMethod.setActiveBody((Body) method.retrieveActiveBody().clone());
            }

            if (method.isConstructor()) {
                transformConstructor(newMethod, currSuperClass, currClass.getSuperclass());
            }

            if (methodName.startsWith("get_SAB")) {
                // if (!method.isStatic())
                replaceClassInMethodForGet(newMethod, currClass, newClass, liveFields);                
            }
            if (methodName.startsWith("set_SAB")) {
                // if (!method.isStatic())
                replaceClassInMethodForSet(newMethod, currClass, newClass, liveFields);                
            } 

            newMethod.setSource(null);
            newClass.addMethod(newMethod);
        }

        // registering in scene
        
        Scene.v().addClass(newClass);
        newClass.setApplicationClass();
        newClass.setInScene(true);

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

    public void convertBaseClassGettersSetters(SootClass sc) {
    List<SootMethod> methodSnapshot = new ArrayList<>(sc.getMethods());

    for (SootMethod method : methodSnapshot) {
        String name = method.getName();

        if (name.startsWith("get_SAB_") || name.startsWith("set_SAB_")) {
            if (!method.hasActiveBody()) continue;

            Body body = method.getActiveBody();

            Type newRetType = getMappedType(method.getReturnType());
            method.setReturnType(newRetType);

            List<Type> params = new ArrayList<>(method.getParameterTypes());
            for (int i = 0; i < params.size(); i++) {
                params.set(i, getMappedType(params.get(i)));
            }
            method.setParameterTypes(params);

            SootClass parentClass = null;
            for (Local local : body.getLocals()) {
                if (local.getName().equals("parentThis")) {
                    SootClass originalParent = ((RefType) local.getType()).getSootClass();
                    parentClass = originalParent;
                }
            }
            Type parentType = (parentClass != null) ? parentClass.getType() : null;

            for (Local local : body.getLocals()) {
                String localName = local.getName();
                if (localName.equals("thisLocal")) {
                    local.setType(sc.getType());
                } else if (localName.equals("parentThis")) {
                    local.setType(parentType);
                } else if (localName.equals("retVal") || localName.equals("val") || 
                           localName.equals("newVal") || localName.equals("tempArray")) {
                    local.setType(getMappedType(local.getType()));
                }
            }

            for (Unit unit : body.getUnits()) {
                
                if (unit instanceof IdentityStmt) {
                    IdentityStmt is = (IdentityStmt) unit;
                    if (is.getRightOp() instanceof ThisRef) {
                        is.setRightOp(Jimple.v().newThisRef(sc.getType()));
                    } else if (is.getRightOp() instanceof ParameterRef) {
                        ParameterRef pr = (ParameterRef) is.getRightOp();
                        is.setRightOp(Jimple.v().newParameterRef(getMappedType(pr.getType()), pr.getIndex()));
                    }
                }

                for (ValueBox box : unit.getUseAndDefBoxes()) {
                    Value val = box.getValue();

                    if (val instanceof CastExpr) {
                        ((CastExpr) val).setCastType(parentType);
                    }

                    if (val instanceof FieldRef) {
                        FieldRef fr = (FieldRef) val;
                        SootField oldField = fr.getField();
                        
                        SootClass fieldRefClass = sc;
                        if (fr.getFieldRef().isStatic()) {
                            fieldRefClass = oldField.getDeclaringClass();
                        } else if (parentClass != null && !oldField.getDeclaringClass().equals(sc)) {
                            fieldRefClass = parentClass;
                        }

                        fr.setFieldRef(Scene.v().makeFieldRef(
                            fieldRefClass,
                            fr.getFieldRef().name(),
                            getMappedType(fr.getFieldRef().type()),
                            fr.getFieldRef().isStatic()
                        ));
                    }
                }
            }
        }
    }
}

    public void interfaceTypeFixer(SootClass interfaceClass) {
        if (!interfaceClass.isInterface()) return;

        List<SootMethod> methodSnapshot = new ArrayList<>(interfaceClass.getMethods());

        for (SootMethod method : methodSnapshot) {
            
            Type oldRetType = method.getReturnType();
            Type newRetType = getMappedType(oldRetType);
            if (!oldRetType.equals(newRetType)) {
                method.setReturnType(newRetType);
            }

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

    private static Type getMappedType(Type t) {
        if (t instanceof RefType) {
            SootClass originalClass = ((RefType) t).getSootClass();
            SootClass interfaceMapped = classInterfaceMapping.get(originalClass);
            return (interfaceMapped != null) ? interfaceMapped.getType() : t;
        } 
        else if (t instanceof ArrayType) {
            return replaceBaseClassInArray(t);
        }
        return t;
    }

    public static void addGettersToClass(SootClass sc) {
        List<SootField> fields = new ArrayList<>(getAllFields(sc));

        for (SootField field : fields) {

            SootClass owner = field.getDeclaringClass();

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

            Local thisLocal = null;
            if (!field.isStatic()) {
                thisLocal = Jimple.v().newLocal("thisLocal", sc.getType());
                body.getLocals().add(thisLocal);
                body.getUnits().add(Jimple.v().newIdentityStmt(thisLocal, 
                    Jimple.v().newThisRef(sc.getType())));
            }

            Local retLocal = Jimple.v().newLocal("retVal", returnType);
            body.getLocals().add(retLocal);

            Value fieldRef;
            if (field.isStatic()) {
                fieldRef = Jimple.v().newStaticFieldRef(field.makeRef());
            } else {
                
                if (owner.equals(sc)) {
                    fieldRef = Jimple.v().newInstanceFieldRef(thisLocal, field.makeRef());
                } else {

                    Local parentLocal = Jimple.v().newLocal("parentThis", owner.getType());
                    body.getLocals().add(parentLocal);
                    
                    CastExpr cast = Jimple.v().newCastExpr(thisLocal, owner.getType());
                    body.getUnits().add(Jimple.v().newAssignStmt(parentLocal, cast));
                    
                    fieldRef = Jimple.v().newInstanceFieldRef(parentLocal, field.makeRef());
                }
            }

            body.getUnits().add(Jimple.v().newAssignStmt(retLocal, fieldRef));
            body.getUnits().add(Jimple.v().newReturnStmt(retLocal));

            sc.addMethod(getter);
        }
    }
    
    public static void addSettersToClass(SootClass sc) {
            List<SootField> fields = new ArrayList<>(getAllFields(sc));

            for (SootField field : fields) {
                SootClass owner = field.getDeclaringClass();
                Type fieldType = field.getType();
                boolean isArray = fieldType instanceof ArrayType;

                String standardMethodName = "set_SAB_" + owner.getShortName() + field.getName();
                createAndAddSetter(sc, field, standardMethodName, false);

                if (isArray) {
                    String arrayMethodName = "set_SAB_Array_" + owner.getShortName() + field.getName();
                    createAndAddSetter(sc, field, arrayMethodName, true);
                }
            }
        }

    public static void replaceClassInMethodForSet(SootMethod method, SootClass baseClass, SootClass currClass, Set<SootField> liveFields) {
        if (!method.hasActiveBody()) return;

        Body body = method.getActiveBody();
        Type currClassType = currClass.getType();

        List<Type> params = new ArrayList<>(method.getParameterTypes());
        for (int i = 0; i < params.size(); i++) {
            params.set(i, getMappedType(params.get(i)));
        }
        method.setParameterTypes(params);

        SootClass parentClass = null;
        SootClass parentBaseClass = null;
        for (Local local : body.getLocals()) {
            if (local.getName().equals("parentThis")) {
                SootClass originalParent = ((RefType) local.getType()).getSootClass();
                parentBaseClass = originalParent;
                parentClass = mapping.get(new ClassFieldGroup(originalParent, liveFields));
            }
        }
        Type parentType = (parentClass != null) ? parentClass.getType() : null;

        for (Local local : body.getLocals()) {
            String name = local.getName();
            Type t = local.getType();

            if (name.equals("thisLocal")) {
                local.setType(currClassType);
            } else if (name.equals("parentThis")) {
                local.setType(parentType);
            } else if (name.equals("val") || name.equals("newVal")) {
                local.setType(getMappedType(t));
            } else if (name.equals("tempArray")) {
                local.setType(getMappedType(t));
            }
        }

        for (Unit unit : body.getUnits()) {
            
            if (unit instanceof IdentityStmt) {
                IdentityStmt is = (IdentityStmt) unit;
                if (is.getRightOp() instanceof ThisRef) {
                    is.setRightOp(Jimple.v().newThisRef((RefType) currClassType));
                } else if (is.getRightOp() instanceof ParameterRef) {
                    ParameterRef pr = (ParameterRef) is.getRightOp();
                    is.setRightOp(Jimple.v().newParameterRef(getMappedType(pr.getType()), pr.getIndex()));
                }
            }

            for (ValueBox box : unit.getUseAndDefBoxes()) {
                Value val = box.getValue();

                if (val instanceof CastExpr) {
                    ((CastExpr) val).setCastType(parentType);
                }

                if (val instanceof FieldRef) {
                    FieldRef fr = (FieldRef) val;
                    SootField oldField = fr.getField();
                    
                    SootClass fieldRefClass = currClass;
                    if (fr.getFieldRef().isStatic()) {
                        fieldRefClass = oldField.getDeclaringClass(); 
                    } else if (parentClass != null && !oldField.getDeclaringClass().equals(baseClass)) {
                        fieldRefClass = parentClass;
                    }

                    fr.setFieldRef(Scene.v().makeFieldRef(
                        fieldRefClass,
                        fr.getFieldRef().name(),
                        getMappedType(fr.getFieldRef().type()),
                        fr.getFieldRef().isStatic()
                    ));
                }
            }
        }
    }

    private static void createAndAddSetter(SootClass sc, SootField field, String methodName, boolean isWholeArraySetter) {
        SootClass owner = field.getDeclaringClass();
        Type fieldType = field.getType();
        boolean isArrayField = fieldType instanceof ArrayType;

        List<Type> parameterTypes = new ArrayList<>();
        if (isArrayField && !isWholeArraySetter) {
            parameterTypes.add(IntType.v());
            parameterTypes.add(((ArrayType) fieldType).getElementType());
        } else {
            parameterTypes.add(fieldType);
        }

        int modifiers = Modifier.PUBLIC;
        if (field.isStatic()) modifiers |= Modifier.STATIC;

        SootMethod setter = new SootMethod(methodName, parameterTypes, VoidType.v(), modifiers);
        JimpleBody body = Jimple.v().newBody(setter);
        setter.setActiveBody(body);

        Local thisLocal = null;
        if (!field.isStatic()) {
            thisLocal = Jimple.v().newLocal("thisLocal", sc.getType());
            body.getLocals().add(thisLocal);
            body.getUnits().add(Jimple.v().newIdentityStmt(thisLocal, Jimple.v().newThisRef(sc.getType())));
        }

        Local valueLocal;
        Local indexLocal = null;

        if (isArrayField && !isWholeArraySetter) {
            indexLocal = Jimple.v().newLocal("idx", IntType.v());
            body.getLocals().add(indexLocal);
            body.getUnits().add(Jimple.v().newIdentityStmt(indexLocal, Jimple.v().newParameterRef(IntType.v(), 0)));

            Type elementType = ((ArrayType) fieldType).getElementType();
            valueLocal = Jimple.v().newLocal("val", elementType);
            body.getLocals().add(valueLocal);
            body.getUnits().add(Jimple.v().newIdentityStmt(valueLocal, Jimple.v().newParameterRef(elementType, 1)));
        } else {
            valueLocal = Jimple.v().newLocal("newVal", fieldType);
            body.getLocals().add(valueLocal);
            body.getUnits().add(Jimple.v().newIdentityStmt(valueLocal, Jimple.v().newParameterRef(fieldType, 0)));
        }

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

        if (isArrayField && !isWholeArraySetter) {
            Local arrayLocal = Jimple.v().newLocal("tempArray", fieldType);
            body.getLocals().add(arrayLocal);
            body.getUnits().add(Jimple.v().newAssignStmt(arrayLocal, fieldRef));
            body.getUnits().add(Jimple.v().newAssignStmt(Jimple.v().newArrayRef(arrayLocal, indexLocal), valueLocal));
        } else {
            body.getUnits().add(Jimple.v().newAssignStmt(fieldRef, valueLocal));
        }

        body.getUnits().add(Jimple.v().newReturnVoidStmt());
        sc.addMethod(setter);
    }


    public SootClass createInterfaceFromClass(SootClass originalClass) {
        // check if already exists
        if (classInterfaceMapping.get(originalClass) != null) {
            return classInterfaceMapping.get(originalClass);
        }
        else if (originalClass == Scene.v().getObjectType().getSootClass()) {
            return null;
        }
        String interfaceName = originalClass.getName() + "Interface";
        SootClass newInterface = new SootClass(interfaceName, Modifier.PUBLIC | Modifier.INTERFACE | Modifier.ABSTRACT);
        
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

        for (SootMethod originalMethod : originalClass.getMethods()) {
            if (originalMethod.isConstructor() || originalMethod.isStaticInitializer()) {
                continue;
            }

            if (!originalMethod.isPublic()) {
                continue;
            }

            SootMethod interfaceMethod = new SootMethod(
                originalMethod.getName(),
                originalMethod.getParameterTypes(),
                originalMethod.getReturnType(),
                Modifier.PUBLIC | Modifier.ABSTRACT,
                originalMethod.getExceptions()
            );

            newInterface.addMethod(interfaceMethod);
        }

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

        for (Unit unit : body.getUnits()) {
            
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

            for (ValueBox box : unit.getUseAndDefBoxes()) {
                Value val = box.getValue();

                if (val instanceof CastExpr) {
                    CastExpr cast = (CastExpr) val;
                    cast.setCastType(parentType);
                }

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
    if (type instanceof ArrayType) {
        ArrayType at = (ArrayType) type;

        if (at.baseType instanceof RefType) {
            SootClass currentBaseClass = ((RefType) at.baseType).getSootClass();

            SootClass interfaceClass = classInterfaceMapping.get(currentBaseClass);
            if (interfaceClass != null)
            return ArrayType.v(interfaceClass.getType(), at.numDimensions);
        }
    }
    
    return type;
}
    public void convertGettersSettersToTrivial(SootClass sc) {
        List<SootMethod> methodSnapshot = new ArrayList<>(sc.getMethods());

        for (SootMethod originalMethod : methodSnapshot) {
            String name = originalMethod.getName();

            if (name.startsWith("get_SAB_") || name.startsWith("set_SAB_")) {
                
                Type newRetType = getMappedType(originalMethod.getReturnType());
                List<Type> newParams = new ArrayList<>();
                for (Type t : originalMethod.getParameterTypes()) {
                    newParams.add(getMappedType(t));
                }

                SootMethod trivialMethod = new SootMethod(
                    name,
                    newParams,
                    newRetType,
                    originalMethod.getModifiers() & ~Modifier.ABSTRACT
                );

                JimpleBody body = Jimple.v().newBody(trivialMethod);
                trivialMethod.setActiveBody(body);

                if (!trivialMethod.isStatic()) {
                    Local thisLocal = Jimple.v().newLocal("thisLocal", sc.getType());
                    body.getLocals().add(thisLocal);
                    body.getUnits().add(Jimple.v().newIdentityStmt(thisLocal, 
                        Jimple.v().newThisRef(sc.getType())));
                }

                for (int i = 0; i < newParams.size(); i++) {
                    Type pType = newParams.get(i);
                    Local pLocal = Jimple.v().newLocal("param" + i, pType);
                    body.getLocals().add(pLocal);
                    body.getUnits().add(Jimple.v().newIdentityStmt(pLocal, 
                        Jimple.v().newParameterRef(pType, i)));
                }

                if (newRetType instanceof VoidType) {
                    body.getUnits().add(Jimple.v().newReturnVoidStmt());
                } else {
                    body.getUnits().add(Jimple.v().newReturnStmt(getDefaultValue(newRetType)));
                }

                sc.removeMethod(originalMethod);
                sc.addMethod(trivialMethod);
            }
        }
    }

    public static void transformConstructor(SootMethod constructor, SootClass newSuperClass, SootClass oldSuperClass) {
        if (!constructor.hasActiveBody()) return;

        Body body = constructor.getActiveBody();
        Chain<Unit> units = body.getUnits();
        
        Unit previousUnit = null;

        for (Unit u : units) {
            Stmt s = (Stmt) u;
            if (s.containsInvokeExpr() && s.getInvokeExpr() instanceof SpecialInvokeExpr) {
                SpecialInvokeExpr sie = (SpecialInvokeExpr) s.getInvokeExpr();
                SootMethodRef targetMethod = sie.getMethodRef();

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

                        SootMethodRef newMethodRef = newConstructor.makeRef();

                        sie.setMethodRef(newMethodRef);
                    }
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

        if (!newMethod.isStatic()) {
            Local thisLocal = Jimple.v().newLocal("thisLocal", sourceMethod.getDeclaringClass().getType());
            body.getLocals().add(thisLocal);
            body.getUnits().add(Jimple.v().newIdentityStmt(thisLocal, Jimple.v().newThisRef(sourceMethod.getDeclaringClass().getType())));
        }

        for (int i = 0; i < sourceMethod.getParameterCount(); i++) {
            Type paramType = sourceMethod.getParameterType(i);
            Local paramLocal = Jimple.v().newLocal("param" + i, paramType);
            body.getLocals().add(paramLocal);
            body.getUnits().add(Jimple.v().newIdentityStmt(paramLocal, Jimple.v().newParameterRef(paramType, i)));
        }

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
            return null; 
        } else {
            return NullConstant.v();
        }
    }

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

}
