package qatch.analysis;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Interface definition for static analysis tools.
 *
 * All tools must define how they run, how to parse the results file,
 * and how to transform the parsed data, if needed.
 */
public interface ITool {

    /**
     * Run the external static analysis tool (often a binary or .exe)
     *
     * @param projectLocation
     *      Root directory location needed by the tool to perform its analysis
     *      on the given project
     * @return
     *      The location of the analysis results: often a .xml or .json file.
     *      Ideally this file should be a temporary file stored on disk only
     *      as long as is necessary.
     */
    Path analyze(Path projectLocation);

    /**
     * Read a .yaml config file that relates properties to their associated measure, tool, and diagnostics.
     * The .yaml file should have the form:
     *      Property01Name:
     *        Tool: Tool_Name
     *        Measure: Measure_Name
     *        Diagnostics:
     *          - list
     *          - of
     *          - relevant
     *          - diagnostic names
     *      Injection:
     *        Tool: Roslynator
     *        Measure: Injection Findings
     *        Diagnostics:
     *          - SCS0001
     *          - SCS0002
     *
     * @param toolConfig
     *      Path location of the .yaml configuration
     * @return
     *      The object represetnation of mapping properties to the relevant measures of this tool.
     *      The key of the map is the Property name
     */
    // TODO: if enforcing YAML syntax, maybe sould use XML and schema definition? Cons and pros to either approach
    Map<String, Measure> mapMeasures(Path toolConfig);

    /**
     * Parse the analysis file generated by the tool and transform the data
     * into Measure objects.
     *
     * Because the output of any given external static analysis tool can vary greatly in
     * format and available information, it is the job of this tool implementation to
     * create a Measure object for each measure it is meant to provide.
     *
     * @param toolResults
     *      The location of the output file generated by running the static analysis tool
     * @return
     *      A collection of measure objects representing the parsed tool result data that maps with a Property
     */
    List<Measure> parse(Path toolResults);

    /**
     * @return
     *      The name of the tool
     */
    String getName();

    /**
     * @return
     *      The mapping of {Property, Measure} associated with the tool
     */
    Map<String, Measure> getMeasureMappings();
}
