# Common Expression Language for Java

The Common Expression Language (CEL) is a non-Turing complete language designed
for simplicity, speed, safety, and portability. CEL's C-like [syntax][1] looks
nearly identical to equivalent expressions in C++, Go, Java, and TypeScript.

```java
// Check whether a resource name starts with a group name.
resource.name.startsWith("/groups/" + auth.claims.group)
```

```go
// Determine whether the request is in the permitted time window.
request.time - resource.age < duration("24h")
```

```typescript
// Check whether all resource names in a list match a given filter.
auth.claims.email_verified && resources.all(r, r.startsWith(auth.claims.email))
```

A CEL "program" is a single expression. The examples have been tagged as `java`,
`go`, and `typescript` within the markdown to showcase the commonality of the
syntax.

CEL is ideal for lightweight expression evaluation when a fully sandboxed
scripting language is too resource intensive.

## Overview

<!-- TODO: update once interface is settled -->

### Environment

<!-- TODO: update once interface is settled -->

Configuration for the entire CEL stack can be done all at once via the
`CelFactory.standardCelBuilder()`, or can be composed into compilation and
evaluation via the `CelCompilerFactory` and `CelRuntimeFactory`.

The simplest form of CEL usage is as follows:

```java
Cel cel = CelFactory.standardCelBuilder().build();
```

More commonly, your application will want to configure type-checking separately
from evaluation, and declare the types, macros, variables, and functions to use
with your CEL application:

```java
// Example environment for the following expression:
//    resource.name.startsWith('/groups/' + group)
CelCompiler cel = CelCompilerFactory.standardCelCompilerBuilder()
    .setStandardMacros(CelStandardMacro.HAS)
    .setContainer("google.rpc.context.AttributeContext")
    .addMessageTypes(AttributeContext.getDescriptor())
    .addVar("resource",
        StructTypeReference.create("google.rpc.context.AttributeContext.Resource"))
    .addVar("group", SimpleType.STRING)
    .build();
```

More information about the features which are supported on the builder may be
found in the [`CelCompilerBuilder`][6].

### Parsing

<!-- TODO: update once interface is settled -->

Some CEL use cases only require parsing of an expression in order to be useful.
For example, one example might want to check whether the expression contains any
nested comprehensions, or possibly to pass the parsed expression to a C++ or Go
binary for evaluation. Presently, Java does not support parse-only evaluation.

```java
CelValidationResult parseResult =
    cel.parse("resource.name.startsWith('/groups/' + group)");
try {
  return parseResult.getAst().toParsedExpr();
} catch (CelValidationException e) {
  // Handle exception...
}
```

### Checking

<!-- TODO: update once interface is settled -->

Type-checking is performed on `CelAbstractSyntaxTree` values to ensure that the
expression is well formed and all variable and function references are defined.

Type-checking can be performed immediately after parsing an expression:

```java
try {
  CelValidationResult parseResult =
      cel.parse("resource.name.startsWith('/groups/' + group)");
  CelValidationResult checkResult = cel.check(parseResult.getAst());
  return checkResult.getAst();
} catch (CelValidationException e) {
  // Handle exception...
}
```

Or, the parse and type-check can be combined into the `compile` call. This is
likely the more common need.

```java
CelValidationResult compileResult =
    cel.compile("resource.name.startsWith('/groups/' + group)");
try {
  return compileResult.getAst();
} catch (CelValidationException e) {
  // Handle exception...
}
```

### Evaluation

<!-- TODO: update once interface is settled -->

In Java, expressions can be evaluated using once they are type-checked/compiled
by creating a `CelRuntime.Program` from a `CelAbstractSyntaxTree`:

```java
CelRuntime celRuntime = CelRuntimeFactory.standardCelRuntimeBuilder().build();
try {
  CelRuntime.Program program = celRuntime.createProgram(compileResult.getAst());
  return program.eval(
      ImmutableMap.of(
          "resource", Resource.newBuilder().setName("/groups/").build(),
          "group", "admin"
      ));
} catch (CelException vx) {
  // Handle validation or evaluation exceptions ...
}

```

### Optimization

<!-- TODO: update once interface is settled -->

TBD

### Dependencies

Java 8 or newer is required.

Library               | Version | Details
--------------------- | ------- | --------------------
[Guava][2]            | TBD     | N/A
[RE2/J][3]            | TBD     | N/A
[Protocol Buffers][4] | TBD     | Full or lite runtime
[ANTLR4][5]           | TBD     | Java runtime

## License

<!-- TODO: update once interface is settled -->

Disclaimer: This is not an official Google product.

[1]:  https://github.com/google/cel-spec
[2]:  https://github.com/google/guava
[3]:  https://github.com/google/re2j
[4]:  https://github.com/protocolbuffers/protobuf/tree/master/java
[5]:  https://github.com/antlr/antlr4/tree/master/runtime/Java
[6]:  http://google3/third_party/java/cel/compiler/src/main/java/dev/cel/compiler/CelCompilerBuilder.java
