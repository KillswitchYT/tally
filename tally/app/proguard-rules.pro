# Rules for the optimized build, which is the real app. Compose and the backdrop library bring their own consumer rules.
# Resources are looked up by name for the built-in alarm sounds, and resource shrinking is off, so nothing else needs keeping.
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**

# A crash trace must stay readable: keep the file names and line numbers. CI also publishes mapping.txt with every release, so the
# renamed classes can be turned back into real names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
