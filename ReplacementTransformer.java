import java.util.*;
import java.io.*;
import soot.*;
import soot.jimple.*;
import soot.util.Chain;

public class ReplacementTransformer extends SceneTransformer {

  private HashMap<ClassFieldGroup, SootClass> mapping;
  private HashMap<SootClass, SootClass> classInterfaceMapping;
  private HashMap<AllocObject, Set<SootField>> liveFields;
  private Map<AllocObject, Integer> allocObjectToLine;
  private int tempAllocCounter = 0;
  private int castCounter = 0;

  @Override
  protected void internalTransform(String phaseName, Map<String, String> options) {
    System.out.println("Starting replacement transformer");

    mapping = new HashMap<>(ClassTransformer.mapping);
    classInterfaceMapping = new HashMap<>(ClassTransformer.classInterfaceMapping);
    liveFields = new HashMap<>(SampleSceneTransform.liveFields);
    allocObjectToLine = new HashMap<>();

    for (Map.Entry<AllocObject, Set<SootField>> entry : liveFields.entrySet()) {
      allocObjectToLine.put(entry.getKey(), entry.getKey().getLineNumber());
    }

    List<SootClass> applicationClasses = new ArrayList<>(Scene.v().getApplicationClasses());

    for (SootClass sc : applicationClasses) {
      if (isGeneratedClass(sc.getName())) {
        transformGeneratedClass(sc);
      } else {
        transformOriginalClass(sc);
      }
    }

    // we do need jimple files for all transformed classes
    writeUpdatedJimpleFiles();

    System.out.println("Replacement transformer complete");
  }

  private void writeUpdatedJimpleFiles() {
    String outDir = SourceLocator.v().getOutputDir();
    List<SootClass> applicationClasses = new ArrayList<>(Scene.v().getApplicationClasses());

    for (SootClass sc : applicationClasses) {
      String fileName = outDir + File.separator + sc.getName() + ".jimple";
      try (PrintWriter writer = new PrintWriter(
          new OutputStreamWriter(new FileOutputStream(fileName)))) {
        Printer.v().printTo(sc, writer);
        System.out.println("Updated Jimple: " + fileName);
      } catch (Exception e) {
        System.err.println("Failed to write Jimple: " + fileName + " - " + e.getMessage());
      }
    }
  }

  private boolean isGeneratedClass(String className) {
    return className.contains("_SAB_");
  }

  private String getOriginalClassName(String generatedClassName) {
    int idx = generatedClassName.indexOf("_SAB_");
    if (idx > 0) {
      return generatedClassName.substring(0, idx);
    }
    return generatedClassName;
  }

  private void transformOriginalClass(SootClass sc) {
    System.out.println("Transforming original class: " + sc.getName());

    // field types
    List<SootField> fieldsSnapshot = new ArrayList<>(sc.getFields());
    for (SootField field : fieldsSnapshot) {
      if (field.isStatic())
        continue;
      Type fieldType = field.getType();
      Type newType = getMappedType(fieldType);
      if (!fieldType.equals(newType)) {
        field.setType(newType);
      }
    }

    // change methods
    List<SootMethod> methodsSnapshot = new ArrayList<>(sc.getMethods());
    for (SootMethod method : methodsSnapshot) {
      if (!method.isConcrete())
        continue;
      transformOriginalMethod(method);
    }
  }

