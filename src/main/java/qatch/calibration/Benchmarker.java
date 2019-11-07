package qatch.calibration;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.opencsv.CSVWriter;
import org.apache.commons.io.FileUtils;
import qatch.analysis.Diagnostic;
import qatch.analysis.ITool;
import qatch.analysis.IToolLOC;
import qatch.analysis.Measure;
import qatch.evaluation.Project;
import qatch.model.QualityModel;
import qatch.utility.FileUtility;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * This class is responsible for analyzing all the projects that are stored in a
 * benchmark repository against
 * 		1. all the supported analysis tools of the system (ITool instances) and
 * 		2. all the Properties of the Quality Model.
 * to produce thresholds associated to each property of the quality model.
 *
 * The Benchmarker provides functionality to run batch tool analysis and run R threshold
 * generation on the resulting Property-Finding pairings from each benchmark project.
 */
public class Benchmarker {

    // Fields
    private Path analysisResults;  // location to place hard-disk file of analysis results (disk file necessary for R script)
    private final Path benchmarkRepository;  // location of root foldering containing language-specific projects for benchmarking
    private QualityModel qmDescription;  // location of quality model description file
    private IToolLOC locTool;  // loc-specific purpose tool, necessary for normalization
    private Map<String, Project> projects = new HashMap<>();  // { key: project name, value: project object }
    private Map<String, ITool> tools = new HashMap<>();  // tools intended for static analysis of diagnostic findings
    private Map<String, Double[]> thresholds = new HashMap<>();  // { key: property name, value: property thresholds }


    // Constructors
    Benchmarker() {
        this.benchmarkRepository = null;
    }

    /**
     * @param benchmarkRepository
     *      Location of root foldering containing language-specific projects for benchmarking
     * @param qmDescription
     *      Location of quality model description file
     * @param analysisResults
     *      Desired location to place the benchmarking [Project_Name:Measure_Value] matrix results.
     *      This path should go up to the directory level containing the results but not specify the
     *      actual result file name.
     */
    Benchmarker(Path benchmarkRepository, Path qmDescription, Path analysisResults, IToolLOC locTool, Set<ITool> tools) {
        this.benchmarkRepository = benchmarkRepository;
        this.qmDescription = new QualityModel(qmDescription);
        this.analysisResults = Paths.get(analysisResults.toString(), "benchmark_data.csv");
        this.locTool = locTool;
        tools.forEach(t -> this.tools.put(t.getName(), t));
    }


    // Getters and setters
    public Path getAnalysisResults() {
        return analysisResults;
    }
    void setAnalysisResults(Path analysisResults) {
        this.analysisResults = analysisResults;
    }


    // Methods
    /**
     * Run tools on all benchmark projects to collect normalized values of each Measure across each project.
     * Use the tool run data with the R script to generate thresholds for each known property.
     * Knowledge of measures to associate with tool findings comes from the quality model description.
     *
     * @param projectRootFlag
     *      Flag, usually a file extension, that signals that a project to be analyzed is
     *      within the directory the flag was found in.
     * @return
     *      The path to the file ready for R input containing normalized values of each measure across each project
     */
    public Map<String, Double[]> run(String projectRootFlag, Path rThresholdsOutput) {

        // Collect root paths of each benchmark project
        Set<Path> projectRoots = FileUtility.multiProjectCollector(this.benchmarkRepository, projectRootFlag);
        ArrayList<Project> projects = new ArrayList<>();

        System.out.println("* Beginning repository benchmark analysis");
        System.out.println(projectRoots.size() + " projects to analyze.\n");

        for (Path projectPath : projectRoots) {

            // Instantiate new project object
            Project project = new Project(projectPath.getFileName().toString(), projectPath, this.qmDescription);

            // Run LOC tool to set lines of code
            project.setLinesOfCode(locTool.analyze(projectPath));

            // Run tools, collect files of tool run results
            Map<ITool, Path> results = new HashMap<>();  // {key: tool, value: path to that tool's analysis file}
            tools.values().forEach(tool -> results.put(tool, tool.analyze(projectPath)));

            // Collect diagnostics
            Map<String, Diagnostic> diagnostics = new HashMap<>();
            // This loop essentially flattens the <String, Diagnostic> mapping from multiple tools into a single collection
            results.forEach((tool, resultPath) -> {
                Map<String, Diagnostic> resultParse = tool.parseAnalysis(resultPath);
                resultParse.forEach(diagnostics::put);
            });

            // Apply collected diagnostics (containing findings) to the project
            diagnostics.forEach((diagnosticName, diagnostic) -> {
                project.addFindings(diagnostic);
            });


            // Evaluate project up to Measure level (normalize diagnostic values according to LoC)
            project.evaluateMeasures();

            // Add new project (with tool findings information included) to the list
            projects.add(project);
        }

        // Create and set reference to [Project_Name:Measure_Values] matrix file
        this.analysisResults = createProjectMeasureMatrix(projects);

        // Run the R script to generate thresholds
        return generateThresholds(rThresholdsOutput);

        // Create and export matrix of [Project_name, Measure 1 value, Measure 2 value, ...] for R to analyze

        // Attach resulting thresholds to their associated property objects

        // Return quality model, now with benchmarked threshold values
    }

