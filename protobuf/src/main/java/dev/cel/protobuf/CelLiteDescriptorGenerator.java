package dev.cel.protobuf;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.Files;
import java.io.File;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;

final class CelLiteDescriptorGenerator implements Callable<Integer> {

  @Option(names = {"--outpath"}, description = "Outpath for the CelLiteDescriptor")
  private String outPath = "";

  @Option(names = {"--descriptor_set"}, description = "Descriptor Set")
  private String descriptorSet = "";

  @Option(names = {"--debug"}, description = "Prints debug output")
  private boolean debug = false;

  @Override
  public Integer call() throws Exception {
    Files.asCharSink(new File(outPath), UTF_8).write("content!");
    return 0;
  }

  public static void main(String[] args) throws Exception {
    CelLiteDescriptorGenerator celLiteDescriptorGenerator = new CelLiteDescriptorGenerator();
    CommandLine cmd = new CommandLine(celLiteDescriptorGenerator);
    cmd.parseArgs(args);
    celLiteDescriptorGenerator.printAllFlags(cmd);

    int exitCode = cmd.execute(args);
    System.exit(exitCode);
  }

  private void printAllFlags(CommandLine cmd) {
    debugPrint("Flag values:");
    debugPrint("-------------------------------------------------------------");
    for (OptionSpec option : cmd.getCommandSpec().options()) {
      debugPrint(option.longestName() + ": " + option.getValue().toString());
    }
    debugPrint("-------------------------------------------------------------");
  }

  private void debugPrint(String message) {
    if (debug) {
      System.out.println(Ansi.ON.string("@|cyan [CelLiteDescriptorGenerator] |@" + message));
    }
  }

  private CelLiteDescriptorGenerator() {}
}