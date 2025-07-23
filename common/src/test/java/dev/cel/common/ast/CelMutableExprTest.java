// Copyright 2024 Google LLC
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

package dev.cel.common.ast;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.testing.EqualsTester;
import com.google.common.truth.Correspondence;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.ast.CelMutableExpr.CelMutableCall;
import dev.cel.common.ast.CelMutableExpr.CelMutableComprehension;
import dev.cel.common.ast.CelMutableExpr.CelMutableIdent;
import dev.cel.common.ast.CelMutableExpr.CelMutableList;
import dev.cel.common.ast.CelMutableExpr.CelMutableMap;
import dev.cel.common.ast.CelMutableExpr.CelMutableSelect;
import dev.cel.common.ast.CelMutableExpr.CelMutableStruct;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelMutableExprTest {

  @Test
  public void ofNotSet() {
    CelMutableExpr mutableExpr = CelMutableExpr.ofNotSet();

    assertThat(mutableExpr.id()).isEqualTo(0L);
    assertThat(mutableExpr.notSet()).isNotNull();
  }

  @Test
  public void ofNotSet_withId() {
    CelMutableExpr mutableExpr = CelMutableExpr.ofNotSet(1L);

    assertThat(mutableExpr.id()).isEqualTo(1L);
    assertThat(mutableExpr.notSet()).isNotNull();
  }

  @Test
  public void ofConstant() {
    CelMutableExpr mutableExpr = CelMutableExpr.ofConstant(CelConstant.ofValue(5L));

    assertThat(mutableExpr.id()).isEqualTo(0L);
    assertThat(mutableExpr.constant()).isEqualTo(CelConstant.ofValue(5L));
  }

  @Test
  public void ofConstant_withId() {
    CelMutableExpr mutableExpr = CelMutableExpr.ofConstant(1L, CelConstant.ofValue(5L));

    assertThat(mutableExpr.id()).isEqualTo(1L);
    assertThat(mutableExpr.constant()).isEqualTo(CelConstant.ofValue(5L));
  }

  @Test
  public void mutableConstant_deepCopy() {
    CelMutableExpr mutableExpr = CelMutableExpr.ofConstant(1L, CelConstant.ofValue(5L));

    CelMutableExpr deepCopiedExpr = CelMutableExpr.newInstance(mutableExpr);

    assertThat(mutableExpr).isEqualTo(deepCopiedExpr);
    assertThat(mutableExpr.constant()).isEqualTo(deepCopiedExpr.constant());
    assertThat(mutableExpr).isNotSameInstanceAs(deepCopiedExpr);
    // The stored constant itself is immutable, thus remain referentially equal when copied.
    assertThat(mutableExpr.constant()).isSameInstanceAs(deepCopiedExpr.constant());
  }

  @Test
  public void ofIdent() {
    CelMutableExpr mutableExpr = CelMutableExpr.ofIdent("x");

    assertThat(mutableExpr.id()).isEqualTo(0L);
    assertThat(mutableExpr.ident().name()).isEqualTo("x");
  }

  @Test
  public void ofIdent_withId() {
    CelMutableExpr mutableExpr = CelMutableExpr.ofIdent(1L, "x");

    assertThat(mutableExpr.id()).isEqualTo(1L);
    assertThat(mutableExpr.ident().name()).isEqualTo("x");
  }

  @Test
  public void mutableIdent_setName() {
    CelMutableIdent ident = CelMutableIdent.create("x");

    ident.setName("y");

    assertThat(ident.name()).isEqualTo("y");
  }

  @Test
  public void mutableIdent_deepCopy() {
    CelMutableExpr mutableExpr = CelMutableExpr.ofIdent(1L, "x");

    CelMutableExpr deepCopiedExpr = CelMutableExpr.newInstance(mutableExpr);

    assertThat(mutableExpr).isEqualTo(deepCopiedExpr);
    assertThat(mutableExpr.ident()).isEqualTo(deepCopiedExpr.ident());
    assertThat(mutableExpr).isNotSameInstanceAs(deepCopiedExpr);
    assertThat(mutableExpr.ident()).isNotSameInstanceAs(deepCopiedExpr.ident());
  }

  @Test
  public void ofSelect() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofSelect(CelMutableSelect.create(CelMutableExpr.ofIdent("x"), "field"));

    assertThat(mutableExpr.id()).isEqualTo(0L);
    assertThat(mutableExpr.select().testOnly()).isFalse();
    assertThat(mutableExpr.select().field()).isEqualTo("field");
    assertThat(mutableExpr.select().operand()).isEqualTo(CelMutableExpr.ofIdent("x"));
  }

  @Test
  public void ofSelect_withId() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofSelect(
            1L,
            CelMutableSelect.create(CelMutableExpr.ofIdent("x"), "field", /* testOnly= */ true));

    assertThat(mutableExpr.id()).isEqualTo(1L);
    assertThat(mutableExpr.select().testOnly()).isTrue();
    assertThat(mutableExpr.select().field()).isEqualTo("field");
    assertThat(mutableExpr.select().operand()).isEqualTo(CelMutableExpr.ofIdent("x"));
  }

  @Test
  public void mutableSelect_setters() {
    CelMutableSelect select =
        CelMutableSelect.create(CelMutableExpr.ofIdent("x"), "field", /* testOnly= */ true);

    select.setOperand(CelMutableExpr.ofConstant(CelConstant.ofValue(1L)));
    select.setField("field2");
    select.setTestOnly(false);

    assertThat(select.operand()).isEqualTo(CelMutableExpr.ofConstant(CelConstant.ofValue(1L)));
    assertThat(select.field()).isEqualTo("field2");
    assertThat(select.testOnly()).isFalse();
  }

  @Test
  public void mutableSelect_deepCopy() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofSelect(
            1L,
            CelMutableSelect.create(CelMutableExpr.ofIdent("x"), "field", /* testOnly= */ true));

    CelMutableExpr deepCopiedExpr = CelMutableExpr.newInstance(mutableExpr);

    assertThat(mutableExpr).isEqualTo(deepCopiedExpr);
    assertThat(mutableExpr.select()).isEqualTo(deepCopiedExpr.select());
    assertThat(mutableExpr).isNotSameInstanceAs(deepCopiedExpr);
    assertThat(mutableExpr.select()).isNotSameInstanceAs(deepCopiedExpr.select());
  }

  @Test
  public void ofCall() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofCall(
            CelMutableCall.create(
                CelMutableExpr.ofConstant(CelConstant.ofValue("target")),
                "function",
                CelMutableExpr.ofConstant(CelConstant.ofValue("arg"))));

    assertThat(mutableExpr.id()).isEqualTo(0L);
    assertThat(mutableExpr.call().target())
        .hasValue(CelMutableExpr.ofConstant(CelConstant.ofValue("target")));
    assertThat(mutableExpr.call().function()).isEqualTo("function");
    assertThat(mutableExpr.call().args())
        .containsExactly(CelMutableExpr.ofConstant(CelConstant.ofValue("arg")));
  }

  @Test
  public void ofCall_withId() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofCall(
            1L,
            CelMutableCall.create(
                CelMutableExpr.ofConstant(CelConstant.ofValue("target")),
                "function",
                CelMutableExpr.ofConstant(CelConstant.ofValue("arg"))));

    assertThat(mutableExpr.id()).isEqualTo(1L);
    assertThat(mutableExpr.call().target())
        .hasValue(CelMutableExpr.ofConstant(CelConstant.ofValue("target")));
    assertThat(mutableExpr.call().function()).isEqualTo("function");
    assertThat(mutableExpr.call().args())
        .containsExactly(CelMutableExpr.ofConstant(CelConstant.ofValue("arg")));
  }

  @Test
  public void setId_success() {
    CelMutableExpr mutableExpr = CelMutableExpr.ofConstant(CelConstant.ofValue(5L));

    mutableExpr.setId(2L);

    assertThat(mutableExpr.id()).isEqualTo(2L);
  }

  @Test
  public void mutableCall_setArgumentAtIndex() {
    CelMutableCall call =
        CelMutableCall.create("function", CelMutableExpr.ofConstant(CelConstant.ofValue(1L)));

    call.setArg(0, CelMutableExpr.ofConstant(CelConstant.ofValue("hello")));

    assertThat(call.args())
        .containsExactly(CelMutableExpr.ofConstant(CelConstant.ofValue("hello")));
    assertThat(call.args()).isInstanceOf(ArrayList.class);
  }

  @Test
  public void mutableCall_setArguments() {
    CelMutableCall call =
        CelMutableCall.create("function", CelMutableExpr.ofConstant(CelConstant.ofValue(1L)));

    call.setArgs(
        ImmutableList.of(
            CelMutableExpr.ofConstant(CelConstant.ofValue(2)),
            CelMutableExpr.ofConstant(CelConstant.ofValue(3))));

    assertThat(call.args())
        .containsExactly(
            CelMutableExpr.ofConstant(CelConstant.ofValue(2)),
            CelMutableExpr.ofConstant(CelConstant.ofValue(3)))
        .inOrder();
    assertThat(call.args()).isInstanceOf(ArrayList.class);
  }

  @Test
  public void mutableCall_addArguments() {
    CelMutableCall call =
        CelMutableCall.create("function", CelMutableExpr.ofConstant(CelConstant.ofValue(1L)));

    call.addArgs(
        CelMutableExpr.ofConstant(CelConstant.ofValue(2)),
        CelMutableExpr.ofConstant(CelConstant.ofValue(3)));

    assertThat(call.args())
        .containsExactly(
            CelMutableExpr.ofConstant(CelConstant.ofValue(1)),
            CelMutableExpr.ofConstant(CelConstant.ofValue(2)),
            CelMutableExpr.ofConstant(CelConstant.ofValue(3)))
        .inOrder();
    assertThat(call.args()).isInstanceOf(ArrayList.class);
  }

  @Test
  public void mutableCall_clearArguments() {
    CelMutableCall call =
        CelMutableCall.create(
            "function",
            CelMutableExpr.ofConstant(CelConstant.ofValue(1L)),
            CelMutableExpr.ofConstant(CelConstant.ofValue(2L)));

    call.clearArgs();

    assertThat(call.args()).isEmpty();
  }

  @Test
  public void mutableCall_setTarget() {
    CelMutableCall call = CelMutableCall.create("function");

    call.setTarget(CelMutableExpr.ofConstant(CelConstant.ofValue("hello")));

    assertThat(call.target()).hasValue(CelMutableExpr.ofConstant(CelConstant.ofValue("hello")));
  }

  @Test
  public void mutableCall_deepCopy() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofCall(
            1L,
            CelMutableCall.create(
                CelMutableExpr.ofConstant(CelConstant.ofValue("target")),
                "function",
                CelMutableExpr.ofConstant(CelConstant.ofValue("arg"))));

    CelMutableExpr deepCopiedExpr = CelMutableExpr.newInstance(mutableExpr);

    assertThat(mutableExpr).isEqualTo(deepCopiedExpr);
    assertThat(mutableExpr.call()).isEqualTo(deepCopiedExpr.call());
    assertThat(mutableExpr).isNotSameInstanceAs(deepCopiedExpr);
    assertThat(mutableExpr.call()).isNotSameInstanceAs(deepCopiedExpr.call());
  }

  @Test
  public void mutableCall_setFunction() {
    CelMutableCall call = CelMutableCall.create("function");

    call.setFunction("function2");

    assertThat(call.function()).isEqualTo("function2");
  }

  @Test
  public void ofList() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofList(
            CelMutableList.create(
                CelMutableExpr.ofConstant(CelConstant.ofValue("element1")),
                CelMutableExpr.ofConstant(CelConstant.ofValue("element2"))));

    assertThat(mutableExpr.id()).isEqualTo(0L);
    assertThat(mutableExpr.list().elements())
        .containsExactly(
            CelMutableExpr.ofConstant(CelConstant.ofValue("element1")),
            CelMutableExpr.ofConstant(CelConstant.ofValue("element2")))
        .inOrder();
    assertThat(mutableExpr.list().optionalIndices()).isEmpty();
  }

  @Test
  public void ofList_withId() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofList(
            1L,
            CelMutableList.create(
                ImmutableList.of(
                    CelMutableExpr.ofConstant(CelConstant.ofValue("element1")),
                    CelMutableExpr.ofConstant(CelConstant.ofValue("element2"))),
                ImmutableList.of(0, 1)));

    assertThat(mutableExpr.id()).isEqualTo(1L);
    assertThat(mutableExpr.list().elements())
        .containsExactly(
            CelMutableExpr.ofConstant(CelConstant.ofValue("element1")),
            CelMutableExpr.ofConstant(CelConstant.ofValue("element2")))
        .inOrder();
    assertThat(mutableExpr.list().optionalIndices()).containsExactly(0, 1).inOrder();
  }

  @Test
  public void mutableList_setElementAtIndex() {
    CelMutableList list =
        CelMutableList.create(CelMutableExpr.ofConstant(CelConstant.ofValue("element1")));

    list.setElement(0, CelMutableExpr.ofConstant(CelConstant.ofValue("hello")));

    assertThat(list.elements())
        .containsExactly(CelMutableExpr.ofConstant(CelConstant.ofValue("hello")));
    assertThat(list.elements()).isInstanceOf(ArrayList.class);
  }

  @Test
  @SuppressWarnings("ReferenceEquality") // test only on iterating through elements
  public void mutableList_deepCopy() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofList(
            CelMutableList.create(
                CelMutableExpr.ofConstant(CelConstant.ofValue("element1")),
                CelMutableExpr.ofConstant(CelConstant.ofValue("element2"))));

    CelMutableExpr deepCopiedExpr = CelMutableExpr.newInstance(mutableExpr);

    assertThat(mutableExpr).isEqualTo(deepCopiedExpr);
    assertThat(mutableExpr.list()).isEqualTo(deepCopiedExpr.list());
    assertThat(mutableExpr).isNotSameInstanceAs(deepCopiedExpr);
    assertThat(mutableExpr.list()).isNotSameInstanceAs(deepCopiedExpr.list());
    assertThat(mutableExpr.list().elements())
        .comparingElementsUsing(
            Correspondence.from(
                (e1, e2) -> e1 != e2 && e1.equals(e2),
                "are only value equal and not referentially equal"))
        .containsExactlyElementsIn(deepCopiedExpr.list().elements());
  }

  @Test
  public void ofStruct() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofStruct(CelMutableStruct.create("message", ImmutableList.of()));

    assertThat(mutableExpr.id()).isEqualTo(0L);
    assertThat(mutableExpr.struct().messageName()).isEqualTo("message");
    assertThat(mutableExpr.struct().entries()).isEmpty();
  }

  @Test
  public void ofStruct_withId() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofStruct(
            8L,
            CelMutableStruct.create(
                "message",
                ImmutableList.of(
                    CelMutableStruct.Entry.create(
                        9L,
                        "field",
                        CelMutableExpr.ofConstant(CelConstant.ofValue("value")),
                        /* optionalEntry= */ true))));

    assertThat(mutableExpr.id()).isEqualTo(8L);
    assertThat(mutableExpr.struct().messageName()).isEqualTo("message");
    assertThat(mutableExpr.struct().entries())
        .containsExactly(
            CelMutableStruct.Entry.create(
                9L,
                "field",
                CelMutableExpr.ofConstant(CelConstant.ofValue("value")),
                /* optionalEntry= */ true));
  }

  @Test
  public void mutableStruct_setEntryAtIndex() {
    CelMutableStruct struct =
        CelMutableStruct.create(
            "message",
            ImmutableList.of(
                CelMutableStruct.Entry.create(
                    1L, "field", CelMutableExpr.ofConstant(CelConstant.ofValue("value")))));
    CelMutableStruct.Entry newEntry =
        CelMutableStruct.Entry.create(
            2L,
            "field2",
            CelMutableExpr.ofConstant(CelConstant.ofValue("value2")),
            /* optionalEntry= */ true);

    struct.setEntry(0, newEntry);

    assertThat(struct.entries()).containsExactly(newEntry);
  }

  @Test
  public void mutableStructEntry_setters() {
    CelMutableStruct.Entry structEntry =
        CelMutableStruct.Entry.create(
            1L, "field", CelMutableExpr.ofConstant(CelConstant.ofValue("value")));

    structEntry.setId(2L);
    structEntry.setFieldKey("field2");
    structEntry.setValue(CelMutableExpr.ofConstant(CelConstant.ofValue("value2")));
    structEntry.setOptionalEntry(true);

    assertThat(structEntry)
        .isEqualTo(
            CelMutableStruct.Entry.create(
                2L, "field2", CelMutableExpr.ofConstant(CelConstant.ofValue("value2")), true));
  }

  @Test
  @SuppressWarnings("ReferenceEquality") // test only on iterating through elements
  public void mutableStruct_deepCopy() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofStruct(
            8L,
            CelMutableStruct.create(
                "message",
                ImmutableList.of(
                    CelMutableStruct.Entry.create(
                        8L,
                        "field",
                        CelMutableExpr.ofConstant(CelConstant.ofValue("value")),
                        /* optionalEntry= */ true))));

    CelMutableExpr deepCopiedExpr = CelMutableExpr.newInstance(mutableExpr);

    assertThat(mutableExpr).isEqualTo(deepCopiedExpr);
    assertThat(mutableExpr.struct()).isEqualTo(deepCopiedExpr.struct());
    assertThat(mutableExpr).isNotSameInstanceAs(deepCopiedExpr);
    assertThat(mutableExpr.struct()).isNotSameInstanceAs(deepCopiedExpr.struct());
    assertThat(mutableExpr.struct().entries())
        .isNotSameInstanceAs(deepCopiedExpr.struct().entries());
    assertThat(mutableExpr.struct().entries())
        .comparingElementsUsing(
            Correspondence.<CelMutableStruct.Entry, CelMutableStruct.Entry>from(
                (e1, e2) ->
                    e1 != e2
                        && e1.equals(e2)
                        && e1.value() != e2.value()
                        && e1.value().equals(e2.value()),
                "are only value equal and not referentially equal"))
        .containsExactlyElementsIn(deepCopiedExpr.struct().entries());
  }

  @Test
  public void ofMap() {
    CelMutableExpr mutableExpr = CelMutableExpr.ofMap(CelMutableMap.create(ImmutableList.of()));

    assertThat(mutableExpr.id()).isEqualTo(0L);
    assertThat(mutableExpr.map().entries()).isEmpty();
  }

  @Test
  public void ofMap_withId() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofMap(
            9L,
            CelMutableMap.create(
                ImmutableList.of(
                    CelMutableMap.Entry.create(
                        10L,
                        CelMutableExpr.ofConstant(CelConstant.ofValue("key")),
                        CelMutableExpr.ofConstant(CelConstant.ofValue("value")),
                        /* optionalEntry= */ true))));

    assertThat(mutableExpr.id()).isEqualTo(9L);
    assertThat(mutableExpr.map().entries())
        .containsExactly(
            CelMutableMap.Entry.create(
                10L,
                CelMutableExpr.ofConstant(CelConstant.ofValue("key")),
                CelMutableExpr.ofConstant(CelConstant.ofValue("value")),
                /* optionalEntry= */ true));
  }

  @Test
  public void mutableMap_setEntryAtIndex() {
    CelMutableMap map =
        CelMutableMap.create(
            ImmutableList.of(
                CelMutableMap.Entry.create(
                    10L,
                    CelMutableExpr.ofConstant(CelConstant.ofValue("key")),
                    CelMutableExpr.ofConstant(CelConstant.ofValue("value")))));
    CelMutableMap.Entry newEntry =
        CelMutableMap.Entry.create(
            2L,
            CelMutableExpr.ofConstant(CelConstant.ofValue("key2")),
            CelMutableExpr.ofConstant(CelConstant.ofValue("value2")),
            /* optionalEntry= */ true);

    map.setEntry(0, newEntry);

    assertThat(map.entries()).containsExactly(newEntry);
  }

  @Test
  public void mutableMapEntry_setters() {
    CelMutableMap.Entry mapEntry =
        CelMutableMap.Entry.create(
            1L,
            CelMutableExpr.ofConstant(CelConstant.ofValue("key")),
            CelMutableExpr.ofConstant(CelConstant.ofValue("value")));

    mapEntry.setId(2L);
    mapEntry.setKey(CelMutableExpr.ofConstant(CelConstant.ofValue("key2")));
    mapEntry.setValue(CelMutableExpr.ofConstant(CelConstant.ofValue("value2")));
    mapEntry.setOptionalEntry(true);

    assertThat(mapEntry)
        .isEqualTo(
            CelMutableMap.Entry.create(
                2L,
                CelMutableExpr.ofConstant(CelConstant.ofValue("key2")),
                CelMutableExpr.ofConstant(CelConstant.ofValue("value2")),
                true));
  }

  @Test
  @SuppressWarnings("ReferenceEquality") // test only on iterating through elements
  public void mutableMap_deepCopy() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofMap(
            9L,
            CelMutableMap.create(
                ImmutableList.of(
                    CelMutableMap.Entry.create(
                        10L,
                        CelMutableExpr.ofConstant(CelConstant.ofValue("key")),
                        CelMutableExpr.ofConstant(CelConstant.ofValue("value")),
                        /* optionalEntry= */ true))));

    CelMutableExpr deepCopiedExpr = CelMutableExpr.newInstance(mutableExpr);

    assertThat(mutableExpr).isEqualTo(deepCopiedExpr);
    assertThat(mutableExpr.map()).isEqualTo(deepCopiedExpr.map());
    assertThat(mutableExpr).isNotSameInstanceAs(deepCopiedExpr);
    assertThat(mutableExpr.map()).isNotSameInstanceAs(deepCopiedExpr.map());
    assertThat(mutableExpr.map().entries())
        .comparingElementsUsing(
            Correspondence.<CelMutableMap.Entry, CelMutableMap.Entry>from(
                (e1, e2) ->
                    e1 != e2
                        && e1.equals(e2)
                        && e1.key() != e2.key()
                        && e1.key().equals(e2.key())
                        && e1.value() != e2.value()
                        && e1.value().equals(e2.value()),
                "are only value equal and not referentially equal"))
        .containsExactlyElementsIn(deepCopiedExpr.map().entries());
  }

  @Test
  public void ofComprehension_withId() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofComprehension(
            10L,
            CelMutableComprehension.create(
                "iterVar",
                CelMutableExpr.ofList(
                    CelMutableList.create(CelMutableExpr.ofConstant(CelConstant.ofValue(true)))),
                "accuVar",
                CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                CelMutableExpr.ofIdent("__result__")));

    assertThat(mutableExpr.id()).isEqualTo(10L);
    assertThat(mutableExpr.comprehension())
        .isEqualTo(
            CelMutableComprehension.create(
                "iterVar",
                "",
                CelMutableExpr.ofList(
                    CelMutableList.create(CelMutableExpr.ofConstant(CelConstant.ofValue(true)))),
                "accuVar",
                CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                CelMutableExpr.ofIdent("__result__")));
  }

  @Test
  public void mutableComprehension_setters() {
    CelMutableComprehension mutableComprehension =
        CelMutableComprehension.create(
            "iterVar",
            CelMutableExpr.ofNotSet(),
            "accuVar",
            CelMutableExpr.ofNotSet(),
            CelMutableExpr.ofNotSet(),
            CelMutableExpr.ofNotSet(),
            CelMutableExpr.ofNotSet());

    mutableComprehension.setIterVar("iterVar2");
    mutableComprehension.setAccuVar("accuVar2");
    mutableComprehension.setIterRange(
        CelMutableExpr.ofList(
            CelMutableList.create(CelMutableExpr.ofConstant(CelConstant.ofValue(true)))));
    mutableComprehension.setAccuInit(CelMutableExpr.ofConstant(CelConstant.ofValue(true)));
    mutableComprehension.setLoopCondition(CelMutableExpr.ofConstant(CelConstant.ofValue(true)));
    mutableComprehension.setLoopStep(CelMutableExpr.ofConstant(CelConstant.ofValue(true)));
    mutableComprehension.setResult(CelMutableExpr.ofIdent("__result__"));

    assertThat(mutableComprehension)
        .isEqualTo(
            CelMutableComprehension.create(
                "iterVar2",
                CelMutableExpr.ofList(
                    CelMutableList.create(CelMutableExpr.ofConstant(CelConstant.ofValue(true)))),
                "accuVar2",
                CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                CelMutableExpr.ofIdent("__result__")));
  }

  @Test
  public void mutableComprehension_deepCopy() {
    CelMutableExpr mutableExpr =
        CelMutableExpr.ofComprehension(
            10L,
            CelMutableComprehension.create(
                "iterVar",
                CelMutableExpr.ofList(
                    CelMutableList.create(CelMutableExpr.ofConstant(CelConstant.ofValue(true)))),
                "accuVar",
                CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                CelMutableExpr.ofIdent("__result__")));

    CelMutableExpr deepCopiedExpr = CelMutableExpr.newInstance(mutableExpr);

    assertThat(mutableExpr).isEqualTo(deepCopiedExpr);
    assertThat(mutableExpr).isNotSameInstanceAs(deepCopiedExpr);
    assertThat(mutableExpr.comprehension()).isEqualTo(deepCopiedExpr.comprehension());
    assertThat(mutableExpr.comprehension()).isNotSameInstanceAs(deepCopiedExpr.comprehension());
    assertThat(mutableExpr.comprehension().accuInit())
        .isEqualTo(deepCopiedExpr.comprehension().accuInit());
    assertThat(mutableExpr.comprehension().accuInit())
        .isNotSameInstanceAs(deepCopiedExpr.comprehension().accuInit());
    assertThat(mutableExpr.comprehension().iterRange())
        .isEqualTo(deepCopiedExpr.comprehension().iterRange());
    assertThat(mutableExpr.comprehension().iterRange())
        .isNotSameInstanceAs(deepCopiedExpr.comprehension().iterRange());
    assertThat(mutableExpr.comprehension().loopCondition())
        .isEqualTo(deepCopiedExpr.comprehension().loopCondition());
    assertThat(mutableExpr.comprehension().loopCondition())
        .isNotSameInstanceAs(deepCopiedExpr.comprehension().loopCondition());
    assertThat(mutableExpr.comprehension().loopStep())
        .isEqualTo(deepCopiedExpr.comprehension().loopStep());
    assertThat(mutableExpr.comprehension().loopStep())
        .isNotSameInstanceAs(deepCopiedExpr.comprehension().loopStep());
    assertThat(mutableExpr.comprehension().result())
        .isEqualTo(deepCopiedExpr.comprehension().result());
    assertThat(mutableExpr.comprehension().result())
        .isNotSameInstanceAs(deepCopiedExpr.comprehension().result());
  }

  @Test
  public void equalityTest() {
    new EqualsTester()
        .addEqualityGroup(CelMutableExpr.ofNotSet())
        .addEqualityGroup(CelMutableExpr.ofNotSet(1L), CelMutableExpr.ofNotSet(1L))
        .addEqualityGroup(CelMutableExpr.ofConstant(1L, CelConstant.ofValue(2L)))
        .addEqualityGroup(
            CelMutableExpr.ofConstant(5L, CelConstant.ofValue("hello")),
            CelMutableExpr.ofConstant(5L, CelConstant.ofValue("hello")))
        .addEqualityGroup(CelMutableExpr.ofIdent("x"))
        .addEqualityGroup(CelMutableExpr.ofIdent(2L, "y"), CelMutableExpr.ofIdent(2L, "y"))
        .addEqualityGroup(
            CelMutableExpr.ofSelect(CelMutableSelect.create(CelMutableExpr.ofIdent("y"), "field")))
        .addEqualityGroup(
            CelMutableExpr.ofSelect(
                4L, CelMutableSelect.create(CelMutableExpr.ofIdent("x"), "test")),
            CelMutableExpr.ofSelect(
                4L, CelMutableSelect.create(CelMutableExpr.ofIdent("x"), "test")))
        .addEqualityGroup(CelMutableExpr.ofCall(CelMutableCall.create("function")))
        .addEqualityGroup(
            CelMutableExpr.ofCall(
                5L,
                CelMutableCall.create(
                    CelMutableExpr.ofConstant(CelConstant.ofValue("target")),
                    "function",
                    CelMutableExpr.ofConstant(CelConstant.ofValue("arg")))),
            CelMutableExpr.ofCall(
                5L,
                CelMutableCall.create(
                    CelMutableExpr.ofConstant(CelConstant.ofValue("target")),
                    "function",
                    CelMutableExpr.ofConstant(CelConstant.ofValue("arg")))))
        .addEqualityGroup(CelMutableExpr.ofList(CelMutableList.create()))
        .addEqualityGroup(
            CelMutableExpr.ofList(
                6L,
                CelMutableList.create(
                    CelMutableExpr.ofConstant(CelConstant.ofValue("element1")),
                    CelMutableExpr.ofConstant(CelConstant.ofValue("element2")))),
            CelMutableExpr.ofList(
                6L,
                CelMutableList.create(
                    CelMutableExpr.ofConstant(CelConstant.ofValue("element1")),
                    CelMutableExpr.ofConstant(CelConstant.ofValue("element2")))))
        .addEqualityGroup(
            CelMutableExpr.ofStruct(CelMutableStruct.create("message", ImmutableList.of())))
        .addEqualityGroup(
            CelMutableExpr.ofStruct(
                7L,
                CelMutableStruct.create(
                    "message",
                    ImmutableList.of(
                        CelMutableStruct.Entry.create(
                            8L,
                            "field",
                            CelMutableExpr.ofConstant(CelConstant.ofValue("value")),
                            /* optionalEntry= */ true)))),
            CelMutableExpr.ofStruct(
                7L,
                CelMutableStruct.create(
                    "message",
                    ImmutableList.of(
                        CelMutableStruct.Entry.create(
                            8L,
                            "field",
                            CelMutableExpr.ofConstant(CelConstant.ofValue("value")),
                            /* optionalEntry= */ true)))))
        .addEqualityGroup(CelMutableExpr.ofMap(CelMutableMap.create(ImmutableList.of())))
        .addEqualityGroup(
            CelMutableMap.create(
                ImmutableList.of(
                    CelMutableMap.Entry.create(
                        9L,
                        CelMutableExpr.ofConstant(CelConstant.ofValue("key")),
                        CelMutableExpr.ofConstant(CelConstant.ofValue("value")),
                        /* optionalEntry= */ true))),
            CelMutableMap.create(
                ImmutableList.of(
                    CelMutableMap.Entry.create(
                        9L,
                        CelMutableExpr.ofConstant(CelConstant.ofValue("key")),
                        CelMutableExpr.ofConstant(CelConstant.ofValue("value")),
                        /* optionalEntry= */ true))))
        .addEqualityGroup(
            CelMutableExpr.ofComprehension(
                10L,
                CelMutableComprehension.create(
                    "iterVar",
                    CelMutableExpr.ofNotSet(),
                    "accuVar",
                    CelMutableExpr.ofNotSet(),
                    CelMutableExpr.ofNotSet(),
                    CelMutableExpr.ofNotSet(),
                    CelMutableExpr.ofNotSet())))
        .addEqualityGroup(
            CelMutableExpr.ofComprehension(
                11L,
                CelMutableComprehension.create(
                    "iterVar",
                    CelMutableExpr.ofList(
                        CelMutableList.create(
                            CelMutableExpr.ofConstant(CelConstant.ofValue(true)))),
                    "accuVar",
                    CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                    CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                    CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                    CelMutableExpr.ofIdent("__result__"))),
            CelMutableExpr.ofComprehension(
                11L,
                CelMutableComprehension.create(
                    "iterVar",
                    CelMutableExpr.ofList(
                        CelMutableList.create(
                            CelMutableExpr.ofConstant(CelConstant.ofValue(true)))),
                    "accuVar",
                    CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                    CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                    CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                    CelMutableExpr.ofIdent("__result__"))))
        .addEqualityGroup(
            CelMutableExpr.ofComprehension(
                12L,
                CelMutableComprehension.create(
                    "iterVar",
                    "iterVar2",
                    CelMutableExpr.ofList(
                        CelMutableList.create(
                            CelMutableExpr.ofConstant(CelConstant.ofValue(true)))),
                    "accuVar",
                    CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                    CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                    CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                    CelMutableExpr.ofIdent("__result__"))),
            CelMutableExpr.ofComprehension(
                12L,
                CelMutableComprehension.create(
                    "iterVar",
                    "iterVar2",
                    CelMutableExpr.ofList(
                        CelMutableList.create(
                            CelMutableExpr.ofConstant(CelConstant.ofValue(true)))),
                    "accuVar",
                    CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                    CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                    CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                    CelMutableExpr.ofIdent("__result__"))))
        .testEquals();
  }

  @SuppressWarnings("Immutable") // Mutable by design
  private enum MutableExprKindTestCase {
    NOT_SET(CelMutableExpr.ofNotSet(1L)),
    CONSTANT(CelMutableExpr.ofConstant(CelConstant.ofValue(2L))),
    IDENT(CelMutableExpr.ofIdent("test")),
    SELECT(CelMutableExpr.ofSelect(CelMutableSelect.create(CelMutableExpr.ofNotSet(), "field"))),
    CALL(CelMutableExpr.ofCall(CelMutableCall.create("call"))),
    LIST(CelMutableExpr.ofList(CelMutableList.create())),
    STRUCT(CelMutableExpr.ofStruct(CelMutableStruct.create("message", ImmutableList.of()))),
    MAP(CelMutableExpr.ofMap(CelMutableMap.create(ImmutableList.of()))),
    COMPREHENSION(
        CelMutableExpr.ofComprehension(
            10L,
            CelMutableComprehension.create(
                "iterVar",
                CelMutableExpr.ofNotSet(),
                "accuVar",
                CelMutableExpr.ofNotSet(),
                CelMutableExpr.ofNotSet(),
                CelMutableExpr.ofNotSet(),
                CelMutableExpr.ofNotSet()))),
    ;

    private final CelMutableExpr mutableExpr;

    MutableExprKindTestCase(CelMutableExpr mutableExpr) {
      this.mutableExpr = mutableExpr;
    }
  }

  @Test
  public void getExprValue_invalidKind_throws(@TestParameter MutableExprKindTestCase testCase) {
    Kind testCaseKind = testCase.mutableExpr.getKind();
    if (!testCaseKind.equals(Kind.NOT_SET)) {
      assertThrows(IllegalArgumentException.class, testCase.mutableExpr::notSet);
    }
    if (!testCaseKind.equals(Kind.CONSTANT)) {
      assertThrows(IllegalArgumentException.class, testCase.mutableExpr::constant);
    }
    if (!testCaseKind.equals(Kind.IDENT)) {
      assertThrows(IllegalArgumentException.class, testCase.mutableExpr::ident);
    }
    if (!testCaseKind.equals(Kind.SELECT)) {
      assertThrows(IllegalArgumentException.class, testCase.mutableExpr::select);
    }
    if (!testCaseKind.equals(Kind.CALL)) {
      assertThrows(IllegalArgumentException.class, testCase.mutableExpr::call);
    }
    if (!testCaseKind.equals(Kind.LIST)) {
      assertThrows(IllegalArgumentException.class, testCase.mutableExpr::list);
    }
    if (!testCaseKind.equals(Kind.STRUCT)) {
      assertThrows(IllegalArgumentException.class, testCase.mutableExpr::struct);
    }
    if (!testCaseKind.equals(Kind.MAP)) {
      assertThrows(IllegalArgumentException.class, testCase.mutableExpr::map);
    }
    if (!testCaseKind.equals(Kind.COMPREHENSION)) {
      assertThrows(IllegalArgumentException.class, testCase.mutableExpr::comprehension);
    }
  }

  @SuppressWarnings("Immutable") // Mutable by design
  private enum HashCodeTestCase {
    NOT_SET(CelMutableExpr.ofNotSet(1L), -722379961),
    CONSTANT(CelMutableExpr.ofConstant(2L, CelConstant.ofValue("test")), -724279919),
    IDENT(CelMutableExpr.ofIdent("x"), -721379855),
    SELECT(
        CelMutableExpr.ofSelect(
            4L,
            CelMutableSelect.create(CelMutableExpr.ofIdent("y"), "field", /* testOnly= */ true)),
        1458249843),
    CALL(
        CelMutableExpr.ofCall(
            5L,
            CelMutableCall.create(
                CelMutableExpr.ofConstant(CelConstant.ofValue("target")),
                "function",
                CelMutableExpr.ofConstant(CelConstant.ofValue("arg")))),
        -1735261193),
    LIST(
        CelMutableExpr.ofList(
            6L,
            CelMutableList.create(
                CelMutableExpr.ofConstant(CelConstant.ofValue("element1")),
                CelMutableExpr.ofConstant(CelConstant.ofValue("element2")))),
        165341403),
    STRUCT(
        CelMutableExpr.ofStruct(
            7L,
            CelMutableStruct.create(
                "message",
                ImmutableList.of(
                    CelMutableStruct.Entry.create(
                        8L,
                        "field",
                        CelMutableExpr.ofConstant(CelConstant.ofValue("value")),
                        /* optionalEntry= */ true)))),
        2064611987),
    MAP(
        CelMutableExpr.ofMap(
            8L,
            CelMutableMap.create(
                ImmutableList.of(
                    CelMutableMap.Entry.create(
                        9L,
                        CelMutableExpr.ofConstant(CelConstant.ofValue("key")),
                        CelMutableExpr.ofConstant(CelConstant.ofValue("value")),
                        /* optionalEntry= */ true)))),
        1260717292),
    COMPREHENSION(
        CelMutableExpr.ofComprehension(
            10L,
            CelMutableComprehension.create(
                "iterVar",
                CelMutableExpr.ofList(
                    CelMutableList.create(CelMutableExpr.ofConstant(CelConstant.ofValue(true)))),
                "accuVar",
                CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                CelMutableExpr.ofIdent("__result__"))),
        -707426392),
    COMPREHENSIONV2(
        CelMutableExpr.ofComprehension(
            10L,
            CelMutableComprehension.create(
                "iterVar",
                "iterVar2",
                CelMutableExpr.ofList(
                    CelMutableList.create(CelMutableExpr.ofConstant(CelConstant.ofValue(true)))),
                "accuVar",
                CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                CelMutableExpr.ofConstant(CelConstant.ofValue(true)),
                CelMutableExpr.ofIdent("__result__"))),
        1063550879);

    private final CelMutableExpr mutableExpr;
    private final int expectedHashCode;

    HashCodeTestCase(CelMutableExpr mutableExpr, int expectedHashCode) {
      this.mutableExpr = mutableExpr;
      this.expectedHashCode = expectedHashCode;
    }
  }

  @Test
  public void hashCodeTest(@TestParameter HashCodeTestCase testCase) {
    assertThat(testCase.mutableExpr.hashCode()).isEqualTo(testCase.expectedHashCode);
    // Run it twice to ensure cached value is stable
    assertThat(testCase.mutableExpr.hashCode()).isEqualTo(testCase.expectedHashCode);
  }

  @Test
  public void propertyMutated_hashCodeChanged() {
    CelMutableExpr mutableExpr = CelMutableExpr.ofIdent("x");
    int originalHash = mutableExpr.hashCode();

    mutableExpr.ident().setName("y");

    assertThat(originalHash).isNotEqualTo(mutableExpr.hashCode());
  }
}
