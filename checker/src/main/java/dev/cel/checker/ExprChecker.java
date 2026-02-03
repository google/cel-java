// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cel.checker;

import static com.google.common.base.Preconditions.checkNotNull;

import dev.cel.expr.CheckedExpr;
import dev.cel.expr.ParsedExpr;
import dev.cel.expr.Type;
import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelContainer;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelProtoAbstractSyntaxTree;
import dev.cel.common.Operator;
import dev.cel.common.annotations.Internal;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelReference;
import dev.cel.common.types.CelKind;
import dev.cel.common.types.CelProtoTypes;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.CelTypes;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.ProtoMessageType;
import dev.cel.common.types.ProtoMessageType.Extension;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructType;
import dev.cel.common.types.StructType.Field;
import dev.cel.common.types.TypeType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The expression type checker.
 *
 * <p>CEL-Java library internals. Do not use.
 *
 * @deprecated Please migrate to CEL-Java Fluent APIs instead. See {@code CelCompilerFactory}.
 */
@Internal
@Deprecated
public final class ExprChecker {

  /**
   * Deprecated type-check API.
   *
   * @deprecated Do not use. CEL-Java users should leverage the Fluent APIs instead. See {@code
   *     CelCompilerFactory}.
   */
  @CheckReturnValue
  @Deprecated
  public static CheckedExpr check(Env env, String inContainer, ParsedExpr parsedExpr) {
    return typecheck(env, inContainer, parsedExpr, Optional.absent());
  }

  /**
   * Deprecated type-check API.
   *
   * @deprecated Do not use. CEL-Java users should leverage the Fluent APIs instead. See {@code
   *     CelCompilerFactory}.
   */
  @CheckReturnValue
  @Deprecated
  public static CheckedExpr typecheck(
      Env env, String inContainer, ParsedExpr parsedExpr, Optional<Type> expectedResultType) {
    Optional<CelType> type =
        expectedResultType.isPresent()
            ? Optional.of(CelProtoTypes.typeToCelType(expectedResultType.get()))
            : Optional.absent();
    CelAbstractSyntaxTree ast =
        typecheck(
            env,
            CelContainer.ofName(inContainer),
            CelProtoAbstractSyntaxTree.fromParsedExpr(parsedExpr).getAst(),
            type);

    if (ast.isChecked()) {
      return CelProtoAbstractSyntaxTree.fromCelAst(ast).toCheckedExpr();
    }

    return CheckedExpr.newBuilder()
        .setExpr(parsedExpr.getExpr())
        .setSourceInfo(parsedExpr.getSourceInfo())
        .build();
  }

  /**
   * Type-checks the parsed expression within the given environment and returns a checked
   * expression. If an expected result type was given, then it verifies that that type matches the
   * actual result type. Conditions for type checking and the constructed {@link CheckedExpr} are
   * described in checked.proto.
   *
   * <p>CEL Library Internals. Do not use. CEL-Java users should use the Fluent APIs instead.
   */
  @CheckReturnValue
  @Internal
  public static CelAbstractSyntaxTree typecheck(
      Env env,
      CelContainer container,
      CelAbstractSyntaxTree ast,
      Optional<CelType> expectedResultType) {
    env.resetTypeAndRefMaps();
    final ExprChecker checker =
        new ExprChecker(
            env,
            container,
            ast.getSource().getPositionsMap(),
            new InferenceContext(),
            env.enableCompileTimeOverloadResolution(),
            env.enableHomogeneousLiterals(),
            env.enableNamespacedDeclarations());
    CelExpr expr = checker.visit(ast.getExpr());
    if (expectedResultType.isPresent()) {
      checker.assertType(expr, expectedResultType.get());
    }
    // Walk over the final type map substituting any type parameters either by their bound value or
    // by DYN.
    Map<Long, CelType> typeMap =
        Maps.transformValues(env.getTypeMap(), checker.inferenceContext::finalize);

    return CelAbstractSyntaxTree.newCheckedAst(expr, ast.getSource(), env.getRefMap(), typeMap);
  }

  private final Env env;
  private final CelTypeProvider typeProvider;
  private final CelContainer container;
  private final Map<Long, Integer> positionMap;
  private final InferenceContext inferenceContext;
  private final boolean compileTimeOverloadResolution;
  private final boolean homogeneousLiterals;
  private final boolean namespacedDeclarations;

