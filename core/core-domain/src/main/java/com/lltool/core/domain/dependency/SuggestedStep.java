package com.lltool.core.domain.dependency;

import java.util.Objects;

/**
 * A suggested safe migration step when altering or dropping a database object with dependencies.
 */
public class SuggestedStep {
    private final int stepNumber;
    private final String actionType;
    private final String targetObject;
    private final String description;
    private final String sqlScript;

    public SuggestedStep(int stepNumber, String actionType, String targetObject, String description, String sqlScript) {
        this.stepNumber = stepNumber;
        this.actionType = Objects.requireNonNull(actionType, "actionType must not be null");
        this.targetObject = Objects.requireNonNull(targetObject, "targetObject must not be null");
        this.description = description != null ? description : "";
        this.sqlScript = sqlScript != null ? sqlScript : "";
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public String getActionType() {
        return actionType;
    }

    public String getTargetObject() {
        return targetObject;
    }

    public String getDescription() {
        return description;
    }

    public String getSqlScript() {
        return sqlScript;
    }

    @Override
    public String toString() {
        return "SuggestedStep{" +
                "stepNumber=" + stepNumber +
                ", actionType='" + actionType + '\'' +
                ", targetObject='" + targetObject + '\'' +
                '}';
    }
}