  private void transformOriginalMethod(SootMethod method) {
    if (!method.hasActiveBody()) {
      try {
        method.retrieveActiveBody();
      } catch (RuntimeException e) {
        return;
      }
    }
    if (!method.hasActiveBody())
      return;

    Body body = method.getActiveBody();
    Map<Local, Local> generatedLocalMap = new HashMap<>();

    // 1. transform ALL assignments (field accesses + new expressions)
    Chain<Unit> unitChain = body.getUnits();
    Iterator<Unit> it = unitChain.snapshotIterator();
    while (it.hasNext()) {
      Unit u = it.next();
      if (u instanceof AssignStmt) {
        AssignStmt assign = (AssignStmt) u;
        Value left = assign.getLeftOp();
        Value right = assign.getRightOp();

        // update types
        updateTypesInValue(left, body);
        updateTypesInValue(right, body);

        // transform field accesses and new expressions
        if (right instanceof NewExpr) {
          transformNewExpr(assign, body, computeRelativeOffset(body, assign), generatedLocalMap);
        } else if (right instanceof NewArrayExpr) {
          transformNewArrayExpr(assign, body);
        } else if (left instanceof InstanceFieldRef) {
          transformFieldStore((InstanceFieldRef) left, assign, body);
        } else if (right instanceof InstanceFieldRef) {
          transformFieldLoad((Local) left, (InstanceFieldRef) right, body);
        } else if (right instanceof InvokeExpr) {
          transformInvokeExpr((InvokeExpr) right, assign, body, generatedLocalMap);
        }
      }
    }

    // 2: update identity statements (@this type)
    it = unitChain.snapshotIterator();
    while (it.hasNext()) {
      Unit u = it.next();
      if (u instanceof IdentityStmt) {
        transformIdentityStmt((IdentityStmt) u, body);
      }
    }

    Local thisLocal = findThisLocal(body);

    // 3: update all local variable types to interface types
    for (Local local : body.getLocals()) {
      if (local.getName().startsWith("temp_alloc_"))
        continue;
      if (thisLocal != null && local.equals(thisLocal))
        continue;
      Type newType = getMappedType(local.getType());
      if (!local.getType().equals(newType)) {
        local.setType(newType);
      }
    }

    // 4: transform invoke statements
    it = unitChain.snapshotIterator();
    while (it.hasNext()) {
      Unit u = it.next();
      if (u instanceof InvokeStmt) {
        transformInvokeStmt((InvokeStmt) u, body, generatedLocalMap);
      }
    }

    updateMethodSignature(method);
  }

  private SootClass transformNewExpr(AssignStmt assign, Body body, int relativeOffset,
      Map<Local, Local> generatedLocalMap) {
    Value right = assign.getRightOp();

    if (!(right instanceof NewExpr))
      return null;

    NewExpr newExpr = (NewExpr) right;
    Type baseType = newExpr.getBaseType();

    if (!(baseType instanceof RefType))
      return null;

    SootClass originalClass = ((RefType) baseType).getSootClass();
    if (!originalClass.isApplicationClass())
      return null;

    int lineNumber = getLineNumber(assign);
    String methodSig = body.getMethod().getSignature();

    AllocObject matchingAlloc = findAllocObject(originalClass, methodSig, lineNumber, relativeOffset);

    Set<SootField> liveFieldsForAlloc = null;
    if (matchingAlloc != null) {
      liveFieldsForAlloc = liveFields.get(matchingAlloc);
    }

    if (liveFieldsForAlloc == null) {
      liveFieldsForAlloc = getAllFields(originalClass);
    }

    ClassFieldGroup cfg = new ClassFieldGroup(originalClass, liveFieldsForAlloc);
    SootClass generatedClass = mapping.get(cfg);

    if (generatedClass == null) {
      generatedClass = findGeneratedClassByName(originalClass.getName());
    }

    if (generatedClass == null) {
      generatedClass = findCompleteVariant(originalClass);
    }

    if (generatedClass == null) {
      System.out.println("Warning: Could not find generated class for " + originalClass.getName());
      return null;
    }

    SootClass interfaceClass = classInterfaceMapping.get(originalClass);
    if (interfaceClass == null) {
      interfaceClass = originalClass;
    }

    Value left = assign.getLeftOp();

    Local tempLocal = Jimple.v().newLocal("temp_alloc_" + (tempAllocCounter++), generatedClass.getType());
    body.getLocals().add(tempLocal);

    NewExpr newGenExpr = Jimple.v().newNewExpr(generatedClass.getType());
    AssignStmt tempAssign = Jimple.v().newAssignStmt(tempLocal, newGenExpr);

    Chain<Unit> units = body.getUnits();
    if (units.contains(assign)) {
      units.insertAfter(tempAssign, assign);
      units.remove(assign);
    }

    if (left instanceof Local) {
      Local leftLocal = (Local) left;
      if (!leftLocal.getType().equals(interfaceClass.getType())) {
        leftLocal.setType(interfaceClass.getType());
      }
      generatedLocalMap.put(leftLocal, tempLocal);
    }

    return generatedClass;
  }

