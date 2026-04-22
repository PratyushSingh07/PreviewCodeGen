# Preview Code Generator (KSP)

A Kotlin Symbol Processing (KSP) based code generator that produces:

1. Sample data factory functions for annotated model classes
2. Jetpack Compose preview functions for annotated composables

The goal is to reduce boilerplate when creating previews and mock data for UI development.

---

## Overview

During compilation, the processor scans for two annotations:

- `@Sample` applied to classes
- `@GeneratePreview` applied to composable functions

Based on these annotations, it generates Kotlin source files containing:

- Sample factory functions for each annotated class
- Preview composables that invoke the original composable with generated sample data

---

## Annotations

### `@Sample`

Marks a class for which a sample factory function should be generated.

```kotlin
@Sample
data class User(
    val name: String,
    val age: Int
)
```
Then annotate the composable with @GenereatePreview to get the corresponding preview file
```kotlin
@GeneratePreview
@Composable
fun UserCard(user: User) {
    Text(user.name)
}
```
---

## Processing Flow
- The processor retrieves all symbols annotated with:
  - @Sample (as KSClassDeclaration)
  - @GeneratePreview (as KSFunctionDeclaration)
- For @Sample classes:
  - Extract primary constructor parameters
  - Resolve parameter types
  - Generate default values per type
  - Build a factory function (sample<ClassName>())
- For @GeneratePreview functions:
  - Inspect parameters
  - Determine if a parameter requires a sample instance
  - Generate argument expressions
  - Create a preview function invoking the original composable
- Write generated files to the configured output directory (hardcoded for now)
