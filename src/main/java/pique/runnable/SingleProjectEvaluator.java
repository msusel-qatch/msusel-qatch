package pique.runnable;

import org.apache.commons.io.FilenameUtils;
import pique.model.Diagnostic;
import pique.analysis.ITool;
import pique.evaluation.Project;
import pique.model.QualityModel;
import pique.model.QualityModelImport;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Behavioral class responsible for running TQI evaluation of a single project
 * in a language agnostic way.  It is the responsibility of extending projects
 * (e.g. qatch-csharp) to provide the language specific tools.
 */
// TODO (1.0): turn into static methods (maybe unless logger problems)
public class SingleProjectEvaluator {

    Project project;

    //region Get / Set
    public Project getEvaluatedProject() {
        return project;
    }

    /**
     * Entry point for running single project evaluation. The library assumes the user has extended Qatch
     * by implementing ITool with language-specific functionality.
     *
     * This method then evaluates the measures, properties, characteristics, and TQI according to the provided
     * quality model.
     *
     * @param projectDir
     *      Path to root directory of project to be analyzed.
     * @param resultsDir
     *      Directory to place the analysis results in. Does not needy to exist initially.
     * @param qmLocation
     *      Path to a completely derived quality model (likely .xml format).
     * @return
     *      The path to the produced quality analysis file on the hard disk.
     */
    public Path runEvaluator(Path projectDir, Path resultsDir, Path qmLocation, Set<ITool> tools) {

        // Initialize data structures
        initialize(projectDir, resultsDir, qmLocation);
        QualityModelImport qmImport = new QualityModelImport(qmLocation);
        QualityModel qualityModel = qmImport.importQualityModel();
        project = new Project(FilenameUtils.getBaseName(projectDir.getFileName().toString()), projectDir, qualityModel);

        // Validate State
        // TODO: validate more objects such as if the quality model has thresholds and weights, are there expected diagnostics, etc
        validatePreEvaluationState(project);

        // Run the static analysis tools process
        Map<String, Diagnostic> allDiagnostics = new HashMap<>();
        tools.forEach(tool -> {
            allDiagnostics.putAll(runTool(projectDir, tool));
        });

        // Run LOC tool to set lines of code
        int linesOfCode = (int)allDiagnostics.get("loc").getValue();
        // TODO (1.0): need to rethink loc, normalizer, evaluator interactions for benchmark repository
        //  interactions
        project.setLinesOfCode(linesOfCode);
        project.getQualityModel().getMeasures().values().forEach(measure -> {
            measure.getNormalizerObject().setNormalizerValue(linesOfCode);
        });

        // Apply tool results to Project object
        project.updateDiagnosticsWithFindings(allDiagnostics);

        double tqiValue = project.evaluateTqi();

        // Create a file of the results and return its path
        return project.exportToJson(resultsDir);
    }


    /**
     * Assert input parameters are valid and create the output folder
     *
     * @param projectDir
     *      Path to directory holding the project to be evaluated. Must exist.
     * @param resultsDir
     *      Directory to place the analysis results in. Does not need to exist initially.
     * @param qmLocation
     *      Path to the quality model file. Must exist.
     */
    private void initialize(Path projectDir, Path resultsDir, Path qmLocation) {
        if (!projectDir.toFile().exists()) {
            throw new IllegalArgumentException("Invalid projectDir path given.");
        }
        if (!qmLocation.toFile().exists() || !qmLocation.toFile().isFile()) {
            throw new IllegalArgumentException("Invalid qmLocation path given.");
        }

        resultsDir.toFile().mkdirs();
    }


    /**
     * Run static analysis tool evaluation process:
     *   (1) run static analysis tool
     *   (2) parse: get object representation of the diagnostics described by the QM
     *   (3) make collection of diagnostic objects
     *
     * @param projectDir
     *      Path to root directory of project to be analyzed.
     * @param tool
     *      Analyzer provided by language-specific instance necessary to find findings of the project.
     * @return
     *      A mapping of (Key: property name, Value: measure object) where the measure objects contain the
     *      static analysis findings for that measure.
     */
    private Map<String, Diagnostic> runTool(Path projectDir, ITool tool) {

        // (1) run static analysis tool
        // TODO: turn this into a temp file that always deletes on/before program exit
        Path analysisOutput = tool.analyze(projectDir);

        // (2) prase output: make collection of {Key: diagnostic name, Value: diagnostic objects}
        return tool.parseAnalysis(analysisOutput);
    }


    /**
     * Sequence of state checks of the project's quality model before running evaluation.
     * Throws runtime error if any expected state is not achieved.
     *
     * @param project
     *      The project under evaluation. This project should have a contained qualityModel with
     *      weight and threshold instances.
     */
    // TODO (1.0) Update once basic tests passing
    private void validatePreEvaluationState(Project project) {
        QualityModel projectQM = project.getQualityModel();

        if (projectQM.getTqi().getWeights() == null) {
            throw new RuntimeException("The project's quality model does not have any weights instantiated to its TQI node");
        }

        projectQM.getQualityAspects().values().forEach(characteristic -> {

            if (characteristic.getWeights() == null) {
                throw new RuntimeException("The project's quality model does not have any weights instantiated to its characteristic node");
            }

//            characteristic.getChildren().values().forEach(productFactor -> {
//                if (productFactor.getMeasure().getThresholds() == null) {
//                    throw new RuntimeException("The project's quality model does not have any thresholds instantiated to its measure node.");
//                }
//            });
        });
    }
}