  private AllocObject findAllocObject(SootClass originalClass, String methodSig, int lineNumber, int relativeOffset) {
    for (Map.Entry<AllocObject, Set<SootField>> entry : liveFields.entrySet()) {
      AllocObject alloc = entry.getKey();
      if (alloc.getStClass() != null && alloc.getStClass().equals(originalClass)) {
        if (lineNumber >= 0 && alloc.getLineNumber() == lineNumber) {
          return alloc;
        }
        if (alloc.getRelativeOffset() >= 0 && alloc.getRelativeOffset() == relativeOffset &&
            alloc.getOriginMethodSignature().equals(methodSig)) {
          return alloc;
        }
      }
    }
    return null;
  }

  private void transformFieldStore(InstanceFieldRef leftRef, AssignStmt assign, Body body) {
    Value base = leftRef.getBase();
    SootFieldRef fieldRef = leftRef.getFieldRef();
    String fieldName = fieldRef.name();
    Type fieldType = fieldRef.type();
    SootClass declaringClass = fieldRef.declaringClass();

    if (!(base instanceof Local))
      return;

    Local baseLocal = (Local) base;

    SootClass ownerClass;
    try {
      SootField actualField = fieldRef.resolve();
      ownerClass = actualField.getDeclaringClass();
    } catch (Exception e) {
      // failed resolution
      ownerClass = declaringClass;
    }

    Value right = assign.getRightOp();
    Type rightType = right.getType();

    // which setter - assignment type
    String setterName;
    if (rightType instanceof ArrayType && fieldType instanceof ArrayType) {
      // array assignment - array setter
      setterName = "set_SAB_Array_" + ownerClass.getShortName() + fieldName;
    } else {
      // standard setter
      setterName = "set_SAB_" + ownerClass.getShortName() + fieldName;
    }

    Type baseType = baseLocal.getType();
    SootClass baseClass = null;
    if (baseType instanceof RefType) {
      baseClass = ((RefType) baseType).getSootClass();
      SootClass mapped = classInterfaceMapping.get(baseClass);
      if (mapped != null) {
        baseClass = mapped;
      }
    }

    SootMethodRef setterRef = Scene.v().makeMethodRef(
        baseClass,
        setterName,
        Collections.singletonList(fieldType),
        VoidType.v(),
        false);

    InvokeExpr invokeExpr;
    if (baseClass != null && baseClass.isInterface()) {
      invokeExpr = Jimple.v().newInterfaceInvokeExpr(baseLocal, setterRef, right);
    } else {
      invokeExpr = Jimple.v().newVirtualInvokeExpr(baseLocal, setterRef, right);
    }
    InvokeStmt invokeStmt = Jimple.v().newInvokeStmt(invokeExpr);

    Chain<Unit> units = body.getUnits();
    if (units.contains(assign)) {
      units.insertAfter(invokeStmt, assign);
      units.remove(assign);
    }
  }

  private void transformFieldLoad(Local left, InstanceFieldRef rightRef, Body body) {
    Value base = rightRef.getBase();
    SootFieldRef fieldRef = rightRef.getFieldRef();
    String fieldName = fieldRef.name();
    Type fieldType = fieldRef.type();
    SootClass declaringClass = fieldRef.declaringClass();

    if (!(base instanceof Local))
      return;

    Local baseLocal = (Local) base;

    SootClass ownerClass;
    try {
      SootField actualField = fieldRef.resolve();
      ownerClass = actualField.getDeclaringClass();
    } catch (Exception e) {
      ownerClass = declaringClass;
    }

    String getterName = "get_SAB_" + ownerClass.getShortName() + fieldName;

    Type baseType = baseLocal.getType();
    SootClass baseClass = null;
    if (baseType instanceof RefType) {
      baseClass = ((RefType) baseType).getSootClass();
      SootClass mapped = classInterfaceMapping.get(baseClass);
      if (mapped != null) {
        baseClass = mapped;
      }
    }

    SootMethodRef getterRef = Scene.v().makeMethodRef(
        baseClass,
        getterName,
        Collections.emptyList(),
        fieldType,
        false);

    InvokeExpr invokeExpr;
    if (baseClass != null && baseClass.isInterface()) {
      invokeExpr = Jimple.v().newInterfaceInvokeExpr(baseLocal, getterRef);
    } else {
      invokeExpr = Jimple.v().newVirtualInvokeExpr(baseLocal, getterRef);
    }
    AssignStmt newAssign = Jimple.v().newAssignStmt(left, invokeExpr);

    Chain<Unit> units = body.getUnits();
    Unit found = null;
    for (Unit u : units) {
      if (u instanceof AssignStmt) {
        AssignStmt a = (AssignStmt) u;
        if (a.getLeftOp().equals(left) && a.getRightOp().equals(rightRef)) {
          found = u;
          break;
        }
      }
    }
    if (found != null) {
      units.insertAfter(newAssign, found);
      units.remove(found);
    }
  }

