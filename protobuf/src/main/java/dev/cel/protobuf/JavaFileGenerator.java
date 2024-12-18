package dev.cel.protobuf;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

final class JavaFileGenerator {

  private static final String HELPER_CLASS_TEMPLATE_FILE = "cel_lite_descriptor_template.ftlh";

  public static void createFile(String filePath, JavaFileGeneratorOption option)
      throws IOException, TemplateException {
    Configuration cfg = new Configuration(Configuration.VERSION_2_3_33);
    cfg.setClassForTemplateLoading(JavaFileGenerator.class, "templates/");
    cfg.setDefaultEncoding("UTF-8");

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
    abstract String fullyQualifiedProtoName();
    abstract String fullyQualifiedProtoJavaClassName();

    ImmutableMap<String, String> getTemplateMap() {
      return ImmutableMap.of(
          "package_name", packageName(),
          "descriptor_class_name", descriptorClassName(),
          "version", version(),
          "fqn_proto_java_class_name", fullyQualifiedProtoJavaClassName(),
          "fqn_proto_name", fullyQualifiedProtoJavaClassName()
      );
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setPackageName(String packageName);
      abstract Builder setDescriptorClassName(String className);
      abstract Builder setVersion(String version);
      abstract Builder setFullyQualifiedProtoJavaClassName(String fqn);
      abstract Builder setFullyQualifiedProtoName(String fqn);

      abstract JavaFileGeneratorOption build();
    }

    static Builder newBuilder() {
      return new AutoValue_JavaFileGenerator_JavaFileGeneratorOption.Builder();
    }
  }
}
