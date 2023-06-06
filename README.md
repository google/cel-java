# Common Expression Language for Java

The Common Expression Language (CEL) is a non-Turing complete language designed
for simplicity, speed, safety, and portability. CEL's C-like [syntax][1] looks
nearly identical to equivalent expressions in C++, Go, Java, and TypeScript.

```java
// Check whether a resource name starts with a group name.
resource.name.startsWith("/groups/"+auth.claims.group)
```

```go
// Determine whether the request is in the permitted time window.
request.time - resource.age < duration("24h")
```

```typescript
// Check whether all resource names in a list match a given filter.
auth.claims.email_verified && resources.all(r, r.startsWith(auth.claims.email))
```

A CEL "program" is a single expression. The examples have been tagged as
`java`, `go`, and `typescript` within the markdown to showcase the commonality
of the syntax.

CEL is ideal for lightweight expression evaluation when a fully sandboxed
scripting language is too resource intensive.

---

* [Overview](#overview)
    * [Environment Setup](#environment-setup)
    * [Parse and Check](#parse-and-check)
    * [Macros](#macros)
    * [Evaluate](#evaluate)
    * [Errors](#Errors)
* [Install](#install)
* [Common Questions](#common-questions)
* [License](#license)

---

## Overview

Determine the variables and functions you want to provide to CEL. Parse and
check an expression to make sure it's valid. Then evaluate the output AST
against some input. Checking is optional, but strongly encouraged.

### Environment Setup

This section will be completed once parser and type-checker has been added.

### Parse and Check

Parsing and type-checking support are currently not available in Java but will
be added in the near future. In the interim, you may consider
leveraging [Go implementation of CEL][4]
to produce a type-checked expression to evaluate it in CEL-Java.

#### Macros

Macros are optional but enabled by default. Macros were introduced to support
optional CEL features that might not be desired in all use cases without the
syntactic burden and complexity such features might desire if they were part of
the core CEL syntax. Macros are expanded at parse time and their expansions are
type-checked at check time.

For example, when macros are enabled it is possible to support bounded iteration
/ fold operators. The macros `all`, `exists`, `exists_one`, `filter`, and `map`
are particularly useful for evaluating a single predicate against list and map
values.

```javascript
// Ensure all tweets are less than 140 chars
tweets.all(t, t.size() <= 140)
```

The `has` macro is useful for unifying field presence testing logic across
protobuf types and dynamic (JSON-like) types.

```javascript
// Test whether the field is a non-default value if proto-based, or defined
// in the JSON case.
has(message.field)
```

Both cases traditionally require special syntax at the language level, but these
features are exposed via macros in CEL.

### Evaluate

Now, evaluate for fun and profit. The evaluation is thread-safe and side-effect
free. Many different inputs can be sent to the same `cel.Program` and if fields
are present in the input, but not referenced in the expression, they are
ignored.

```java
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.runtime.CelRuntimeFactory;

CelRuntime celRuntime = CelRuntimeFactory.standardCelRuntimeBuilder().build();
CelAbstractSyntaxTree ast = CelProtoAbstractSyntaxTree.fromCheckedExpr(checkedExpr).getAst();

try {
  CelRuntime.Program program = celRuntime.createProgram(ast);
  Object evaluatedResult = program.eval(parameterValues);
} catch (CelEvaluationException e) {
  throw new IllegalArgumentException("Evaluation error has occurred.",e);
}
```

#### Partial State

In distributed apps it is not uncommon to have edge caches and central services.
If possible, evaluation should happen at the edge, but it isn't always possible
to know the full state required for all values and functions present in the CEL
expression.

To improve the odds of successful evaluation with partial state, CEL uses
commutative logical operators `&&`, `||`. If an error or unknown value (not the
same thing) is encountered on the left-hand side, the right-hand side is
evaluated also to determine the outcome. While it is possible to implement
evaluation with partial state without this feature, this method was chosen
because it aligns with the semantics of SQL evaluation and because it's more
robust to evaluation against dynamic data types such as JSON inputs.

In the following truth-table, the symbols `<x>` and `<y>` represent error or
unknown values, with the `?` indicating that the branch is not taken due to
short-circuiting. When the result is `<x, y>` this means that the both args are
possibly relevant to the result.

| Expression          | Result   |
|---------------------|----------|
| `false && ?`        | `false`  |
| `true && false`     | `false`  |
| `<x> && false`      | `false`  |
| `true && true`      | `true`   |
| `true && <x>`       | `<x>`    |
| `<x> && true`       | `<x>`    |
| `<x> && <y>`        | `<x, y>` |
| `true \|\| ?`       | `true`   |
| `false \|\| true`   | `true`   |
| `<x> \|\| true`     | `true`   |
| `false \|\| false`  | `false`  |
| `false \|\| <x>`    | `<x>`    |
| `<x> \|\| false`    | `<x>`    |
| `<x> \|\| <y>`      | `<x, y>` |

### Errors

This section will be completed once parser and type-checker has been added.

## Install

CEL-Java is available in Maven Central Repository. [Download the JARs here][6] or add the following to your build dependencies:

**Maven (pom.xml)**:

```xml
<dependency>
  <groupId>dev.cel</groupId>
  <artifactId>runtime</artifactId>
  <version>0.1.0</version>
</dependency>
```

**Gradle**

```gradle
implementation 'dev.cel:runtime:0.1.0'
```

Note: if you are already using `com.google.api.expr.v1alpha1` protobuf definitions, you also need to take `dev:cel:v1alpha1:0.1.0` as a dependency and leverage `CelProtoV1Alpha1AbstractSyntaxTree` class to convert your protobuf objects. Please note that v1alpha1 is now deprecated and new consumers should opt to use `dev.cel.expr` protos instead.

## Common Questions

### Why not JavaScript, Lua, or WASM?

JavaScript and Lua are rich languages that require sandboxing to execute safely.
Sandboxing is costly and factors into the "what will I let users evaluate?"
question heavily when the answer is anything more than O(n)
complexity.

CEL evaluates linearly with respect to the size of the expression and the input
being evaluated when macros are disabled. The only functions beyond the
built-ins that may be invoked are provided by the host environment. While
extension functions may be more complex, this is a choice by the application
embedding CEL.

But, why not WASM? WASM is an excellent choice for certain applications and is
far superior to embedded JavaScript and Lua, but it does not have support for
garbage collection and non-primitive object types require semi-expensive calls
across modules. In most cases CEL will be faster and just as portable for its
intended use case, though for node.js and web-based execution CEL too may offer
a WASM evaluator with direct to WASM compilation.

### Do I need to Parse _and_ Check?

Checking is an optional, but strongly suggested, step in CEL expression
validation. It is sufficient in some cases to simply Parse and rely on the
runtime bindings and error handling to do the right thing.

### Where can I learn more about the language?

* See the [CEL Spec][1] for the specification and conformance test suite.
* Ask for support on the [CEL Java Discuss][2] Google group.

### How can I contribute?

* See [CONTRIBUTING.md](./CONTRIBUTING.md) to get started.
* Use [GitHub Issues][4] to request features or report bugs.

### Dependencies

Java 8 or newer is required.

| Library               | Version | Details              |
|-----------------------|---------|----------------------|
| [Guava][2]            | 31.1    | N/A                  |
| [RE2/J][3]            | 1.7     | N/A                  |
| [Protocol Buffers][4] | 3.21.11 | Full or lite runtime |
| [ANTLR4][5]           | 4.11.1  | Java runtime         |

## License

Released under the [Apache License](LICENSE).

Disclaimer: This is not an official Google product.

[1]:  https://github.com/google/cel-spec

[2]:  https://groups.google.com/forum/#!forum/cel-java-discuss

[3]:  https://github.com/google/cel-cpp

[4]:  https://github.com/google/cel-go

[5]:  https://github.com/google/cel-java/issues

[6]:  https://search.maven.org/search?q=g:dev.cel