  private void transformIdentityStmt(IdentityStmt istmt, Body body) {
    Value right = istmt.getRightOp();
    if (right instanceof ThisRef) {
      SootClass currentClass = body.getMethod().getDeclaringClass();
      // Always use concrete class type for @this
      istmt.setRightOp(Jimple.v().newThisRef(currentClass.getType()));
    }
  }

  private void transformInvokeStmt(InvokeStmt invoke, Body body, Map<Local, Local> generatedLocalMap) {
    transformInvokeExpr(invoke.getInvokeExpr(), invoke, body, generatedLocalMap);
  }

  private void transformInvokeExpr(InvokeExpr invokeExpr, Stmt stmt, Body body, Map<Local, Local> generatedLocalMap) {
    if (invokeExpr instanceof SpecialInvokeExpr) {
      SpecialInvokeExpr specialInvoke = (SpecialInvokeExpr) invokeExpr;
      SootMethod method = specialInvoke.getMethod();
      if (method.getName().equals("<init>")) {
        Value base = specialInvoke.getBase();
        if (base instanceof Local) {
          Local baseLocal = (Local) base;

          if (baseLocal.getName().startsWith("temp_alloc_")) {
            return;
          }

          Local tempLocal = generatedLocalMap.get(baseLocal);
          if (tempLocal != null) {
            specialInvoke.setBase(tempLocal);

            SootClass genClass = ((RefType) tempLocal.getType()).getSootClass();
            List<Type> paramTypes = new ArrayList<>();
            for (Type t : method.getParameterTypes()) {
              paramTypes.add(getMappedType(t));
            }
            SootMethodRef newInitRef = Scene.v().makeMethodRef(
                genClass,
                "<init>",
                paramTypes,
                method.getReturnType(),
                false);
            specialInvoke.setMethodRef(newInitRef);

            CastExpr castExpr = Jimple.v().newCastExpr(tempLocal, baseLocal.getType());
            AssignStmt castAssign = Jimple.v().newAssignStmt(baseLocal, castExpr);
            body.getUnits().insertAfter(castAssign, stmt);

            generatedLocalMap.remove(baseLocal);
            return;
          }

          // only remove specialinvoke for non-constructor cases.
          Type baseType = baseLocal.getType();
          if (baseType instanceof RefType) {
            RefType rt = (RefType) baseType;
            SootClass baseClass = rt.getSootClass();

            // only remove if it's NOT a constructor call
            if (baseClass.isInterface() && !method.getName().equals("<init>")) {
              body.getUnits().remove(stmt);
              return;
            }
          }
        }
      }
    }

    SootMethodRef methodRef = invokeExpr.getMethodRef();
    List<Type> newParamTypes = new ArrayList<>();
    for (Type t : methodRef.getParameterTypes()) {
      newParamTypes.add(getMappedType(t));
    }
    Type newReturnType = getMappedType(methodRef.getReturnType());

    SootClass declaringClass = methodRef.getDeclaringClass();
    SootClass mappedDeclaringClass;
    if (methodRef.getName().equals("<init>")) {
      // for constructors, keep original class
      mappedDeclaringClass = declaringClass;
    } else {
      mappedDeclaringClass = classInterfaceMapping.get(declaringClass);
      if (mappedDeclaringClass == null)
        mappedDeclaringClass = declaringClass;
    }

    SootMethodRef newMethodRef = Scene.v().makeMethodRef(
        mappedDeclaringClass,
        methodRef.getName(),
        newParamTypes,
        newReturnType,
        methodRef.isStatic());
    invokeExpr.setMethodRef(newMethodRef);

    if (invokeExpr instanceof VirtualInvokeExpr) {
      VirtualInvokeExpr virtualInvoke = (VirtualInvokeExpr) invokeExpr;
      Value base = virtualInvoke.getBase();
      if (mappedDeclaringClass.isInterface()) {
        InterfaceInvokeExpr newInvokeExpr = Jimple.v().newInterfaceInvokeExpr((Local) base, newMethodRef,
            virtualInvoke.getArgs());
        if (stmt instanceof InvokeStmt) {
          ((InvokeStmt) stmt).setInvokeExpr(newInvokeExpr);
        } else if (stmt instanceof AssignStmt) {
          ((AssignStmt) stmt).setRightOp(newInvokeExpr);
        }
      }
    }
  }