  private ExprChecker(
      Env env,
      CelContainer container,
      Map<Long, Integer> positionMap,
      InferenceContext inferenceContext,
      boolean compileTimeOverloadResolution,
      boolean homogeneousLiterals,
      boolean namespacedDeclarations) {
    this.env = checkNotNull(env);
    this.typeProvider = env.getTypeProvider();
    this.positionMap = checkNotNull(positionMap);
    this.container = checkNotNull(container);
    this.inferenceContext = checkNotNull(inferenceContext);
    this.compileTimeOverloadResolution = compileTimeOverloadResolution;
    this.homogeneousLiterals = homogeneousLiterals;
    this.namespacedDeclarations = namespacedDeclarations;
  }

  /** Visit the {@code expr} value, routing to overloads based on the kind of expression. */
  @CheckReturnValue
  public CelExpr visit(CelExpr expr) {
    switch (expr.exprKind().getKind()) {
      case CONSTANT:
        return visit(expr, expr.constant());
      case IDENT:
        return visit(expr, expr.ident());
      case SELECT:
        return visit(expr, expr.select());
      case CALL:
        return visit(expr, expr.call());
      case LIST:
        return visit(expr, expr.list());
      case STRUCT:
        return visit(expr, expr.struct());
      case MAP:
        return visit(expr, expr.map());
      case COMPREHENSION:
        return visit(expr, expr.comprehension());
      default:
        throw new IllegalArgumentException("unexpected expr kind");
    }
  }

  @CheckReturnValue
  private CelExpr visit(CelExpr expr, CelConstant constant) {
    switch (constant.getKind()) {
      case INT64_VALUE:
        env.setType(expr, SimpleType.INT);
        break;
      case UINT64_VALUE:
        env.setType(expr, SimpleType.UINT);
        break;
      case STRING_VALUE:
        env.setType(expr, SimpleType.STRING);
        break;
      case BYTES_VALUE:
        env.setType(expr, SimpleType.BYTES);
        break;
      case BOOLEAN_VALUE:
        env.setType(expr, SimpleType.BOOL);
        break;
      case NULL_VALUE:
        env.setType(expr, SimpleType.NULL_TYPE);
        break;
      case DOUBLE_VALUE:
        env.setType(expr, SimpleType.DOUBLE);
        break;
      case TIMESTAMP_VALUE:
        env.setType(expr, SimpleType.TIMESTAMP);
        break;
      case DURATION_VALUE:
        env.setType(expr, SimpleType.DURATION);
        break;
      default:
        throw new IllegalArgumentException("unexpected constant case: " + constant.getKind());
    }
    return expr;
  }

  @CheckReturnValue
  private CelExpr visit(CelExpr expr, CelExpr.CelIdent ident) {
    CelIdentDecl decl = env.lookupIdent(expr.id(), getPosition(expr), container, ident.name());
    checkNotNull(decl);
    if (decl.equals(Env.ERROR_IDENT_DECL)) {
      // error reported
      env.setType(expr, SimpleType.ERROR);
      env.setRef(expr, makeReference(decl.name(), decl));
      return expr;
    }

    String refName = maybeDisambiguate(ident.name(), decl.name());

    if (!refName.equals(ident.name())) {
      // Overwrite the identifier with its fully qualified name.
      expr = replaceIdentSubtree(expr, refName);
    }
    env.setType(expr, decl.type());
    env.setRef(expr, makeReference(refName, decl));
    return expr;
  }

  @CheckReturnValue
  private CelExpr visit(CelExpr expr, CelExpr.CelSelect select) {
    // Before traversing down the tree, try to interpret as qualified name.
    String qname = asQualifiedName(expr);

    if (qname != null) {
      CelIdentDecl decl = env.tryLookupCelIdent(container, qname);
      if (decl != null) {
        if (select.testOnly()) {
          env.reportError(expr.id(), getPosition(expr), "expression does not select a field");
          env.setType(expr, SimpleType.BOOL);
        } else {
          String refName = maybeDisambiguate(qname, decl.name());

          if (namespacedDeclarations) {
            // Rewrite the node to be a variable reference to the resolved fully-qualified
            // variable name.
            expr = replaceIdentSubtree(expr, refName);
          }
          env.setType(expr, decl.type());
          env.setRef(expr, makeReference(refName, decl));
        }
        return expr;
      }
    }
    // Interpret as field selection, first traversing down the operand.
    CelExpr visitedOperand = visit(select.operand());
    if (namespacedDeclarations && !select.operand().equals(visitedOperand)) {
      // Subtree has been rewritten. Replace the operand.
      expr = replaceSelectOperandSubtree(expr, visitedOperand);
    }
    CelType resultType = visitSelectField(expr, visitedOperand, select.field(), false);

    if (select.testOnly()) {
      resultType = SimpleType.BOOL;
    }
    env.setType(expr, resultType);
    return expr;
  }

