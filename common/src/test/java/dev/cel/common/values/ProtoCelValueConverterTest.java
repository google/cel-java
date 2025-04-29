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

package dev.cel.common.values;

import static com.google.common.truth.Truth.assertThat;

import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import dev.cel.common.internal.DefaultDescriptorPool;
import dev.cel.common.internal.DefaultMessageFactory;
import dev.cel.common.internal.DynamicProto;
import dev.cel.common.internal.ProtoTimeUtils;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProtoCelValueConverterTest {

  private static final ProtoCelValueConverter PROTO_CEL_VALUE_CONVERTER =
      ProtoCelValueConverter.newInstance(
          DefaultDescriptorPool.INSTANCE, DynamicProto.create(DefaultMessageFactory.INSTANCE));

  @Test
  public void fromCelValueToJavaObject_returnsTimestampValue() {
    Timestamp timestamp =
        (Timestamp)
            PROTO_CEL_VALUE_CONVERTER.fromCelValueToJavaObject(
                TimestampValue.create(Instant.ofEpochSecond(50)));

    assertThat(timestamp).isEqualTo(ProtoTimeUtils.fromSecondsToTimestamp(50));
  }

  @Test
  public void fromCelValueToJavaObject_returnsDurationValue() {
    Duration duration =
        (Duration)
            PROTO_CEL_VALUE_CONVERTER.fromCelValueToJavaObject(
                DurationValue.create(java.time.Duration.ofSeconds(10)));

    assertThat(duration).isEqualTo(ProtoTimeUtils.fromSecondsToDuration(10));
  }

  @Test
  public void fromCelValueToJavaObject_returnsBytesValue() {
    ByteString byteString =
        (ByteString)
            PROTO_CEL_VALUE_CONVERTER.fromCelValueToJavaObject(
                BytesValue.create(CelByteString.of(new byte[] {0x1, 0x5, 0xc})));

    assertThat(byteString).isEqualTo(ByteString.copyFrom(new byte[] {0x1, 0x5, 0xc}));
  }

  @Test
  public void fromCelValueToJavaObject_returnsProtobufNullValue() {
    com.google.protobuf.NullValue nullValue =
        (com.google.protobuf.NullValue)
            PROTO_CEL_VALUE_CONVERTER.fromCelValueToJavaObject(NullValue.NULL_VALUE);

    assertThat(nullValue).isEqualTo(com.google.protobuf.NullValue.NULL_VALUE);
  }
}
