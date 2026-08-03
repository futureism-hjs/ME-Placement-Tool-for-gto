# Local build dependencies

Third-party Mod JARs are intentionally excluded from release source archives. Before building, place legally obtained copies of these files in this directory:

```text
appliedenergistics2-forge-1.20.1-15.267.4.jar
gtceu-forge-1.20.1-26.7.3.jar
ldlib-forge-1.20.1-1.0.50.jar
```

The target GregTech Odyssey instance must also provide GTOCore `0.5.6-beta` and its locked runtime dependencies. Use Java 21 for Gradle; the compiled Mod targets Java 17 bytecode.