  @CheckReturnValue
  private CelExpr visit(CelExpr expr, CelExpr.CelCall call) {
    String functionName = call.function();
    if (Operator.OPTIONAL_SELECT.getFunction().equals(functionName)) {
      return visitOptionalCall(expr, call);
    }
    // Traverse arguments.
    ImmutableList<CelExpr> argsList = call.args();
    for (int i = 0; i < argsList.size(); i++) {
      CelExpr arg = argsList.get(i);
      CelExpr visitedArg = visit(arg);
      if (namespacedDeclarations && !visitedArg.equals(arg)) {
        // Argument has been overwritten.
        expr = replaceCallArgumentSubtree(expr, visitedArg, i);
      }
    }

    int position = getPosition(expr);
    OverloadResolution resolution;

    if (!call.target().isPresent()) {
      // Regular static call with simple name.
      CelFunctionDecl decl = env.lookupFunction(expr.id(), position, container, call.function());
      resolution = resolveOverload(expr.id(), position, decl, null, call.args());

      if (!decl.name().equals(call.function())) {
        if (namespacedDeclarations) {
          // Overwrite the function name with its fully qualified resolved name.
          expr = replaceCallSubtree(expr, decl.name());
        }
      }
    } else {
      // Check whether the target is actually a qualified name for a static function.
      String qualifiedName = asQualifiedName(call.target().get());
      CelFunctionDecl decl =
          env.tryLookupCelFunction(container, qualifiedName + "." + call.function());
      if (decl != null) {
        resolution = resolveOverload(expr.id(), position, decl, null, call.args());

        if (namespacedDeclarations) {
          // The function name is namespaced and so preserving the target operand would
          // be an inaccurate representation of the desired evaluation behavior.
          // Overwrite with fully-qualified resolved function name sans receiver target.
          expr = replaceCallSubtree(expr, decl.name());
        }
      } else {
        // Regular instance call.
        CelExpr target = call.target().get();
        CelExpr visitedTargetExpr = visit(target);
        if (namespacedDeclarations && !visitedTargetExpr.equals(target)) {
          // Visiting target contained a namespaced function. Rewrite the call expression here by
          // setting the target to the new subtree.
          expr = replaceCallSubtree(expr, visitedTargetExpr);
        }
        resolution =
            resolveOverload(
                expr.id(),
                position,
                env.lookupFunction(expr.id(), getPosition(expr), container, call.function()),
                target,
                call.args());
      }
    }

    env.setType(expr, resolution.type());
    env.setRef(expr, resolution.reference());

    return expr;
  }

  @CheckReturnValue
  private CelExpr visit(CelExpr expr, CelExpr.CelStruct struct) {
    // Determine the type of the message.
    CelType messageType = SimpleType.ERROR;
    CelIdentDecl decl =
        env.lookupIdent(expr.id(), getPosition(expr), container, struct.messageName());
    if (!struct.messageName().equals(decl.name())) {
      expr =
          expr.toBuilder()
              .setStruct(struct.toBuilder().setMessageName(decl.name()).build())
              .build();
    }

    env.setRef(expr, CelReference.newBuilder().setName(decl.name()).build());
    CelType type = decl.type();
    if (type.kind() != CelKind.ERROR) {
      if (type.kind() != CelKind.TYPE) {
        // expected type of types
        env.reportError(expr.id(), getPosition(expr), "'%s' is not a type", CelTypes.format(type));
      } else {
        messageType = ((TypeType) type).type();
        if (messageType.kind() != CelKind.STRUCT) {
          env.reportError(
              expr.id(),
              getPosition(expr),
              "'%s' is not a message type",
              CelTypes.format(messageType));
          messageType = SimpleType.ERROR;
        }
      }
    }

    // When the type is well-known mark the expression with the CEL type rather than the proto type.
    if (Env.isWellKnownType(messageType)) {
      env.setType(expr, Env.getWellKnownType(messageType));
    } else {
      env.setType(expr, messageType);
    }

    // Check the field initializers.
    ImmutableList<CelExpr.CelStruct.Entry> entriesList = struct.entries();
    for (int i = 0; i < entriesList.size(); i++) {
      CelExpr.CelStruct.Entry entry = entriesList.get(i);
      CelExpr visitedValueExpr = visit(entry.value());
      if (namespacedDeclarations && !visitedValueExpr.equals(entry.value())) {
        // Subtree has been rewritten. Replace the struct value.
        expr = replaceStructEntryValueSubtree(expr, visitedValueExpr, i);
      }
      CelType fieldType =
          getFieldType(entry.id(), getPosition(entry), messageType, entry.fieldKey()).type();
      CelType valueType = env.getType(visitedValueExpr);
      if (entry.optionalEntry()) {
        if (valueType instanceof OptionalType) {
          valueType = unwrapOptional(valueType);
        } else {
          assertIsAssignable(
              visitedValueExpr.id(),
              getPosition(visitedValueExpr),
              valueType,
              OptionalType.create(valueType));
        }
      }
      if (!inferenceContext.isAssignable(fieldType, valueType)) {
        env.reportError(
            expr.id(),
            getPosition(entry),
            "expected type of field '%s' is '%s' but provided type is '%s'",
            entry.fieldKey(),
            CelTypes.format(fieldType),
            CelTypes.format(valueType));
      }
    }
    return expr;
  }

