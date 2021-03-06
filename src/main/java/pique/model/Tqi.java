package pique.model;

import pique.evaluation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * This class represents the total quality index of a project.
 * It is used for the evaluation (quality assessment - estimation) of a
 * certain project or a benchmark of projects.
 *
 * @author Miltos, Rice
 */
public class Tqi extends ModelNode {

    // Constructor

    public Tqi(String name, String description, Map<String, Double> weights) {
        super(name, description, new DefaultFactorEvaluator(), new DefaultNormalizer());
        this.weights = (weights == null) ? new HashMap<>() : weights;
    }

    public Tqi(String name, String description, IEvaluator evaluator, INormalizer normalizer,
               IUtilityFunction utilityFunction, Map<String, Double> weights, Double[] thresholds) {
        super(name, description, evaluator, normalizer, utilityFunction, weights, thresholds);
    }

    // Used for cloning
    public Tqi(double value, String name, String description, IEvaluator evaluator, INormalizer normalizer,
               IUtilityFunction utilityFunction, Map<String, Double> weights, Double[] thresholds, Map<String,
            ModelNode> children) {
        super(value, name, description, evaluator, normalizer, utilityFunction, weights, thresholds, children);
    }


    // Methods

    /**
     * Tqi clone needs to work from a bottom-up parse to allow the fully connected
     * QualityAspect -> ProductFactor layer to pass the cloned property nodes by reference.
     */
    // TODO (1.0): Possible breaking change?
    @Override
    public ModelNode clone() {

        Map<String, ModelNode> clonedChildren = new HashMap<>();
        getChildren().forEach((k, v) -> clonedChildren.put(k, v.clone()));

        return new Tqi(getValue(), getName(), getDescription(), getEvaluatorObject(), getNormalizerObject(), getUtilityFunctionObject()
                , getWeights(), getThresholds(), clonedChildren);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Tqi)) {
            return false;
        }
        Tqi otherTqi = (Tqi) other;

        return getName().equals(otherTqi.getName())
                && getChildren().size() == otherTqi.getChildren().size();
    }

}
