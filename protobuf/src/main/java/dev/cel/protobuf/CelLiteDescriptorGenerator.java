package dev.cel.protobuf;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.Files;
import java.io.File;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

final class CelLiteDescriptorGenerator implements Callable<Integer> {

  // @Parameters(index = "0", description = "The file whose checksum to calculate.")
  // private String file;

  @Option(names = {"-f", "--foo"}, description = "MD5, SHA-1, SHA-256, ...")
  private String foo = "";

  @Override
  public Integer call() throws Exception {
    System.out.println("Called!");

    System.out.println("foo: " + foo);


    Files.asCharSink(new File("/Users/sokwhan/SourceCode/cel-java/common/src/test/resources/foo.java"), UTF_8).write("content!");
    return 0;
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Hello world!");
    for (String arg : args) {
      System.out.println("arg: " + arg);
    }

    int exitCode = new CommandLine(new CelLiteDescriptorGenerator()).execute(args);
    System.exit(exitCode);

  }

  private CelLiteDescriptorGenerator() {}

}