  @CheckReturnValue
  private CelExpr visit(CelExpr expr, CelExpr.CelMap map) {
    CelType mapKeyType = null;
    CelType mapValueType = null;
    ImmutableList<CelExpr.CelMap.Entry> entriesList = map.entries();
    for (int i = 0; i < entriesList.size(); i++) {
      CelExpr.CelMap.Entry entry = entriesList.get(i);
      CelExpr visitedMapKeyExpr = visit(entry.key());
      if (namespacedDeclarations && !visitedMapKeyExpr.equals(entry.key())) {
        // Subtree has been rewritten. Replace the map key.
        expr = replaceMapEntryKeySubtree(expr, visitedMapKeyExpr, i);
      }
      mapKeyType =
          joinTypes(
              visitedMapKeyExpr.id(),
              getPosition(visitedMapKeyExpr),
              mapKeyType,
              env.getType(visitedMapKeyExpr));

      CelExpr visitedValueExpr = visit(entry.value());
      if (namespacedDeclarations && !visitedValueExpr.equals(entry.value())) {
        // Subtree has been rewritten. Replace the map value.
        expr = replaceMapEntryValueSubtree(expr, visitedValueExpr, i);
      }
      CelType valueType = env.getType(visitedValueExpr);
      if (entry.optionalEntry()) {
        if (valueType instanceof OptionalType) {
          valueType = unwrapOptional(valueType);
        } else {
          assertIsAssignable(
              visitedValueExpr.id(),
              getPosition(visitedValueExpr),
              valueType,
              OptionalType.create(valueType));
        }
      }

      mapValueType =
          joinTypes(visitedValueExpr.id(), getPosition(visitedValueExpr), mapValueType, valueType);
    }
    if (mapKeyType == null) {
      // If the map is empty, assign free type variables to key and value type.
      mapKeyType = inferenceContext.newTypeVar("key");
      mapValueType = inferenceContext.newTypeVar("value");
    }
    env.setType(expr, MapType.create(mapKeyType, mapValueType));
    return expr;
  }

  @CheckReturnValue
  private CelExpr visit(CelExpr expr, CelExpr.CelList list) {
    CelType elemsType = null;
    ImmutableList<CelExpr> elementsList = list.elements();
    HashSet<Integer> optionalIndices = new HashSet<>(list.optionalIndices());
    for (int i = 0; i < elementsList.size(); i++) {
      CelExpr visitedElem = visit(elementsList.get(i));
      if (namespacedDeclarations && !visitedElem.equals(elementsList.get(i))) {
        // Subtree has been rewritten. Replace the list element
        expr = replaceListElementSubtree(expr, visitedElem, i);
      }
      CelType elemType = env.getType(visitedElem);
      if (optionalIndices.contains(i)) {
        if (elemType instanceof OptionalType) {
          elemType = unwrapOptional(elemType);
        } else {
          assertIsAssignable(
              visitedElem.id(), getPosition(visitedElem), elemType, OptionalType.create(elemType));
        }
      }

      elemsType = joinTypes(visitedElem.id(), getPosition(visitedElem), elemsType, elemType);
    }
    if (elemsType == null) {
      // If the list is empty, assign free type var to elem type.
      elemsType = inferenceContext.newTypeVar("elem");
    }
    env.setType(expr, ListType.create(elemsType));
    return expr;
  }

