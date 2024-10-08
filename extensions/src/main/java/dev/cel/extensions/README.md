# Extensions

CEL extensions are a related set of constants, functions, macros, or other
features which may not be covered by the core CEL spec.

To use, supply the desired extensions into `CelCompiler` and `CelRuntime`
through `addLibrary` methods in their builder:

    CelCompilerFactory.standardCelCompilerBuilder().addLibraries(CelExtensions.strings())
    CelRuntimeFactory.standardCelRuntimeBuilder().addLibraries(CelExtensions.strings())

## Cel

The `cel` namespace is reserved for linguistic constructs which augment the
expressiveness of the language.

### Cel.Bind

Binds a simple identifier to an initialization expression which may be used in a
subsequent result expression. Bindings may also be nested within each other.

    cel.bind(<varName>, <initExpr>, <resultExpr>)

Examples:

    cel.bind(a, 'hello',
        cel.bind(b, 'world', a + b + b + a)) // "helloworldworldhello"

    // Avoid a list allocation within the exists comprehension.
    cel.bind(valid_values, [a, b, c],
        [d, e, f].exists(elem, elem in valid_values))

    cel.bind(sum, x + y + z, sum < 10 || sum > 20)

Local bindings are not guaranteed to be evaluated before use.

## Math

Math configures namespaced math helper macros and functions.

Note, all macros use the 'math' namespace; however, at the time of macro
expansion the namespace looks just like any other identifier. If you are
currently using a variable named 'math', the macro will likely work just as
intended; however, there is some chance for collision.

### Math.Greatest

Returns the greatest valued number present in the arguments to the macro.

Greatest is a variable argument count macro which must take at least one
argument. Simple numeric and list literals are supported as valid argument
types; however, other literals will be flagged as errors during macro
expansion. If the argument expression does not resolve to a numeric or
list(numeric) type during type-checking, or during runtime then an error
will be produced. If a list argument is empty, this too will produce an
error.

    math.greatest(<arg>, ...) -> <double|int|uint>

Examples:

    math.greatest(1)      // 1
    math.greatest(1u, 2u) // 2u
    math.greatest(-42.0, -21.5, -100.0)   // -21.5
    math.greatest([-42.0, -21.5, -100.0]) // -21.5
    math.greatest(numbers) // numbers must be list(numeric)

    math.greatest()         // parse error
    math.greatest('string') // parse error
    math.greatest(a, b)     // check-time error if a or b is non-numeric
    math.greatest(dyn('string')) // runtime error

### Math.Least

Returns the least valued number present in the arguments to the macro.

Least is a variable argument count macro which must take at least one
argument. Simple numeric and list literals are supported as valid argument
types; however, other literals will be flagged as errors during macro
expansion. If the argument expression does not resolve to a numeric or
list(numeric) type during type-checking, or during runtime then an error
will be produced. If a list argument is empty, this too will produce an error.

    math.least(<arg>, ...) -> <double|int|uint>

Examples:

    math.least(1)      // 1
    math.least(1u, 2u) // 1u
    math.least(-42.0, -21.5, -100.0)   // -100.0
    math.least([-42.0, -21.5, -100.0]) // -100.0
    math.least(numbers) // numbers must be list(numeric)

    math.least()         // parse error
    math.least('string') // parse error
    math.least(a, b)     // check-time error if a or b is non-numeric
    math.least(dyn('string')) // runtime error

## Protos

Extended macros and functions for proto manipulation.

Protos configures extended macros and functions for proto manipulation.

Note, all macros use the 'proto' namespace; however, at the time of macro
expansion the namespace looks just like any other identifier. If you are
currently using a variable named 'proto', the macro will likely work just as
you intend; however, there is some chance for collision.

### Proto.GetExt

Macro which generates a select expression that retrieves an extension field
from the input proto2 syntax message. If the field is not set, the default
value for the extension field is returned according to safe-traversal semantics.

    proto.getExt(<msg>, <fully.qualified.extension.name>) -> <field-type>

Example:

    proto.getExt(msg, google.expr.proto2.test.int32_ext) // returns int value

### Proto.HasExt

Macro which generates a test-only select expression that determines whether
an extension field is set on a proto2 syntax message.

    proto.hasExt(<msg>, <fully.qualified.extension.name>) -> <bool>

Example:

    proto.hasExt(msg, google.expr.proto2.test.int32_ext) // returns true || false

## Strings

Extended functions for string manipulation. As a general note, all indices are
zero-based.

### CharAt

