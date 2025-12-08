package common.reliability;

import dag.TaskNode;

public class TransientFaultBasedReliability extends TaskReliability{
    static double LAMBDA_0 = Math.pow(10, -9);
    static double d = 0.45;
    public double getReliability(TaskNode taskNode){
        return transientFaultBasedReliability(taskNode);
    }

    private double transientFaultBasedReliability(TaskNode taskNode){
        double fmax = taskNode.getEdgeDevice().getMipsPerCore();
        double minMipsNeeded = Math.ceil(taskNode.getLength() / taskNode.getMaxLatency());
        double fmin = 0;
        double executionTime = 0;
        if (minMipsNeeded >= fmax) {
            executionTime = taskNode.getLength() / fmax;
            fmin = fmax;
        } else {
            executionTime = taskNode.getLength() / minMipsNeeded;
            fmin = minMipsNeeded;
        }

        double fi = (fmin + fmax)/2;
        double lambda = calculateLambda(fi, fmin);
        double exp_power = lambda*(executionTime/fi);
        double reliability = Math.exp(-exp_power);
        return reliability;
    }

    static double calculateLambda(double fi, double fmin){
        double pow_coeff = d*(1-fi)/(1-fmin);
        return LAMBDA_0*Math.pow(10, pow_coeff);
    }
}