  @CheckReturnValue
  private CelExpr visit(CelExpr expr, CelExpr.CelComprehension compre) {
    CelExpr visitedRange = visit(compre.iterRange());
    CelExpr visitedInit = visit(compre.accuInit());
    CelType accuType = env.getType(visitedInit);
    CelType rangeType = inferenceContext.specialize(env.getType(visitedRange));
    CelType varType;
    CelType varType2 = null;
    switch (rangeType.kind()) {
      case LIST:
        varType = ((ListType) rangeType).elemType();
        if (!Strings.isNullOrEmpty(compre.iterVar2())) {
          varType2 = varType;
          varType = SimpleType.INT;
        }
        break;
      case MAP:
        // Ranges over the keys.
        varType = ((MapType) rangeType).keyType();
        if (!Strings.isNullOrEmpty(compre.iterVar2())) {
          varType2 = ((MapType) rangeType).valueType();
        }
        break;
      case DYN:
      case ERROR:
        varType = SimpleType.DYN;
        varType2 = SimpleType.DYN;
        break;
      case TYPE_PARAM:
        // Mark the range as DYN to avoid its free variable being associated with the wrong type
        // based on an earlier or later use. The isAssignable call will ensure that type
        // substitutions are updated for the type param.
        inferenceContext.isAssignable(SimpleType.DYN, rangeType);
        // Mark the variable type as DYN.
        varType = SimpleType.DYN;
        varType2 = SimpleType.DYN;
        break;
      default:
        env.reportError(
            expr.id(),
            getPosition(visitedRange),
            "expression of type '%s' cannot be range of a comprehension "
                + "(must be list, map, or dynamic)",
            CelTypes.format(rangeType));
        varType = SimpleType.DYN;
        varType2 = SimpleType.DYN;
        break;
    }

    // Declare accumulation variable on outer scope.
    env.enterScope();
    env.add(CelIdentDecl.newIdentDeclaration(compre.accuVar(), accuType));
    // Declare iteration variable on inner scope.
    env.enterScope();
    env.add(CelIdentDecl.newIdentDeclaration(compre.iterVar(), varType));
    if (!Strings.isNullOrEmpty(compre.iterVar2())) {
      env.add(CelIdentDecl.newIdentDeclaration(compre.iterVar2(), varType2));
    }
    CelExpr condition = visit(compre.loopCondition());
    assertType(condition, SimpleType.BOOL);
    CelExpr visitedStep = visit(compre.loopStep());
    assertType(visitedStep, accuType);
    // Forget iteration variable, as result expression must only depend on accu.
    env.exitScope();
    CelExpr visitedResult = visit(compre.result());
    env.exitScope();
    if (namespacedDeclarations) {
      if (!visitedRange.equals(compre.iterRange())) {
        expr = replaceComprehensionRangeSubtree(expr, visitedRange);
      }
      if (!visitedInit.equals(compre.accuInit())) {
        expr = replaceComprehensionAccuInitSubtree(expr, visitedInit);
      }
      if (!visitedStep.equals(compre.loopStep())) {
        expr = replaceComprehensionStepSubtree(expr, visitedStep);
      }
      if (!visitedResult.equals(compre.result())) {
        expr = replaceComprehensionResultSubtree(expr, visitedResult);
      }
    }
    env.setType(expr, inferenceContext.specialize(env.getType(visitedResult)));
    return expr;
  }

  private CelReference makeReference(String name, CelIdentDecl decl) {
    CelReference.Builder ref = CelReference.newBuilder().setName(name);
    if (decl.constant().isPresent()) {
      ref.setValue(decl.constant().get());
    }
    return ref.build();
  }

  /**
   * Returns the reference name, prefixed with a leading dot only if disambiguation is needed.
   * Disambiguation is needed when: the original name had a leading dot, and there's a local
   * variable that would shadow the resolved name.
   */
  private String maybeDisambiguate(String originalName, String resolvedName) {
    if (!originalName.startsWith(".")) {
      return resolvedName;
    }
    String simpleName = originalName.substring(1);
    int dotIndex = simpleName.indexOf('.');
    String localName = dotIndex > 0 ? simpleName.substring(0, dotIndex) : simpleName;
    if (env.tryLookupCelIdentFromLocalScopes(localName) != null) {
      return "." + resolvedName;
    }
    return resolvedName;
  }