Returns the character at the given position. If the position is negative, or greater than
the length of the string, the function will produce an error.

    <string>.charAt(<int>) -> <string>

Examples:

    'hello'.charAt(4)  // return 'o'
    'hello'.charAt(5)  // return ''
    'hello'.charAt(-1) // error

### IndexOf

Returns the integer index of the first occurrence of the search string. If the search string is
not found the function returns -1.

The function also accepts an optional offset from which to begin the substring search. If the
substring is the empty string, the index where the search starts is returned (zero or custom).

    <string>.indexOf(<string>) -> <int>
    <string>.indexOf(<string>, <int>) -> <int>

Examples:

    'hello mellow'.indexOf('')         // returns 0
    'hello mellow'.indexOf('ello')     // returns 1
    'hello mellow'.indexOf('jello')    // returns -1
    'hello mellow'.indexOf('', 2)      // returns 2
    'hello mellow'.indexOf('ello', 2)  // returns 7
    'hello mellow'.indexOf('ello', 20) // error

### Join

Returns a new string where the elements of string list are concatenated.

The function also accepts an optional separator which is placed between elements in the resulting string.

    <list<string>>.join() -> <string>
    <list<string>>.join(<string>) -> <string>

Examples:

    ['hello', 'mellow'].join() // returns 'hellomellow'
    ['hello', 'mellow'].join(' ') // returns 'hello mellow'
    [].join() // returns ''
    [].join('/') // returns ''

### LastIndexOf

Returns the integer index of the last occurrence of the search string. If the
search string is not found the function returns -1.

The function also accepts an optional offset which represents the last index
to be considered as the beginning of the substring match. If the substring is
the empty string, the index where the search starts is returned (string length
or custom).

    <string>.lastIndexOf(<string>) -> <int>
    <string>.lastIndexOf(<string>, <int>) -> <int>

Examples:

    'hello mellow'.lastIndexOf('')         // returns 12
    'hello mellow'.lastIndexOf('ello')     // returns 7
    'hello mellow'.lastIndexOf('jello')    // returns -1
    'hello mellow'.lastIndexOf('ello', 6)  // returns 1
    'hello mellow'.lastIndexOf('ello', -1) // error

### LowerAscii

Returns a new string where all ASCII characters are lower-cased.

This function does not perform Unicode case-mapping for characters outside the
ASCII range.

     <string>.lowerAscii() -> <string>

Examples:

     'TacoCat'.lowerAscii()      // returns 'tacocat'
     'TacoCÆt Xii'.lowerAscii()  // returns 'tacocÆt xii'

### Replace

Returns a new string based on the target, which replaces the occurrences of a
search string with a replacement string if present. The function accepts an
optional limit on the number of substring replacements to be made.

When the replacement limit is 0, the result is the original string. When the
limit is a negative number, the function behaves the same as replace all.

    <string>.replace(<string>, <string>) -> <string>
    <string>.replace(<string>, <string>, <int>) -> <string>

Examples:

    'hello hello'.replace('he', 'we')     // returns 'wello wello'
    'hello hello'.replace('he', 'we', -1) // returns 'wello wello'
    'hello hello'.replace('he', 'we', 1)  // returns 'wello hello'
    'hello hello'.replace('he', 'we', 0)  // returns 'hello hello'

### Split

Returns a mutable list of strings split from the input by the given separator. The
function accepts an optional argument specifying a limit on the number of
substrings produced by the split.

When the split limit is 0, the result is an empty list. When the limit is 1,
the result is the target string to split. When the limit is a negative
number, the function behaves the same as split all.

    <string>.split(<string>) -> <list<string>>
    <string>.split(<string>, <int>) -> <list<string>>

Examples:

    'hello hello hello'.split(' ')     // returns ['hello', 'hello', 'hello']
    'hello hello hello'.split(' ', 0)  // returns []
    'hello hello hello'.split(' ', 1)  // returns ['hello hello hello']
    'hello hello hello'.split(' ', 2)  // returns ['hello', 'hello hello']
    'hello hello hello'.split(' ', -1) // returns ['hello', 'hello', 'hello']

### Substring

Returns the substring given a numeric range corresponding to character
positions. Optionally may omit the trailing range for a substring from a given
character position until the end of a string.

Character offsets are 0-based with an inclusive start range and exclusive end
range. It is an error to specify an end range that is lower than the start
range, or for either the start or end index to be negative or exceed the string
length.

    <string>.substring(<int>) -> <string>
    <string>.substring(<int>, <int>) -> <string>

