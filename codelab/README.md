# CEL-Java Codelab

In this codelab developers would learn some key concepts, how to set up, use variables, customize and build CEL for Java.

## What is CEL?

Common Expression Language (CEL) is an expression language that’s fast, portable, and safe to execute in performance-critical applications. CEL is designed to be embedded in an application, with application-specific extensions, and is ideal for extending declarative configurations that your applications might already use.

## What is covered in this Codelab?

In this codelab, we’ll walk through some coding Exercises that cover common use cases. This codelab is aimed at developers who would like to learn CEL to use services that already support CEL. This codelab doesn't cover how to integrate CEL into your own project. For a more in-depth look at the language, semantics, and features see the [CEL Language Definition on GitHub](https://github.com/google/cel-spec).

Some key areas covered are:

* [Hello, World: Using CEL to evaluate a String](#hello-world)
* [Creating variables](#creating-variables)
* [Commutatibe logical AND/OR](#logical-andor)
* [Adding custom functions](#custom-functions)
* [Building Protos](#building-protos)

### Prerequisites
This codelab builds upon a basic understanding of Protocol Buffers and Java.

If you're not familiar with Protocol Buffers, the first exercise will give you a sense of how CEL works, but because the more advanced examples use Protocol Buffers as the input into CEL, they may be harder to understand. Consider working through one of these [tutorials](https://developers.google.com/protocol-buffers/docs/tutorials?authuser=0), first.

Note that Protocol Buffers are not required to use CEL, but they are used extensively in this codelab.

What you'll need:

- JDK 8 or higher
- Bazel

You can test that java and bazel is installed by running:

```
java --version
bazel
```

## GitHub Setup

GitHub Repo:

The code for this codelab lives in the `codelab` folder of the cel-java repo. The solution is available in the `codelab/solution` folder of the same repo.

Clone and cd into the repo:

```
git clone git@github.com:google/cel-java.git
cd cel-java
```

Run the following:
```
bazel test //codelab/src/test/codelab:Exercise1Test --test_output=errors
```

You should see five failing tests in the output:

```
There were 5 failures:

… (omitted)

FAILURES!!!
Tests run: 5,  Failures: 5
```

## Where are CEL-Java Codelab Code in GitHub

Each exercise is laid out as `ExerciseN.java` and is accompanied by failing tests. Throughout this codelab, we will modify the main exercise code to make these tests pass.

- Codelab code: https://github.com/google/cel-java/tree/main/codelab/src/main/codelab
- Test code for the main codelab: https://github.com/google/cel-java/tree/main/codelab/src/test/codelab
- Codelab solution code: https://github.com/google/cel-java/tree/main/codelab/src/main/codelab/solutions
- Test code for the solution: https://github.com/google/cel-java/tree/main/codelab/src/test/codelab/solutions

We will also be using `google.rpc.context.AttributeContext` in [attribute_context.proto](https://github.com/googleapis/googleapis/blob/master/google/rpc/context/attribute_context.proto) to help with defining inputs for exercises.

## Hello, World

In the tried and true tradition of all programming languages, let's start with "Hello, World!".

Open `Exercise1.java`. You should see two stub methods:

```java
final class Exercise1 {
  /**
   * Compile the input {@code expression} and produce an AST. This method parses and type-checks the
   * given expression to validate the syntax and type-agreement of the expression.
   *
   * @throws IllegalArgumentException If the expression is malformed due to syntactic or semantic
   *     errors.
   */
  @SuppressWarnings("DoNotCallSuggester")
  CelAbstractSyntaxTree compile(String expression) {
    throw new UnsupportedOperationException("To be implemented");
  }

  /**
   * Evaluates the compiled AST.
   *
   * @throws IllegalArgumentException If the compiled expression in AST fails to evaluate.
   */
  @SuppressWarnings("DoNotCallSuggester")
  Object eval(CelAbstractSyntaxTree ast) {
    throw new UnsupportedOperationException("To be implemented");
  }
}
```

### Setup the Environment

CEL applications evaluate an expression against an Environment.

The standard CEL environment supports all of the types, operators, functions, and macros defined within the language spec. The environment can be customized by providing options to disable macros, declare custom variables and functions, etc.
Java compilation and evaluation environments are constructed by their factories.

```java
// Setup an environment for expression compilation and evaluation.

// Breaking behavior changes and optional features are controlled by CelOptions
private static final CelOptions CEL_OPTIONS = CelOptions.current().build();

// CelCompiler is used to parse and type-check expressions.
// Note, it is preferable to statically initialize the compiler when it is
// statically configured.
// All CelCompiler instances are immutable and thus trivially thread-safe and
// thus amenable to caching.
private static final CelCompiler CEL_COMPILER =
        CelCompilerFactory.standardCelCompilerBuilder()
             .setOptions(CEL_OPTIONS)
             .build();

// CelRuntime takes in a compiled expression and produces an evaluable instance.
// CelRuntime can also be initialized statically and cached just like the
// compiler.
private static final CelRuntime CEL_RUNTIME =
        CelRuntimeFactory.standardCelRuntimeBuilder()
             .setOptions(CEL_OPTIONS)
             .build();
```

Both `CelCompilerBuilder` and `CelRuntimeBuilder` allow customization through their fluent APIs.

### Parse and Check the Expression

Once the environment has been configured, expressions can be parsed and checked.

Copy the following into compile method:

```java
CelAbstractSyntaxTree compile(String expression) {
   // Construct a CelCompiler instance.
   // CelCompiler is immutable and when statically configured can be moved to a static final
   // member.
   CelCompiler celCompiler = CelCompilerFactory.standardCelCompilerBuilder().build();

   CelAbstractSyntaxTree ast;
   try {
        // Parse the expression
        ast = celCompiler.parse(expression).getAst();
   } catch (CelValidationException e) {
        // Report syntactic errors, if present
        throw new IllegalArgumentException(
            "Failed to parse expression. Reason: " + e.getMessage(), e);
   }

   return ast;
}
```

When the expression is syntactically valid, the result of parse call is an executable **CelAbstractSyntaxTree**. Any issues from a malformed expression are raised as an exception of type **CelValidationException**.

You can type-check the parsed AST by calling check. Append the following at the end of the compile method:

```java
try {
    // Type-check the expression for correctness
    ast = celCompiler.check(ast).getAst();
} catch (CelValidationException e) {
    // Report semantic errors, if present.
    throw new IllegalArgumentException(
         "Failed to type-check expression. Reason: " + e.getMessage(), e);
}
return ast;
```

Type-checking issues are also raised via **CelValidationException** similar to the parse call. **e.getMessage()** contains a debug string that renders syntactic and semantic error messages in a human-readable format.

In this exercise, we expect the evaluated expression to always produce a string output. We can leverage the type-checker to validate this.

Append `setResultType` method to the CelCompiler declaration:

```java
// Construct a CelCompiler instance.
// CelCompiler is immutable and when statically configured can be moved to a static final
// member.
// "String" is the expected output type for the type-checked expression
CelCompiler celCompiler =
   CelCompilerFactory.standardCelCompilerBuilder()
       .setResultType(SimpleType.STRING)
       .build();
```

Run the unit tests again. You should now see all compile tests pass:

```
bazel test --test_output=errors //codelab/src/test/codelab:Exercise1Test
```

CelCompiler offers a `Compile` method where parse and check phases are consolidated. This will be used throughout the remainder of the exercises.

### Evaluate

After the expressions have been parsed and checked into an AST representation, it can be converted into an evaluable program whose function bindings and evaluation modes can be customized depending on the stack you are using.

Once a CEL expression is planned, it can be evaluated against an evaluation context (an activation). The evaluation result will be either a value or an error state.

Copy the following into eval method:

```java
/**
 * Evaluates the compiled AST.
 *
 * @throws IllegalArgumentException If the compiled expression in AST fails to evaluate.
 */
Object eval(CelAbstractSyntaxTree ast) {
  // Construct a CelRuntime instance
  // CelRuntime is immutable just like the compiler and can be moved to a static final member.
  CelRuntime celRuntime = CelRuntimeFactory.standardCelRuntimeBuilder().build();

  try {
    // Plan the program
    CelRuntime.Program program = celRuntime.createProgram(ast);

    // Evaluate the program without any additional arguments.
    return program.eval();
  } catch (CelEvaluationException e) {
    // Report any evaluation errors, if present
    throw new IllegalArgumentException(
        "Evaluation error has occurred. Reason: " + e.getMessage(), e);
  }
}
```

**CelRuntime.Program** represents the planned expression and the method eval evaluates it against the input. Any evaluation errors are raised as an exception of type **CelEvaluationException**.

Run the unit tests again and everything should pass now.

## Creating variables

Most CEL applications will declare variables that can be referenced within expressions. Variables declarations specify a name and a type. A variable's type may either be a CEL builtin type, a protocol buffer well-known type, or any protobuf message type so long as its descriptor is also provided to CEL.

At runtime, the hosting program binds instances of variables to the evaluation context (using the variable name as a key).

Suppose you would like to test whether a user provided value is negative. We can write a simple expression for this: value < 0, where value is the variable to be declared.

Take a look at the evaluate_negativeExpression method in `Exercise2Test.java`:

```java
@Test
@TestParameters("{value: 5, expectedResult: false}")
@TestParameters("{value: -5, expectedResult: true}")
public void evaluate_negativeExpression(long value, boolean expectedResult) {
  CelAbstractSyntaxTree ast = exercise2.compile("value < 0", "value", SimpleType.INT);

  Object evaluatedResult = exercise2.eval(ast, ImmutableMap.of("value", value));
  assertThat(evaluatedResult).isEqualTo(expectedResult);
}
```

Let's write the code needed to pass this test. Copy the following into Exercise2.java:

```java
/**
 * Compiles the input expression with provided variable information.
 *
 * @throws IllegalArgumentException If the expression is malformed due to syntactic or semantic
 *     errors.
 */
CelAbstractSyntaxTree compile(String expression, String variableName, CelType variableType) {
  // Compile (parse + type-check) the expression
  // CelCompiler is immutable and when statically configured can be moved to a static final
  CelCompiler celCompiler =
      CelCompilerFactory.standardCelCompilerBuilder()
          .addVar(variableName, variableType)
          .addMessageTypes(AttributeContext.Request.getDescriptor())
          .setResultType(SimpleType.BOOL)
          .build();
  try {
    return celCompiler.compile(expression).getAst();
  } catch (CelValidationException e) {
    throw new IllegalArgumentException(
        "Failed to compile expression. Reason: " + e.getMessage(), e);
  }
}
```

The compiler's `addVar` method allows us to declare variables. Note that you must supply the type of the variable being declared. Supported CEL types can be found [here](https://github.com/google/cel-java/tree/main/common/src/main/java/dev/cel/common/types).

> [!TIP]
> Best practice: You may have noticed `addVar` has an overloaded method which accepts a proto based Type instead of the CEL-Java native CelType used in this example. While the two types are functionally equivalent, we recommend using the native types whenever possible.

Let's make the evaluation work now. Copy into the eval method:

```java
/**
 * Evaluates the compiled AST with the user provided parameter values.
 *
 * @throws IllegalArgumentException If the compiled expression in AST fails to evaluate.
 */
Object eval(CelAbstractSyntaxTree ast, Map<String, ?> parameterValues) {
  CelRuntime celRuntime = CelRuntimeFactory.standardCelRuntimeBuilder().build();
  try {
    CelRuntime.Program program = celRuntime.createProgram(ast);

    // Evaluate using the provided map as input variables (key: variableName, value:
    // variableValue)
    return program.eval(parameterValues);
  } catch (CelEvaluationException e) {
    // Report any evaluation errors, if present
    throw new IllegalArgumentException(
        "Evaluation error has occurred. Reason: " + e.getMessage(), e);
  }
}
```

Now run the unit tests. You should see evaluate_negativeExpressionTest pass:

```
bazel test --test_output=errors //codelab/src/test/codelab:Exercise2Test
```

You can also declare complex type variables such as a protocol buffer message. Suppose we would like to validate if an auth claim is set on a Request object.

Take a look at the test case `evaluate_requestAuthorization` in `Exercise2Test.java`:

```java
@Test
@TestParameters("{group: 'admin', expectedResult: true}")
@TestParameters("{group: 'users', expectedResult: false}")
public void evaluate_requestAuthorization(String group, boolean expectedResult) {
  CelAbstractSyntaxTree ast =
      exercise2.compile(
          "request.auth.claims.group == 'admin'",
          "request",
          StructTypeReference.create("google.rpc.context.AttributeContext.Request"));

  AttributeContext.Auth auth =
      AttributeContext.Auth.newBuilder()
          .setPrincipal("user:me@acme.co")
          .setClaims(
              Struct.newBuilder()
                  .putFields("group", Value.newBuilder().setStringValue(group).build()))
          .build();
  AttributeContext.Request request = AttributeContext.Request.newBuilder().setAuth(auth).build();

  Object evaluatedResult = exercise2.eval(ast, ImmutableMap.of("request", request));
  assertThat(evaluatedResult).isEqualTo(expectedResult);
}
```

You'll notice that for the declared variable name request, we are creating a type reference for Protocol buffer message by providing its fully qualified name `google.rpc.context.AttributeContext.Request`.

Run the unit test. You should see the following error:

> Failed to compile expression. Reason: ERROR: <input>:1:12: Message type resolution failure while referencing field 'auth'. Ensure that the descriptor for type 'google.rpc.context.AttributeContext.Request' was added to the environment | request.auth.claims.group == 'admin'

To use variables that refer to protobuf messages, the type-checker needs to also know the type descriptor. The type descriptor is a protobuf message that describes the field declarations of a protobuf message. Descriptors can be thought of as a reflection type. Descriptors are used within the type-checker to determine the field type references. Descriptors can either be stored and saved, or read from a protobuf message instance.

To add the needed descriptor, append `addMessageTypes` method to the `celCompiler` declaration:

```java
CelCompiler celCompiler =
   CelCompilerFactory.standardCelCompilerBuilder()
       .addVar(variableName, variableType)
       .addMessageTypes(AttributeContext.Request.getDescriptor())
       .setResultType(SimpleType.BOOL)
       .build();
```

Run the unit tests again. All test cases should pass now.

## Logical AND/OR

One of CEL's more unique features is its use of commutative logical operators. Either side of a conditional branch can short-circuit the evaluation, even in the face of errors or partial input.

We'll be fixing the tests to complete the expected truth table.

Run the tests. You should see failures for Logical AND and Ternary:

```
bazel test --test_output=errors //codelab/src/test/codelab:Exercise3Test
```

Take a look at the evaluate_logicalOrShortCircuits_success test in `Exercise3Test.java`.

```java
/**
 * Demonstrates CEL's unique feature of commutative logical operators.
 *
 * <p>If a logical operation can short-circuit a branch that results in an error, CEL evaluation
 * will return the logical result instead of propagating the error.
 */
@Test
@TestParameters("{expression: 'true || true', expectedResult: true}")
@TestParameters("{expression: 'true || false', expectedResult: true}")
@TestParameters("{expression: 'false || true', expectedResult: true}")
@TestParameters("{expression: 'false || false', expectedResult: false}")
@TestParameters("{expression: 'true || (1 / 0 > 2)', expectedResult: true}")
@TestParameters("{expression: '(1 / 0 > 2) || true', expectedResult: true}")
public void evaluate_logicalOrShortCircuits_success(String expression, boolean expectedResult) {
  Object evaluatedResult = exercise3.compileAndEvaluate(expression);

  assertThat(evaluatedResult).isEqualTo(expectedResult);
}
```

You'll note that despite the OR expression containing a branch where the evaluation results in an error, CEL still returns the expected result through short-circuiting.

Conversely, an exception is surfaced when all branches of the logical operator resolves to an error:

```java
/** Demonstrates a case where an error is surfaced to the user. */
@Test
@TestParameters("{expression: 'false || (1 / 0 > 2)'}")
@TestParameters("{expression: '(1 / 0 > 2) || false'}")
public void evaluate_logicalOrFailure_throwsException(String expression) {
  IllegalArgumentException exception =
      assertThrows(
          IllegalArgumentException.class, () -> exercise3.compileAndEvaluate(expression));

  assertThat(exception).hasMessageThat().contains("Evaluation error has occurred.");
  assertThat(exception).hasCauseThat().isInstanceOf(CelEvaluationException.class);
  assertThat(exception).hasCauseThat().hasMessageThat().contains("evaluation error: / by zero");
}
```

Using this knowledge, try to complete the truth table by filling in the expressions in @TestParameters for logicalAnd and ternary tests.

CEL finds an evaluation order which gives results whenever possible, ignoring errors or even missing data that might occur in other evaluation orders. Applications like IAM conditions rely on this property to minimize the cost of evaluation, deferring the gathering of expensive inputs when a result can be reached without them.

## Custom Functions

While there are many builtin functions, there are occasions where a custom function is useful:

- Improve the user experience for common conditions
- Expose context-sensitive state

In this exercise, we'll be exploring how to expose a function to package together commonly used checks. We'll introduce a '.contains' function for checking if a map has a key set and is set to a particular value, allowing for expressions like: `map.contains(key, value)`. This provides a generalization of this common pattern using built-ins for string maps: `has(map.key) && map.key == value`.

Take a look at the expression that has .contains function in `Exercise4Test.java`:

```java
@Test
@TestParameters("{group: 'admin', expectedResult: true}")
@TestParameters("{group: 'users', expectedResult: false}")
public void evaluate_requestContainsGroup_success(String group, boolean expectedResult) {
  CelAbstractSyntaxTree ast = exercise4.compile("request.auth.claims.contains('group', 'admin')");
  AttributeContext.Auth auth =
      AttributeContext.Auth.newBuilder()
          .setPrincipal("user:me@acme.co")
          .setClaims(
              Struct.newBuilder()
                  .putFields("group", Value.newBuilder().setStringValue(group).build()))
          .build();
  AttributeContext.Request request = AttributeContext.Request.newBuilder().setAuth(auth).build();

  Object evaluatedResult = exercise4.eval(ast, ImmutableMap.of("request", request));

  assertThat(evaluatedResult).isEqualTo(expectedResult);
}
```

We will build upon the provided compile and eval method implementations in Exercise4.java to make this work:

```java
final class Exercise4 {

/**
 * Compiles the input expression.
 *
 * @throws IllegalArgumentException If the expression is malformed due to syntactic or semantic
 *     errors.
 */
CelAbstractSyntaxTree compile(String expression) {
  CelCompiler celCompiler =
      CelCompilerFactory.standardCelCompilerBuilder()
          .addVar("request", StructTypeReference.create(Request.getDescriptor().getFullName()))
          .addMessageTypes(Request.getDescriptor())
          .setResultType(SimpleType.BOOL)
          // Provide the custom `contains` function declaration here.
          .build();

  try {
    return celCompiler.compile(expression).getAst();
  } catch (CelValidationException e) {
    throw new IllegalArgumentException("Failed to compile expression.", e);
  }
}

/**
 * Evaluates the compiled AST with the user provided parameter values.
 *
 * @throws IllegalArgumentException If the compiled expression in AST fails to evaluate.
 */
Object eval(CelAbstractSyntaxTree ast, Map<String, ?> parameterValues) {
  CelRuntime celRuntime =
      CelRuntimeFactory.standardCelRuntimeBuilder()
          // Provide the custom `contains` function implementation here.
          .build();

  try {
    CelRuntime.Program program = celRuntime.createProgram(ast);
    return program.eval(parameterValues);
  } catch (CelEvaluationException e) {
    throw new IllegalArgumentException("Evaluation error has occurred.", e);
  }
}
```

Run the test:

```
bazel test --test_output=errors //codelab/src/test/codelab:Exercise4Test
```

You should see the following error:

> ERROR: <input>:1:29: found no matching overload for 'contains' applied to 'map(string, dyn).(string, string)'
> | request.auth.claims.contains('group', 'admin')
> | ............................^

To fix the error, the contains function will need to be added to the list of declarations which currently declares the request variable. Declaring a function is not much different than declaring a variable. A function must indicate its common name and enumerate a set of overloads with unique signatures.

```java
The following snippet shows how to declare a parameterized type. This is the most complicated any function overload will ever be for CEL:
/**
 * Compiles the input expression.
 *
 * @throws IllegalArgumentException If the expression is malformed due to syntactic or semantic
 *     errors.
 */
CelAbstractSyntaxTree compile(String expression) {
  // Useful components of the type-signature for 'contains'.
  TypeParamType typeParamA = TypeParamType.create("A");
  TypeParamType typeParamB = TypeParamType.create("B");
  MapType mapTypeAB = MapType.create(typeParamA, typeParamB);

  CelCompiler celCompiler =
      CelCompilerFactory.standardCelCompilerBuilder()
          .addVar("request", StructTypeReference.create(Request.getDescriptor().getFullName()))
          .addMessageTypes(Request.getDescriptor())
          .setResultType(SimpleType.BOOL)
          .addFunctionDeclarations(
              newFunctionDeclaration(
                  "contains",
                  newMemberOverload(
                      "map_contains_key_value",
                      SimpleType.BOOL,
                      ImmutableList.of(mapTypeAB, typeParamA, typeParamB))))
          .build();

  try {
    return celCompiler.compile(expression).getAst();
  } catch (CelValidationException e) {
    throw new IllegalArgumentException("Failed to compile expression.", e);
  }
}
```

Re-run the exercise. Compilation should now succeed and you should see a new error about the missing runtime function:

> evaluation error at <input>:28: [internal] Unknown overload id 'map_contains_key_value' for function 'contains'

To fix this, we need to provide the CEL environment with a function implementation for `contains`.

Provide the function implementation to the runtime using the .addFunctionBindings builder method:

```java
/**
 * Evaluates the compiled AST with the user provided parameter values.
 *
 * @throws IllegalArgumentException If the compiled expression in AST fails to evaluate.
 */
Object eval(CelAbstractSyntaxTree ast, Map<String, ?> parameterValues) {
  CelRuntime celRuntime =
      CelRuntimeFactory.standardCelRuntimeBuilder()
          .addFunctionBindings(
              CelFunctionBinding.from(
                  "map_contains_key_value",
                  ImmutableList.of(Map.class, String.class, Object.class),
                  Exercise4::mapContainsKeyValue))
          .build();

  try {
    CelRuntime.Program program = celRuntime.createProgram(ast);
    return program.eval(parameterValues);
  } catch (CelEvaluationException e) {
    throw new IllegalArgumentException("Evaluation error has occurred.", e);
  }
}
```

A function binding requires an Overload implementation to be provided (the "actual Java code" to be executed). The method reference `Exercise4::mapContainskeyValue` is that implementation:

```java
/**
 * mapContainsKeyValue implements the custom function: map.contains(key, value) -> bool.
 *
 * @param args, where:
 *     <ol>
 *       <li>args[0] is the map
 *       <li>args[1] is the key
 *       <li>args[2] is the value at the key
 *     </ol>
 *
 * @return true If the key was found AND the value at the key equals to the value being checked
 */
@SuppressWarnings("unchecked") // Type-checker guarantees casting safety.
private static boolean mapContainsKeyValue(Object[] args) {
  // The declaration of the function ensures that only arguments which match
  // the mapContainsKey signature will be provided to the function.
  Map<String, Object> map = (Map<String, Object>) args[0];
  String key = (String) args[1];
  Object value = args[2];

  return map.containsKey(key) && map.containsValue(value);
}
```

> [!TIP]
> Best practice: Use `Unary<T>` or `Binary<T1, T2>` helper interfaces to improve compile-time correctness for any overload implementations with 2 arguments or fewer.

> [!TIP]
> Best practice: Declare overload ids according to their types and function names. e.g. targetType_func_argType_argType. In the case where argType is a type param, use a descriptive name instead of the simple type name.

## Building Protos

CEL can also build protobuf messages for any message type compiled into the application.