  private OverloadResolution resolveOverload(
      long callExprId,
      int position,
      @Nullable CelFunctionDecl function,
      @Nullable CelExpr target,
      List<CelExpr> args) {
    if (function == null || function.equals(Env.ERROR_FUNCTION_DECL)) {
      // Error reported, just return error value.
      return OverloadResolution.of(CelReference.newBuilder().build(), SimpleType.ERROR);
    }
    List<CelType> argTypes = new ArrayList<>();
    if (target != null) {
      argTypes.add(env.getType(target));
    }
    for (CelExpr arg : args) {
      argTypes.add(env.getType(arg));
    }
    CelType resultType = null; // For most common result type.
    String firstCandString = null;
    CelReference.Builder refBuilder = CelReference.newBuilder();
    List<String> excludedCands = new ArrayList<>();
    String expectedString =
        TypeFormatter.formatFunction(inferenceContext.specialize(argTypes), target != null);
    for (CelOverloadDecl overload : function.overloads()) {
      boolean isInstance = overload.isInstanceFunction();
      if ((target == null && isInstance) || (target != null && !isInstance)) {
        // not a compatible call style.
        continue;
      }
      CelType overloadType =
          CelTypes.createFunctionType(overload.resultType(), overload.parameterTypes());
      if (!overload.typeParameterNames().isEmpty()) {
        // Instantiate overload's type with fresh type variables.
        overloadType = inferenceContext.newInstance(overload.typeParameterNames(), overloadType);
      }
      ImmutableList<CelType> candArgTypes =
          overloadType.parameters().subList(1, overloadType.parameters().size());
      String candString =
          TypeFormatter.formatFunction(inferenceContext.specialize(candArgTypes), target != null);
      if (inferenceContext.isAssignable(argTypes, candArgTypes)) {
        // Collect overload id.
        refBuilder.addOverloadIds(overload.overloadId());
        if (resultType == null) {
          // First matching overload, determines result type.
          resultType = inferenceContext.specialize(overloadType.parameters().get(0));
          firstCandString = candString;
        } else {
          // More than one matching overload in non-strict mode, narrow result type to DYN unless
          // the overload type matches the previous result type.
          CelType fnResultType = inferenceContext.specialize(overloadType).parameters().get(0);
          if (!Types.isDyn(resultType) && !resultType.equals(fnResultType)) {
            // TODO: Consider joining result types of successful candidates when the
            // types are assignable, but not the same. Note, type assignability checks here seem to
            // mutate the type substitutions list in unexpected ways that result in errant results.
            resultType = SimpleType.DYN;
          }
          if (compileTimeOverloadResolution) {
            // In compile-time overload resolution mode report this situation as an error.
            env.reportError(
                callExprId,
                position,
                "found more than one matching overload for '%s' applied to '%s': %s and also %s",
                function.name(),
                expectedString,
                firstCandString,
                candString);
          }
        }
      } else {
        excludedCands.add(candString);
      }
    }
    if (resultType == null) {
      env.reportError(
          callExprId,
          position,
          "found no matching overload for '%s' applied to '%s'%s",
          function.name(),
          expectedString,
          excludedCands.isEmpty()
              ? ""
              : " (candidates: " + Joiner.on(',').join(excludedCands) + ")");
      resultType = SimpleType.ERROR;
    }
    return OverloadResolution.of(refBuilder.build(), resultType);
  }

  // Return value from visit is not needed as the subtree is not rewritten here.
  @SuppressWarnings("CheckReturnValue")
  private CelType visitSelectField(
      CelExpr expr, CelExpr operand, String fieldName, boolean isOptional) {
    CelType operandType = inferenceContext.specialize(env.getType(operand));
    CelType resultType = SimpleType.ERROR;

    if (operandType instanceof OptionalType) {
      isOptional = true;
      operandType = unwrapOptional(operandType);
    }

    if (!Types.isDynOrError(operandType)) {
      if (operandType.kind() == CelKind.STRUCT) {
        Field field = getFieldType(expr.id(), getPosition(expr), operandType, fieldName);
        resultType = field.type();
      } else if (operandType.kind() == CelKind.MAP) {
        resultType = ((MapType) operandType).valueType();
      } else if (operandType.kind() == CelKind.TYPE_PARAM) {
        // Mark the operand as type DYN to avoid cases where the free type variable might take on
        // an incorrect type if used in multiple locations.
        //
        // The assignability test will update the type substitutions for the type parameter with the
        // type DYN. This ensures that the operand type is appropriately flagged as DYN even though
        // it has already been assigned a TypeParam type from an earlier call flow.
        inferenceContext.isAssignable(SimpleType.DYN, operandType);

        // Mark the result type as DYN since no meaningful type inferences can be made from this
        // field selection.
        resultType = SimpleType.DYN;
      } else {
        env.reportError(
            expr.id(),
            getPosition(expr),
            "type '%s' does not support field selection",
            CelTypes.format(operandType));
      }
    } else {
      resultType = SimpleType.DYN;
    }

    // If the target type was optional coming in, then the result must be optional going out.
    if (isOptional) {
      resultType = OptionalType.create(resultType);
    }
    return resultType;
  }

