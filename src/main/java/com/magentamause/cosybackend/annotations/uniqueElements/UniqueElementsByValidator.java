package com.magentamause.cosybackend.annotations.uniqueElements;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class UniqueElementsByValidator
        implements ConstraintValidator<UniqueElementsBy, Collection<?>> {
    private String[] fieldNames;

    @Override
    public void initialize(UniqueElementsBy constraintAnnotation) {
        this.fieldNames = constraintAnnotation.fieldNames();
    }

    @Override
    public boolean isValid(Collection<?> value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) return true;
        Set<String> seen = new HashSet<>();
        for (Object element : value) {
            if (element == null) continue;
            String key;
            if (fieldNames.length == 0) {
                key = String.valueOf(element);
            } else {
                try {
                    key =
                            Arrays.stream(fieldNames)
                                    .map(
                                            fn -> {
                                                try {
                                                    Field f =
                                                            element.getClass()
                                                                    .getDeclaredField(fn);
                                                    f.setAccessible(true);
                                                    Object v = f.get(element);
                                                    return String.valueOf(v);
                                                } catch (Exception e) {
                                                    throw new RuntimeException(e);
                                                }
                                            })
                                    .collect(Collectors.joining("::"));
                } catch (RuntimeException ex) {
                    return false; // invalid configuration -> fail validation
                }
            }
            if (!seen.add(key)) return false;
        }
        return true;
    }
}
