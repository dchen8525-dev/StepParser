package com.example.stepparser.model;

public sealed interface StepValue permits StepStringValue, StepNumberValue, StepReferenceValue,
        StepEnumValue, StepListValue, StepOmittedValue, StepDerivedValue, StepTypedValue {
}