  private CelExpr visitOptionalCall(CelExpr expr, CelExpr.CelCall call) {
    CelExpr operand = call.args().get(0);
    CelExpr field = call.args().get(1);
    if (!field.exprKind().getKind().equals(CelExpr.ExprKind.Kind.CONSTANT)
        || field.constant().getKind() != CelConstant.Kind.STRING_VALUE) {
      env.reportError(expr.id(), getPosition(field), "unsupported optional field selection");
      return expr;
    }

    CelExpr visitedOperand = visit(operand);
    if (namespacedDeclarations && !operand.equals(visitedOperand)) {
      // Subtree has been rewritten. Replace the operand.
      expr = replaceCallArgumentSubtree(expr, visitedOperand, 0);
    }
    CelType resultType = visitSelectField(expr, operand, field.constant().stringValue(), true);
    env.setType(expr, resultType);
    env.setRef(expr, CelReference.newBuilder().addOverloadIds("select_optional_field").build());

    return expr;
  }

  /**
   * Attempt to interpret an expression as a qualified name. This traverses select and getIdent
   * expression and returns the name they constitute, or null if the expression cannot be
   * interpreted like this.
   */
  private @Nullable String asQualifiedName(CelExpr expr) {
    switch (expr.exprKind().getKind()) {
      case IDENT:
        return expr.ident().name();
      case SELECT:
        String qname = asQualifiedName(expr.select().operand());
        if (qname != null) {
          return qname + "." + expr.select().field();
        }
        return null;
      default:
        return null;
    }
  }

  /** Returns the field type give a type instance and field name. */
  private Field getFieldType(long exprId, int position, CelType type, String fieldName) {
    String typeName = type.name();
    StructType structType =
        typeProvider
            .findType(typeName)
            .filter(t -> t instanceof StructType)
            .map(StructType.class::cast)
            .orElse(null);

    if (structType != null) {
      Field field = structType.findField(fieldName).orElse(null);
      if (field != null) {
        return field;
      }

      if (structType instanceof ProtoMessageType) {
        Extension extensionField =
            ((ProtoMessageType) structType).findExtension(fieldName).orElse(null);

        if (extensionField != null) {
          return Field.of(extensionField.name(), extensionField.type());
        }
      }

      env.reportError(exprId, position, "undefined field '%s'", fieldName);
    } else {
      // Proto message was added as a variable to the environment but the descriptor was not
      // provided
      String errorMessage =
          String.format("Message type resolution failure while referencing field '%s'.", fieldName);
      if (type.kind().equals(CelKind.STRUCT)) {
        errorMessage +=
            String.format(
                " Ensure that the descriptor for type '%s' was added to the environment", typeName);
      }
      env.reportError(exprId, position, errorMessage, fieldName, typeName);
    }

    return ERROR;
  }

  /** Checks compatibility of joined types, and returns the most general common type. */
  private CelType joinTypes(long exprId, int position, CelType previousType, CelType type) {
    if (previousType == null) {
      return type;
    }
    if (homogeneousLiterals) {
      assertIsAssignable(exprId, position, type, previousType);
    } else if (!inferenceContext.isAssignable(previousType, type)) {
      return SimpleType.DYN;
    }
    return Types.mostGeneral(previousType, type);
  }

  private void assertIsAssignable(long exprId, int position, CelType actual, CelType expected) {
    if (!inferenceContext.isAssignable(expected, actual)) {
      env.reportError(
          exprId,
          position,
          "expected type '%s' but found '%s'",
          CelTypes.format(expected),
          CelTypes.format(actual));
    }
  }

  private CelType unwrapOptional(CelType type) {
    return type.parameters().get(0);
  }

  private void assertType(CelExpr expr, CelType type) {
    assertIsAssignable(expr.id(), getPosition(expr), env.getType(expr), type);
  }

  private int getPosition(CelExpr expr) {
    Integer pos = positionMap.get(expr.id());
    return pos == null ? 0 : pos;
  }

  private int getPosition(CelExpr.CelStruct.Entry entry) {
    Integer pos = positionMap.get(entry.id());
    return pos == null ? 0 : pos;
  }

  /** Helper object for holding an overload resolution result. */
  @AutoValue
  protected abstract static class OverloadResolution {

    /** The {@code Reference} to the declaration name and overload id. */
    public abstract CelReference reference();