  private void updateMethodSignature(SootMethod method) {
    Type returnType = method.getReturnType();
    Type newReturnType = getMappedType(returnType);
    if (!returnType.equals(newReturnType)) {
      method.setReturnType(newReturnType);
    }

    List<Type> paramTypes = method.getParameterTypes();
    List<Type> newParamTypes = new ArrayList<>();
    for (Type param : paramTypes) {
      newParamTypes.add(getMappedType(param));
    }
    method.setParameterTypes(newParamTypes);
  }

  private Type getMappedType(Type t) {
    if (t instanceof RefType) {
      SootClass originalClass = ((RefType) t).getSootClass();
      SootClass interfaceMapped = classInterfaceMapping.get(originalClass);
      return (interfaceMapped != null) ? interfaceMapped.getType() : t;
    } else if (t instanceof ArrayType) {
      return replaceBaseClassInArray(t);
    }
    return t;
  }

  private Type replaceBaseClassInArray(Type type) {
    if (type instanceof ArrayType) {
      ArrayType at = (ArrayType) type;
      if (at.baseType instanceof RefType) {
        SootClass currentBaseClass = ((RefType) at.baseType).getSootClass();
        SootClass interfaceClass = classInterfaceMapping.get(currentBaseClass);
        if (interfaceClass != null) {
          return ArrayType.v(interfaceClass.getType(), at.numDimensions);
        }
      }
    }
    return type;
  }

