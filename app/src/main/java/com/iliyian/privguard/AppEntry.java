package com.iliyian.privguard;

public final class AppEntry {
    public final String packageName;
    public final String label;
    public final boolean system;

    public AppEntry(String packageName, String label, boolean system) {
        this.packageName = packageName;
        this.label = label;
        this.system = system;
    }
}
