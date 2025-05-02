// Copyright 2025 Google LLC
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

package dev.cel.protobuf;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
// CEL-Internal-5
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.Version;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

final class JavaFileGenerator {

  private static final String HELPER_CLASS_TEMPLATE_FILE = "cel_lite_descriptor_template.txt";

  public static void createFile(String filePath, JavaFileGeneratorOption option)
      throws IOException, TemplateException {
    Version version = Configuration.VERSION_2_3_32;
    Configuration cfg = new Configuration(version);
    cfg.setClassForTemplateLoading(JavaFileGenerator.class, "templates/");
    cfg.setDefaultEncoding("UTF-8");
    cfg.setBooleanFormat("c");
    cfg.setAPIBuiltinEnabled(true);
    cfg.setNumberFormat("#"); // Prevent thousandth separator in numbers (eg: 1000 instead of 1,000)
    DefaultObjectWrapperBuilder wrapperBuilder = new DefaultObjectWrapperBuilder(version);
    wrapperBuilder.setExposeFields(true);
    cfg.setObjectWrapper(wrapperBuilder.build());

    Template template = cfg.getTemplate(HELPER_CLASS_TEMPLATE_FILE);
    Writer out = new StringWriter();

    template.process(option.getTemplateMap(), out);

    Files.asCharSink(new File(filePath), UTF_8).write(out.toString());
  }

  @AutoValue
  abstract static class JavaFileGeneratorOption {
    abstract String packageName();

    abstract String descriptorClassName();

    abstract String version();

    abstract ImmutableList<LiteDescriptorCodegenMetadata> descriptorMetadataList();

    ImmutableMap<String, Object> getTemplateMap() {
      return ImmutableMap.of(
          "package_name", packageName(),
          "descriptor_class_name", descriptorClassName(),
          "version", version(),
          "descriptor_metadata_list", descriptorMetadataList());
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setPackageName(String packageName);

      abstract Builder setDescriptorClassName(String className);

      abstract Builder setVersion(String version);

      abstract Builder setDescriptorMetadataList(
          ImmutableList<LiteDescriptorCodegenMetadata> messageInfo);

      abstract JavaFileGeneratorOption build();
    }

    static Builder newBuilder() {
      return new AutoValue_JavaFileGenerator_JavaFileGeneratorOption.Builder();
    }
  }

  private JavaFileGenerator() {}
}
