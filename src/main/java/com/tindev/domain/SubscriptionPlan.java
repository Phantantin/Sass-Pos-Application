package com.tindev.domain;

/** A value of -1 means the plan does not impose a resource quota. */
public enum SubscriptionPlan {
    FREE(-1, -1, -1),
    BASIC(-1, -1, -1),
    PRO(-1, -1, -1);

    private final int branchLimit;
    private final int employeeLimit;
    private final int productLimit;

    SubscriptionPlan(int branchLimit, int employeeLimit, int productLimit) {
        this.branchLimit = branchLimit;
        this.employeeLimit = employeeLimit;
        this.productLimit = productLimit;
    }

    public int branchLimit() {
        return branchLimit;
    }

    public int employeeLimit() {
        return employeeLimit;
    }

    public int productLimit() {
        return productLimit;
    }
}