Examples:

    'tacocat'.substring(4)    // returns 'cat'
    'tacocat'.substring(0, 4) // returns 'taco'
    'tacocat'.substring(-1)   // error
    'tacocat'.substring(2, 1) // error

### Trim

Returns a new string which removes the leading and trailing whitespace in the
target string. The trim function uses the Unicode definition of whitespace
which does not include the zero-width spaces. See:
https://en.wikipedia.org/wiki/Whitespace_character#Unicode

    <string>.trim() -> <string>

Examples:

    '  \ttrim\n    '.trim() // returns 'trim'

### UpperAscii

Returns a new string where all ASCII characters are upper-cased.

This function does not perform Unicode case-mapping for characters outside the
ASCII range.

    <string>.upperAscii() -> <string>

Examples:

    'TacoCat'.upperAscii()      // returns 'TACOCAT'
    'TacoCÆt Xii'.upperAscii()  // returns 'TACOCÆT XII'

## Encoders

Encoding utilities for marshalling data into standardized representations.

### Base64.Decode

Decodes base64-encoded string to bytes.

This function will return an error if the string input is not
base64-encoded.

    base64.decode(<string>) -> <bytes>

Examples:

    base64.decode('aGVsbG8=')  // return b'hello'
    base64.decode('aGVsbG8')   // return b'hello'. Note that the padding
                               // character can be omitted.
    base64.decode('z!')        // error

### Base64.Encode

Encodes bytes to a base64-encoded string. Note that the string is encoded in
ISO_8859_1.

    base64.encode(<bytes>)  -> <string>

Example:

    base64.encode(b'hello') // return 'aGVsbG8='

## Sets

Sets provides set relationship tests.

There is no set type within CEL, and while one may be introduced in the future,
there are cases where a `list` type is known to behave like a set. For such
cases, this library provides some basic functionality for determining set
containment, equivalence, and intersection.

### Sets.Contains

Returns whether the first list argument contains all elements in the second list
argument. The list may contain elements of any type and standard CEL equality is
used to determine whether a value exists in both lists. If the second list is
empty, the result will always return true.

```
sets.contains(list(T), list(T)) -> bool
```

Examples:

```
sets.contains([], []) // true
sets.contains([], [1]) // false
sets.contains([1, 2, 3, 4], [2, 3]) // true
sets.contains([1, 2.0, 3u], [1.0, 2u, 3]) // true
```

### Sets.Equivalent

Returns whether the first and second list are set equivalent. Lists are set
equivalent if for every item in the first list, there is an element in the
second which is equal. The lists may not be of the same size as they do not
guarantee the elements within them are unique, so size does not factor into the
computation.

```
sets.equivalent(list(T), list(T)) -> bool
```

Examples:

```
sets.equivalent([], []) // true
sets.equivalent([1], [1, 1]) // true
sets.equivalent([1], [1u, 1.0]) // true
sets.equivalent([1, 2, 3], [3u, 2.0, 1]) // true
```

### Sets.Intersects

Returns whether the first list has at least one element whose value is equal to
an element in the second list. If either list is empty, the result will be
false.

```
sets.intersects(list(T), list(T)) -> bool
```

Examples:

```
sets.intersects([1], []) // false
sets.intersects([1], [1, 2]) // true
sets.intersects([[1], [2, 3]], [[1, 2], [2, 3.0]]) // true
```

## Lists

Extended functions for list manipulation. As a general note, all indices are
zero-based.

### Flatten

Flattens a list by one level, or to the specified level. Providing a negative level will error.

Examples:

```
// Single-level flatten:

[].flatten() // []
[1,[2,3],[4]].flatten() // [1, 2, 3, 4]
[1,[2,[3,4]]].flatten() // [1, 2, [3, 4]]
[1,2,[],[],[3,4]].flatten() // [1, 2, 3, 4]

// Recursive flatten
[1,[2,[3,[4]]]].flatten(2) // return [1, 2, 3, [4]]
[1,[2,[3,[4]]]].flatten(3) // return [1, 2, 3, 4]

// Error
[1,[2,[3,[4]]]].flatten(-1)
```

Note that due to the current limitations of type-checker, a compilation error
will occur if an already flat list is populated to the argument-less flatten
function.

For time being, you must explicitly provide 1 as the depth level, or wrap the
list in dyn if you anticipate having to deal with a flat list:

```
[1,2,3].flatten() // error

// But the following will work:
[1,2,3].flatten(1) // [1,2,3]
dyn([1,2,3]).flatten() // [1,2,3]
```

This will be addressed once we add the appropriate capabilities in the
type-checker to handle type-reductions, or union types.
