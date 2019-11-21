package qatch.analysis;

import com.google.gson.annotations.Expose;
import qatch.model.ModelNode;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class Diagnostic extends ModelNode {

    // Instance variables

    private Function<Set<Finding>, Double> evalFunction;
    @Expose
    private Set<Finding> findings = new HashSet<>();
    @Expose
    private String toolName;
    @Expose
    private double value;


    // Constructors

    public Diagnostic(String id, String description, String toolName) {
        super(id, description);
        this.evalFunction = this::defaultEvalFunction;
        this.toolName = toolName;
    }


    // Getters and setters

    public Set<Finding> getFindings() { return findings; }
    public void setFinding(Finding finding) { findings.add(finding); }
    public void setFindings(Set<Finding> findings) { this.findings = findings; }
    public String getToolName() {
        return toolName;
    }
    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public double getValue() {
        evaluate();
        return this.value;
    }


    // Methods

    @Override
    public ModelNode clone() {
        Diagnostic clonedDiagnostic = new Diagnostic(getName(), getDescription(), getToolName());
        findings.forEach(finding -> {
            setFinding(finding.deepClone());
        });

        return clonedDiagnostic;
    }

    /**
     * Diagnostics must define in their instantiating, language-specific class
     * how to evaluate the collection of its findings.  Often this will simply be
     * a count of findings, but quality evaluation (especially in the context of security)
     * should allow for other evaluation functions.
     *
     * @return
     *      The non-normalized value of the diagnostic
     */
    public void evaluate() {
        assert this.evalFunction != null;
        this.value = this.evalFunction.apply(this.findings);
    }


    // Helper methods

    /**
     * Define the default evaluation function to simply be a count of findings
     * @param findings
     *      The set of findings found by this diagnostic
     * @return
     *      The count of findings
     */
    private double defaultEvalFunction(Set<Finding> findings) {
        return findings.size();
    }
}