    /**
     * Execute the R script for theshold derivation to create thesholds for each property in the
     * quality model description according to the benchmark repository analysis findings.
     *
     * This method assumes the analysisResults fields points to an existing, generated file path
     * as a precondition.
     *
     * @return
     *      A mapping of property names to the associated thresholds of that property
     */
    Map<String, Double[]> generateThresholds(Path output) {

        // Precondition check
        if (!this.analysisResults.toFile().isFile()) {
            throw new RuntimeException("Benchmark analysisResults field must point to an existing file");
        }

        // Prepare temp file for R Script results
        output.toFile().mkdirs();
        File thresholdsFile = new File(output.toFile(), "threshold.json");

        // R script expects the directory containining the analysis results as a parameter
        Path analysisDirectory = this.analysisResults.getParent();

        // Run R Script
        RInvoker.executeRScript(
                RInvoker.Script.THRESHOLD,
                analysisDirectory.toAbsolutePath().toString(),
                output.toAbsolutePath().toString()
        );

        if (!thresholdsFile.isFile()) {
            throw new RuntimeException("Execution of R script did not result in an existing file at " + thresholdsFile.toString());
        }

        // Build object representation of data from R script
        Map<String, Double[]> thresholds = new HashMap<>();
        try {
            FileReader fr = new FileReader(thresholdsFile.toString());
            JsonArray jsonEntries = new JsonParser().parse(fr).getAsJsonArray();

            for (JsonElement entry : jsonEntries) {
                JsonObject jsonProperty = entry.getAsJsonObject();
                String name = jsonProperty.getAsJsonPrimitive("_row").getAsString().replaceAll("\\.", " ");
                Double[] threshold = new Double[] {
                        jsonProperty.getAsJsonPrimitive("t1").getAsDouble(),
                        jsonProperty.getAsJsonPrimitive("t2").getAsDouble(),
                        jsonProperty.getAsJsonPrimitive("t3").getAsDouble()
                };
                thresholds.put(name, threshold);
            }

            fr.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Delete temporary artifacts
        try { FileUtils.deleteDirectory(output.toFile()); }
        catch (IOException e) { e.printStackTrace(); }

        this.thresholds = thresholds;
        return thresholds;
    }


    // Helper methods

    /**
     * Transform a collection of Project objects into a matrix (likely .csv) with the Project name
     * as the rows and the normalized measure values of that project as the columns.
     *
     * @param projects
     *      Collection of projects
     * @return
     *      The path to the matrix file on the hard drive.
     */
    Path createProjectMeasureMatrix(List<Project> projects) {

        // Ensure containing directory of matrix output file exists
        analysisResults.getParent().toFile().mkdirs();
        // Make ArrayList of measures to assert order of measure data retrieval
        ArrayList<Measure> measureList = new ArrayList<>(projects.get(0).getMeasures().values());
        // Basic matrix data
        int numMeasures = measureList.size();

        try {
            FileWriter fw = new FileWriter(analysisResults.toFile());
            CSVWriter writer = new CSVWriter(fw);

            // Build rows of string arrays to eventually feed to writer
            ArrayList<String[]> csvRows = new ArrayList<>();

            // Header
            String[] header = new String[numMeasures + 1];
            header[0] = "Project_Name";
            for (int i = 1; i < measureList.size() + 1; i++) {
                header[i] = measureList.get(i-1).getName();
            }
            csvRows.add(header);

            // Additional rows
            for (Project project : projects) {
                String[] row = new String[numMeasures + 1];
                row[0] = project.getName();
                for (int i = 1; i < measureList.size() + 1; i++) {
                    row[i] = String.valueOf(project.getMeasure(measureList.get(i-1).getName()).getNormalizedValue());
                }
                csvRows.add(row);
            }

            // Run writer
            csvRows.forEach(writer::writeNext);
            writer.close();
            fw.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return analysisResults;
    }
}