    /** The {@code Type} of the result associated with the overload. */
    public abstract CelType type();

    /** Construct a new {@code OverloadResolution} from a {@code reference} and {@code type}. */
    public static OverloadResolution of(CelReference reference, CelType type) {
      return new AutoValue_ExprChecker_OverloadResolution(reference, type);
    }
  }

  /** Helper object to represent a {@link CelTypeProvider#findType(String)} lookup failure. */
  private static final Field ERROR = Field.of(SimpleType.ERROR.name(), SimpleType.ERROR);

  private static CelExpr replaceIdentSubtree(CelExpr expr, String name) {
    CelExpr.CelIdent newIdent = CelExpr.CelIdent.newBuilder().setName(name).build();
    return expr.toBuilder().setIdent(newIdent).build();
  }

  private static CelExpr replaceSelectOperandSubtree(CelExpr expr, CelExpr operand) {
    CelExpr.CelSelect newSelect = expr.select().toBuilder().setOperand(operand).build();
    return expr.toBuilder().setSelect(newSelect).build();
  }

  private static CelExpr replaceCallArgumentSubtree(CelExpr expr, CelExpr newArg, int index) {
    CelExpr.CelCall newCall = expr.call().toBuilder().setArg(index, newArg).build();
    return expr.toBuilder().setCall(newCall).build();
  }

  private static CelExpr replaceCallSubtree(CelExpr expr, String functionName) {
    CelExpr.CelCall newCall =
        expr.call().toBuilder().setFunction(functionName).clearTarget().build();
    return expr.toBuilder().setCall(newCall).build();
  }

  private static CelExpr replaceCallSubtree(CelExpr expr, CelExpr target) {
    CelExpr.CelCall newCall = expr.call().toBuilder().setTarget(target).build();
    return expr.toBuilder().setCall(newCall).build();
  }

  private static CelExpr replaceListElementSubtree(CelExpr expr, CelExpr element, int index) {
    CelExpr.CelList newList = expr.list().toBuilder().setElement(index, element).build();
    return expr.toBuilder().setList(newList).build();
  }

  private static CelExpr replaceStructEntryValueSubtree(CelExpr expr, CelExpr newValue, int index) {
    CelExpr.CelStruct struct = expr.struct();
    CelExpr.CelStruct.Entry newEntry =
        struct.entries().get(index).toBuilder().setValue(newValue).build();
    struct = struct.toBuilder().setEntry(index, newEntry).build();
    return expr.toBuilder().setStruct(struct).build();
  }

  private static CelExpr replaceMapEntryKeySubtree(CelExpr expr, CelExpr newKey, int index) {
    CelExpr.CelMap map = expr.map();
    CelExpr.CelMap.Entry newEntry = map.entries().get(index).toBuilder().setKey(newKey).build();
    map = map.toBuilder().setEntry(index, newEntry).build();
    return expr.toBuilder().setMap(map).build();
  }

  private static CelExpr replaceMapEntryValueSubtree(CelExpr expr, CelExpr newValue, int index) {
    CelExpr.CelMap map = expr.map();
    CelExpr.CelMap.Entry newEntry = map.entries().get(index).toBuilder().setValue(newValue).build();
    map = map.toBuilder().setEntry(index, newEntry).build();
    return expr.toBuilder().setMap(map).build();
  }

  private static CelExpr replaceComprehensionAccuInitSubtree(CelExpr expr, CelExpr newAccuInit) {
    CelExpr.CelComprehension newComprehension =
        expr.comprehension().toBuilder().setAccuInit(newAccuInit).build();
    return expr.toBuilder().setComprehension(newComprehension).build();
  }

  private static CelExpr replaceComprehensionRangeSubtree(CelExpr expr, CelExpr newRange) {
    CelExpr.CelComprehension newComprehension =
        expr.comprehension().toBuilder().setIterRange(newRange).build();
    return expr.toBuilder().setComprehension(newComprehension).build();
  }

  private static CelExpr replaceComprehensionStepSubtree(CelExpr expr, CelExpr newStep) {
    CelExpr.CelComprehension newComprehension =
        expr.comprehension().toBuilder().setLoopStep(newStep).build();
    return expr.toBuilder().setComprehension(newComprehension).build();
  }

  private static CelExpr replaceComprehensionResultSubtree(CelExpr expr, CelExpr newResult) {
    CelExpr.CelComprehension newComprehension =
        expr.comprehension().toBuilder().setResult(newResult).build();
    return expr.toBuilder().setComprehension(newComprehension).build();
  }
}
