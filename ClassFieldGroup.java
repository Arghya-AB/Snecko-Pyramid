import java.util.*;
import soot.*;

public class ClassFieldGroup {
    private final SootClass sc;
    private final Set<SootField> fields;

    public ClassFieldGroup(SootClass sc, Set<SootField> fields) {
        this.sc = sc;
        Set<SootField> liveFields = new HashSet<>(fields);
        Set<SootField> allFields = getAllFields(sc);
        liveFields.retainAll(allFields);
        this.fields = liveFields;
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

    public SootClass getSootClass() {
        return sc;
    }

    public Set<SootField> getFields() {
        return fields;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassFieldGroup that = (ClassFieldGroup) o;
        return Objects.equals(sc, that.sc) && 
               Objects.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sc, fields);
    }

}