  private Set<SootField> getAllFields(SootClass sClass) {
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

  private SootClass findGeneratedClassByName(String originalName) {
    for (ClassFieldGroup cfg : mapping.keySet()) {
      SootClass originalClass = cfg.getSootClass();
      if (originalClass.getName().equals(originalName)) {
        return mapping.get(cfg);
      }
    }
    return null;
  }

  private SootClass findCompleteVariant(SootClass originalClass) {
    for (ClassFieldGroup cfg : mapping.keySet()) {
      SootClass sc = cfg.getSootClass();
      if (sc.getName().equals(originalClass.getName())) {
        Set<SootField> fields = getAllFields(originalClass);
        fields.removeIf(f -> f.isStatic());
        if (cfg.getFields().containsAll(fields)) {
          return mapping.get(cfg);
        }
      }
    }
    return null;
  }

  private int getLineNumber(Unit u) {
    Integer existing = SampleSceneTransform.unitIds.get(u);
    return existing != null ? existing : -1;
  }

  private int computeRelativeOffset(Body body, Unit targetUnit) {
    int offset = 0;
    for (Unit u : body.getUnits()) {
      if (u == targetUnit) {
        return offset;
      }
      offset++;
    }
    return -1;
  }

  private void updateTypesInValue(Value val, Body body) {
    if (val instanceof Local) {
      Local local = (Local) val;
      if (local.getName().startsWith("temp_alloc_"))
        return;
      Type newType = getMappedType(local.getType());
      if (!local.getType().equals(newType)) {
        local.setType(newType);
      }
    } else if (val instanceof CastExpr) {
      CastExpr cast = (CastExpr) val;
      Type newCastType = getMappedType(cast.getCastType());
      if (!cast.getCastType().equals(newCastType)) {
        cast.setCastType(newCastType);
      }
    } else if (val instanceof InstanceFieldRef) {
      InstanceFieldRef ifr = (InstanceFieldRef) val;
      SootFieldRef fieldRef = ifr.getFieldRef();
      Type fieldType = getMappedType(fieldRef.type());
      if (!fieldRef.type().equals(fieldType)) {
        SootClass declaringClass = fieldRef.declaringClass();
        ifr.setFieldRef(Scene.v().makeFieldRef(
            declaringClass,
            fieldRef.name(),
            fieldType,
            fieldRef.isStatic()));
      }
    }
  }

  private void transformGeneratedClass(SootClass sc) {
    System.out.println("Transforming generated class: " + sc.getName());

    String originalName = getOriginalClassName(sc.getName());

    // Ufield types to interface types
    for (SootField field : sc.getFields()) {
      if (field.isStatic())
        continue;
      Type fieldType = field.getType();
      Type newType = getMappedType(fieldType);
      if (!fieldType.equals(newType)) {
        field.setType(newType);
      }
    }

    // transform methods
    List<SootMethod> methodsSnapshot = new ArrayList<>(sc.getMethods());
    for (SootMethod method : methodsSnapshot) {
      String methodName = method.getName();
      if (methodName.startsWith("get_SAB_") || methodName.startsWith("set_SAB_")) {
        continue;
      }
      if (!method.isConcrete())
        continue;
      transformGeneratedMethod(method, originalName);
    }
  }

  private void transformGeneratedMethod(SootMethod method, String originalClassName) {
    if (!method.hasActiveBody()) {
      try {
        method.retrieveActiveBody();
      } catch (RuntimeException e) {
        return;
      }
    }
    if (!method.hasActiveBody())
      return;

    Body body = method.getActiveBody();
    Map<Local, Local> generatedLocalMap = new HashMap<>();

    Local thisLocal = findThisLocal(body);
    Chain<Unit> unitChain = body.getUnits();
    Iterator<Unit> it = unitChain.snapshotIterator();
    while (it.hasNext()) {
      Unit u = it.next();
      if (u instanceof AssignStmt) {
        AssignStmt assign = (AssignStmt) u;
        Value left = assign.getLeftOp();
        Value right = assign.getRightOp();

        if (!(left instanceof Local && left.equals(thisLocal))) {
          updateTypesInValue(left, body);
        }
        updateTypesInValue(right, body);
        if (right instanceof NewExpr) {
          transformNewExprInGenerated(assign, body, originalClassName, generatedLocalMap);
        } else if (right instanceof NewArrayExpr) {
          transformNewArrayExpr(assign, body);
        } else if (left instanceof InstanceFieldRef) {
          transformFieldStoreInGenerated((InstanceFieldRef) left, assign, body);
        } else if (right instanceof InstanceFieldRef) {
          transformFieldLoadInGenerated((Local) left, (InstanceFieldRef) right, body);
        } else if (right instanceof InvokeExpr) {
          transformInvokeExpr((InvokeExpr) right, assign, body, generatedLocalMap);
        }
      }
    }

    it = unitChain.snapshotIterator();
    while (it.hasNext()) {
      Unit u = it.next();
      if (u instanceof IdentityStmt) {
        transformIdentityStmt((IdentityStmt) u, body);
      }
    }

    if (thisLocal != null) {
      SootClass generatedClass = body.getMethod().getDeclaringClass();
      Type concreteType = generatedClass.getType();
      if (!thisLocal.getType().equals(concreteType)) {
        thisLocal.setType(concreteType);
      }
    }

    // 5: update all local variable types to interface types
    // but skip the 'this' local - it should stay as concrete generated class type
    for (Local local : body.getLocals()) {
      if (local.getName().startsWith("temp_alloc_"))
        continue;
      if (thisLocal != null && local.equals(thisLocal))
        continue;
      Type newType = getMappedType(local.getType());
      if (!local.getType().equals(newType)) {
        local.setType(newType);
      }
    }

    // 6: transform invoke
    it = unitChain.snapshotIterator();
    while (it.hasNext()) {
      Unit u = it.next();
      if (u instanceof InvokeStmt) {
        transformInvokeStmt((InvokeStmt) u, body, generatedLocalMap);
      }
    }

    updateMethodSignature(method);
  }

  private Local findThisLocal(Body body) {
    for (Unit u : body.getUnits()) {
      if (u instanceof IdentityStmt) {
        IdentityStmt istmt = (IdentityStmt) u;
        Value right = istmt.getRightOp();
        if (right instanceof ThisRef) {
          Value left = istmt.getLeftOp();
          if (left instanceof Local) {
            return (Local) left;
          }
        }
      }
    }
    return null;
  }

  private SootClass transformNewExprInGenerated(AssignStmt assign, Body body, String originalClassName,
      Map<Local, Local> generatedLocalMap) {
    Value right = assign.getRightOp();

    if (!(right instanceof NewExpr))
      return null;

    NewExpr newExpr = (NewExpr) right;
    Type baseType = newExpr.getBaseType();

    if (!(baseType instanceof RefType))
      return null;

    SootClass originalClass = ((RefType) baseType).getSootClass();
    if (!originalClass.isApplicationClass())
      return null;

    int lineNumber = getLineNumber(assign);
    String methodSig = buildOriginalMethodSignature(body.getMethod(), originalClassName);

    AllocObject matchingAlloc = findAllocObject(originalClass, methodSig, lineNumber,
        computeRelativeOffset(body, assign));

    Set<SootField> liveFieldsForAlloc = null;
    if (matchingAlloc != null) {
      liveFieldsForAlloc = liveFields.get(matchingAlloc);
    }

    if (liveFieldsForAlloc == null) {
      liveFieldsForAlloc = getAllFields(originalClass);
    }

    ClassFieldGroup cfg = new ClassFieldGroup(originalClass, liveFieldsForAlloc);
    SootClass generatedClass = mapping.get(cfg);

    if (generatedClass == null) {
      generatedClass = findGeneratedClassByName(originalClass.getName());
    }

    if (generatedClass == null) {
      generatedClass = findCompleteVariant(originalClass);
    }

    if (generatedClass == null)
      return null;

    SootClass interfaceClass = classInterfaceMapping.get(originalClass);

    Value left = assign.getLeftOp();

    Local tempLocal = Jimple.v().newLocal("temp_alloc_" + (tempAllocCounter++), generatedClass.getType());
    body.getLocals().add(tempLocal);

    NewExpr newGenExpr = Jimple.v().newNewExpr(generatedClass.getType());
    AssignStmt tempAssign = Jimple.v().newAssignStmt(tempLocal, newGenExpr);

    Chain<Unit> units = body.getUnits();
    if (units.contains(assign)) {
      units.insertAfter(tempAssign, assign);
      units.remove(assign);
    }

    if (left instanceof Local) {
      Local leftLocal = (Local) left;
      Type targetType = interfaceClass != null ? interfaceClass.getType() : generatedClass.getType();
      if (!leftLocal.getType().equals(targetType)) {
        leftLocal.setType(targetType);
      }
      generatedLocalMap.put(leftLocal, tempLocal);
    }

    return generatedClass;
  }

  private String buildOriginalMethodSignature(SootMethod method, String originalClassName) {
    return "<" + originalClassName + ": " + method.getSubSignature() + ">";
  }

  private void transformNewArrayExpr(AssignStmt assign, Body body) {
    Value right = assign.getRightOp();
    if (!(right instanceof NewArrayExpr))
      return;

    NewArrayExpr newArrayExpr = (NewArrayExpr) right;
    Type elementType = newArrayExpr.getBaseType();

    if (elementType instanceof RefType) {
      SootClass elementClass = ((RefType) elementType).getSootClass();
      SootClass interfaceClass = classInterfaceMapping.get(elementClass);
      if (interfaceClass != null) {
        NewArrayExpr newExpr = Jimple.v().newNewArrayExpr(interfaceClass.getType(), newArrayExpr.getSize());
        assign.setRightOp(newExpr);
      }
    }
  }

  private void transformFieldStoreInGenerated(InstanceFieldRef leftRef, AssignStmt assign, Body body) {
    Value base = leftRef.getBase();
    SootFieldRef fieldRef = leftRef.getFieldRef();
    String fieldName = fieldRef.name();
    Type fieldType = fieldRef.type();
    SootClass declaringClass = fieldRef.declaringClass();

    if (!(base instanceof Local))
      return;

    Local baseLocal = (Local) base;

    SootClass ownerClass;
    try {
      SootField actualField = fieldRef.resolve();
      ownerClass = actualField.getDeclaringClass();
    } catch (Exception e) {
      ownerClass = declaringClass;
    }

    String setterName = "set_SAB_" + ownerClass.getShortName() + fieldName;

    Value right = assign.getRightOp();
    Type baseType = baseLocal.getType();
    SootClass interfaceClass = null;
    if (baseType instanceof RefType) {
      SootClass baseClass = ((RefType) baseType).getSootClass();
      if (baseClass.isInterface()) {
        interfaceClass = baseClass;
      } else {
        interfaceClass = classInterfaceMapping.get(baseClass);
      }
    }

    // use interface class for method ref if available, otherwise use the concrete
    // cls
    SootClass methodRefClass = interfaceClass != null ? interfaceClass : ownerClass;
    Type methodRefType = methodRefClass.getType();

    Chain<Unit> units = body.getUnits();

    Local castLocal = Jimple.v().newLocal("$r_cast_" + (castCounter++), methodRefType);
    body.getLocals().add(castLocal);

    CastExpr castExpr = Jimple.v().newCastExpr(baseLocal, methodRefType);
    AssignStmt castStmt = Jimple.v().newAssignStmt(castLocal, castExpr);

    SootMethodRef setterRef = Scene.v().makeMethodRef(
        methodRefClass,
        setterName,
        Collections.singletonList(fieldType),
        VoidType.v(),
        false);

    InvokeExpr invokeExpr;
    if (methodRefClass.isInterface()) {
      invokeExpr = Jimple.v().newInterfaceInvokeExpr(castLocal, setterRef, right);
    } else {
      invokeExpr = Jimple.v().newVirtualInvokeExpr(castLocal, setterRef, right);
    }
    InvokeStmt invokeStmt = Jimple.v().newInvokeStmt(invokeExpr);

    if (units.contains(assign)) {
      units.insertBefore(castStmt, assign);
      units.insertAfter(invokeStmt, castStmt);
      units.remove(assign);
    }
  }

  private void transformFieldLoadInGenerated(Local left, InstanceFieldRef rightRef, Body body) {
    Value base = rightRef.getBase();
    SootFieldRef fieldRef = rightRef.getFieldRef();
    String fieldName = fieldRef.name();
    Type fieldType = fieldRef.type();
    SootClass declaringClass = fieldRef.declaringClass();

    if (!(base instanceof Local))
      return;

    Local baseLocal = (Local) base;

    SootClass ownerClass;
    try {
      SootField actualField = fieldRef.resolve();
      ownerClass = actualField.getDeclaringClass();
    } catch (Exception e) {
      ownerClass = declaringClass;
    }

    String getterName = "get_SAB_" + ownerClass.getShortName() + fieldName;

    Type baseType = baseLocal.getType();
    SootClass interfaceClass = null;
    if (baseType instanceof RefType) {
      SootClass baseClass = ((RefType) baseType).getSootClass();
      if (baseClass.isInterface()) {
        interfaceClass = baseClass;
      } else {
        interfaceClass = classInterfaceMapping.get(baseClass);
      }
    }

    SootClass methodRefClass = interfaceClass != null ? interfaceClass : ownerClass;
    Type methodRefType = methodRefClass.getType();

    Chain<Unit> units = body.getUnits();

    Local castLocal = Jimple.v().newLocal("$r_cast_" + (castCounter++), methodRefType);
    body.getLocals().add(castLocal);

    CastExpr castExpr = Jimple.v().newCastExpr(baseLocal, methodRefType);
    AssignStmt castStmt = Jimple.v().newAssignStmt(castLocal, castExpr);

    SootMethodRef getterRef = Scene.v().makeMethodRef(
        methodRefClass,
        getterName,
        Collections.emptyList(),
        fieldType,
        false);

    InvokeExpr invokeExpr;
    if (methodRefClass.isInterface()) {
      invokeExpr = Jimple.v().newInterfaceInvokeExpr(castLocal, getterRef);
    } else {
      invokeExpr = Jimple.v().newVirtualInvokeExpr(castLocal, getterRef);
    }
    AssignStmt newAssign = Jimple.v().newAssignStmt(left, invokeExpr);

    Unit found = null;
    for (Unit u : units) {
      if (u instanceof AssignStmt) {
        AssignStmt a = (AssignStmt) u;
        if (a.getLeftOp().equals(left) && a.getRightOp().equals(rightRef)) {
          found = u;
          break;
        }
      }
    }
    if (found != null) {
      units.insertBefore(castStmt, found);
      units.insertAfter(newAssign, castStmt);
      units.remove(found);
    }
  }
}
